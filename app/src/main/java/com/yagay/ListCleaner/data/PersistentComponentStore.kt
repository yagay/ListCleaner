package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.domain.ComponentStatePolicy
import com.yagay.ListCleaner.domain.RootGlobalModePolicy

/**
 * Persists the user's desired disabled state separately from Android's current component override.
 * Some apps call PackageManager#setComponentEnabledSetting during startup and would otherwise
 * overwrite a Root `pm disable` performed by ListCleaner.
 */
data class PersistentComponentRef(
    val user: Int,
    val component: ComponentName
)

object PersistentComponentState {
    const val REMOTE_KEY = "root_disabled_components_v1"
    const val LOCAL_PREFS = "root_component_state"
    const val LOCAL_KEY = "disabled_components"
    const val PAUSED_USERS_KEY = "paused_root_users_v1"
    const val REMOTE_PAUSED_USERS_KEY = "root_paused_users_v1"
    private const val MAX_ENTRIES = 20_000

    fun key(user: Int, component: ComponentName): String =
        key(user, component.packageName, component.className)

    fun key(user: Int, packageName: String, className: String): String =
        "$user|$packageName|$className"

    fun parse(key: String): PersistentComponentRef? {
        val parts = key.split('|', limit = 3)
        if (parts.size != 3) return null
        val user = parts[0].toIntOrNull() ?: return null
        val packageName = parts[1]
        val className = parts[2]
        if (!ComponentStatePolicy.valid(packageName, className, user)) return null
        return PersistentComponentRef(user, ComponentName(packageName, className))
    }

    fun isValid(key: String): Boolean = parse(key) != null

    fun sanitize(values: Collection<String>): Set<String> = values.asSequence()
        .filter(::isValid)
        .distinct()
        .take(MAX_ENTRIES)
        .toSet()
}

enum class ComponentPersistenceResult {
    LOCAL_FAILED,
    LOCAL_SAVED,
    FULLY_SYNCED;

    val localSaved: Boolean get() = this != LOCAL_FAILED
    val remoteSynced: Boolean get() = this == FULLY_SYNCED
}

/** Local source of truth plus a best-effort mirror into libxposed remote preferences. */
class PersistentComponentStore(private val app: ListCleanerApp) {
    private val prefs = app.getSharedPreferences(PersistentComponentState.LOCAL_PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun disabledKeys(): Set<String> = PersistentComponentState.sanitize(
        prefs.getStringSet(PersistentComponentState.LOCAL_KEY, emptySet()).orEmpty()
    )

    /** Mode belongs to an Android user, not the currently selected UI category. */
    @Synchronized
    fun pausedUsers(): Set<Int> =
        prefs.getStringSet(PersistentComponentState.PAUSED_USERS_KEY, emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull).filter { it in 0..21474 }.toSet()

    @Synchronized
    fun isPaused(userId: Int): Boolean = userId in pausedUsers()

    @Synchronized
    fun setPaused(userId: Int, paused: Boolean): Boolean {
        require(userId in 0..21474)
        val next = pausedUsers().toMutableSet().apply {
            if (paused) add(userId) else remove(userId)
        }
        return prefs.edit().putStringSet(
            PersistentComponentState.PAUSED_USERS_KEY, next.map(Int::toString).toSet()
        ).commit()
    }

    /**
     * Root desired disabled components are retained while paused. Only the effective
     * active set is published to system_server's re-enable guard and runtime snapshot.
     */
    @Synchronized
    fun effectiveDisabledKeys(): Set<String> {
        val paused = pausedUsers()
        return RootGlobalModePolicy.effectiveKeys(disabledKeys(), paused)
    }

    fun isDisabled(component: RootComponent): Boolean =
        PersistentComponentState.key(component.user, component.component) in disabledKeys()

    /**
     * Update local intent first, then mirror the complete set to system_server's remote preferences.
     * Local persistence is authoritative. A remote mirror failure is recoverable because the next
     * synchronization transports the locally persisted set again.
     */
    @Synchronized
    fun setDisabled(component: RootComponent, disabled: Boolean): ComponentPersistenceResult {
        val key = PersistentComponentState.key(component.user, component.component)
        val next = disabledKeys().toMutableSet().apply {
            if (disabled) add(key) else remove(key)
        }.toSet()
        if (!prefs.edit().putStringSet(PersistentComponentState.LOCAL_KEY, next).commit()) {
            Log.e(TAG, "Failed to persist desired component state for $key")
            return ComponentPersistenceResult.LOCAL_FAILED
        }
        return if (syncRemote()) {
            ComponentPersistenceResult.FULLY_SYNCED
        } else {
            ComponentPersistenceResult.LOCAL_SAVED
        }
    }

    @Synchronized
    fun syncRemote(): Boolean {
        val paused = pausedUsers()
        val values = RootGlobalModePolicy.effectiveKeys(disabledKeys(), paused)
        return syncRemote(values, paused)
    }

    private fun syncRemote(values: Set<String>, paused: Set<Int>): Boolean {
        val session = app.currentSession() ?: return false
        return runCatching {
            val remote = session.service.getRemotePreferences(RuleRepository.REMOTE_PREFS)
            remote.edit()
                .putStringSet(PersistentComponentState.REMOTE_KEY, HashSet(values))
                .putStringSet(PersistentComponentState.REMOTE_PAUSED_USERS_KEY, paused.map(Int::toString).toSet())
                .commit()
        }.onFailure {
            Log.w(TAG, "Unable to mirror persistent component state to Xposed service", it)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "ListCleaner.ComponentStore"
    }
}
