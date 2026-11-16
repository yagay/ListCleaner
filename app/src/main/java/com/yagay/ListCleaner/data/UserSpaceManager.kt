package com.yagay.ListCleaner.data

import android.content.Context
import android.os.Process
import android.os.UserManager
import android.util.Log
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Android user/profile identity, never an application-clone package name. */
data class UserSpace(val userId: Int, val title: String)

/** Distinguish an empty remote profile from a failed or unfinished Root query. */
enum class SpacePackageScanState { LOCAL, LOADING, READY, FAILED, LOCKED }

/**
 * One authoritative selected space for every List Cleaner editor. Root is used only for
 * enumerating other Android users; without it we expose the local user rather than
 * guessing whether a vendor app clone is a separate Android profile.
 */
class UserSpaceManager(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("space_selection", Context.MODE_PRIVATE)
    val currentUserId: Int = AndroidUid.userId(Process.myUid())
    private val mutableSpaces = MutableStateFlow(listOf(UserSpace(currentUserId, app.getString(R.string.space_user_title, currentUserId))))
    val spaces: StateFlow<List<UserSpace>> = mutableSpaces.asStateFlow()
    private val mutableSelected = MutableStateFlow(currentUserId)
    val selected: StateFlow<Int> = mutableSelected.asStateFlow()
    private val mutableInstalledPackages = MutableStateFlow<Set<String>?>(null)
    /** null means a local user or an unavailable remote inventory; FAILED is a separate state. */
    private val verifiedPackagesByUser = mutableMapOf<Int, Set<String>>()
    private val packageScanReports = mutableMapOf<Int, String>()

    fun diagnosticReport(): String = buildString {
        appendLine("activeUser=${selected.value} state=${scanState.value}")
        spaces.value.forEach { space ->
            appendLine("user=${space.userId} name=${space.title} verifiedPackages=${verifiedPackagesByUser[space.userId]?.size ?: "unknown"}")
            appendLine("  ${packageScanReports[space.userId] ?: "not_scanned"}")
        }
    }
    val installedPackages: StateFlow<Set<String>?> = mutableInstalledPackages.asStateFlow()
    private val mutableScanState = MutableStateFlow(SpacePackageScanState.LOCAL)
    val scanState: StateFlow<SpacePackageScanState> = mutableScanState.asStateFlow()

    suspend fun refreshInstalledPackages(userId: Int) {
        if (userId == currentUserId) {
            mutableInstalledPackages.value = null
            mutableScanState.value = SpacePackageScanState.LOCAL
            return
        }
        if (selected.value == userId) mutableScanState.value = SpacePackageScanState.LOADING
        val started = System.nanoTime()
        val unlock = withContext(Dispatchers.IO) { ProfileUserAvailability.check(userId) }
        if (unlock == ProfileUnlockState.LOCKED) {
            packageScanReports[userId] = "deferred reason=profile_locked_or_not_running snapshot=retained"
            if (selected.value == userId) {
                mutableInstalledPackages.value = verifiedPackagesByUser[userId]
                mutableScanState.value = SpacePackageScanState.LOCKED
            }
            return
        }
        val (packages, detail) = withContext(Dispatchers.IO) {
            runCatching {
                val response = ComponentRootCommand.runReadOnly("pm list packages --user $userId")
                check(response.exitCode == 0 && !response.timedOut &&
                    ProfileShellBatch.clean(response.output)) {
                    "exit=${response.exitCode} timedOut=${response.timedOut} output=${response.output.take(200)}"
                }
                val result = response.output.lineSequence().map { it.trim() }
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:") }
                    .filter(com.yagay.ListCleaner.domain.PackageIdentity::valid)
                    .toSet()
                check(result.isNotEmpty()) { "profile_package_inventory_empty" }
                result to "verified count=${result.size} rootExit=${response.exitCode}"
            }.getOrElse { failure ->
                null to "failed reason=${failure.javaClass.simpleName} detail=${failure.message?.take(160)}"
            }
        }
        packageScanReports[userId] = "$detail unlock=$unlock elapsedMs=${(System.nanoTime() - started) / 1_000_000}"
        if (packages != null) verifiedPackagesByUser[userId] = packages
        if (selected.value == userId) {
            // Never turn a failed shell call into a verified empty profile.
            mutableInstalledPackages.value = packages ?: verifiedPackagesByUser[userId]
            mutableScanState.value = if (packages == null) SpacePackageScanState.FAILED
                else SpacePackageScanState.READY
            if (packages == null) Log.w("ListCleaner.Spaces", "Package inventory failed for user=$userId: $detail; keeping verified snapshot")
        }
    }

    suspend fun refresh() {
        val found = withContext(Dispatchers.IO) {
            val result = linkedMapOf(currentUserId to UserSpace(currentUserId, app.getString(R.string.space_user_title, currentUserId)))
            runCatching {
                app.getSystemService(UserManager::class.java)?.userProfiles.orEmpty().forEach { handle ->
                    // SDK stubs may hide getIdentifier even when the device provides
                    // it. Prefer the runtime value, with the legacy UserHandle
                    // representation as a compatibility fallback.
                    val id = runCatching {
                        (handle.javaClass.getMethod("getIdentifier").invoke(handle) as Number).toInt()
                    }.getOrElse { handle.hashCode() }
                    if (id >= 0) result[id] = UserSpace(
                        id, app.getString(R.string.space_user_title, id)
                    )
                }
            }
            runCatching {
                val output = ComponentRootCommand.runReadOnly("cmd user list")
                if (output.exitCode == 0 && !output.timedOut) {
                    // UserInfo{10:Work profile:...}; no shell substitution or user-controlled input.
                    Regex("UserInfo\\{([0-9]{1,5}):([^:}\\r\\n]{1,100}):").findAll(output.output).forEach { hit ->
                        val id = hit.groupValues[1].toInt()
                        result[id] = UserSpace(id, hit.groupValues[2].trim().ifEmpty { app.getString(R.string.space_user_title, id) })
                    }
                }
            }
            result.values.sortedBy { it.userId }
        }
        mutableSpaces.value = found
        val restored = prefs.getInt("selected_user", currentUserId)
        if (mutableSelected.value !in found.map { it.userId }) {
            mutableSelected.value = if (restored in found.map { it.userId }) restored else currentUserId
        } else if (mutableSelected.value == currentUserId && restored in found.map { it.userId }) {
            mutableSelected.value = restored
        }
    }

    fun select(userId: Int): Boolean {
        if (userId !in mutableSpaces.value.map { it.userId }) return false
        mutableSelected.value = userId
        mutableInstalledPackages.value = if (userId == currentUserId) null else verifiedPackagesByUser[userId]
        mutableScanState.value = if (userId == currentUserId)
            SpacePackageScanState.LOCAL else SpacePackageScanState.LOADING
        prefs.edit().putInt("selected_user", userId).apply()
        return true
    }
}
