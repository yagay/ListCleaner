package com.yagay.ListCleaner.data

import android.content.Context
import android.os.Process
import android.os.UserManager
import com.yagay.ListCleaner.domain.AndroidUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Android user/profile identity, never an application-clone package name. */
data class UserSpace(val userId: Int, val title: String)

/**
 * One authoritative selected space for every List Cleaner editor. Root is used only for
 * enumerating other Android users; without it we expose the local user rather than
 * guessing whether a vendor app clone is a separate Android profile.
 */
class UserSpaceManager(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("space_selection", Context.MODE_PRIVATE)
    val currentUserId: Int = AndroidUid.userId(Process.myUid())
    private val mutableSpaces = MutableStateFlow(listOf(UserSpace(currentUserId, "User $currentUserId")))
    val spaces: StateFlow<List<UserSpace>> = mutableSpaces.asStateFlow()
    private val mutableSelected = MutableStateFlow(currentUserId)
    val selected: StateFlow<Int> = mutableSelected.asStateFlow()

    suspend fun refresh() {
        val found = withContext(Dispatchers.IO) {
            val result = linkedMapOf(currentUserId to UserSpace(currentUserId, "User $currentUserId"))
            runCatching {
                app.getSystemService(UserManager::class.java)?.userProfiles.orEmpty().forEach { handle ->
                    result[handle.hashCode()] = UserSpace(handle.hashCode(), "User ${handle.hashCode()}")
                }
            }
            runCatching {
                val output = ComponentRootCommand.run("cmd user list")
                if (output.exitCode == 0 && !output.timedOut) {
                    // UserInfo{10:Work profile:...}; no shell substitution or user-controlled input.
                    Regex("UserInfo\\{([0-9]{1,5}):([^:}\\r\\n]{1,100}):").findAll(output.output).forEach { hit ->
                        val id = hit.groupValues[1].toInt()
                        result[id] = UserSpace(id, hit.groupValues[2].trim().ifEmpty { "User $id" })
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
        prefs.edit().putInt("selected_user", userId).apply()
        return true
    }
}
