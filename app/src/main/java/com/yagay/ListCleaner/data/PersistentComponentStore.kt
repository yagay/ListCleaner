package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp

/**
 * Persists the user's desired disabled state separately from Android's current component override.
 * Some apps call PackageManager#setComponentEnabledSetting during startup and would otherwise
 * overwrite a Root `pm disable` performed by ListCleaner.
 */
object PersistentComponentState {
    const val REMOTE_KEY = "root_disabled_components_v1"
    const val LOCAL_PREFS = "root_component_state"
    const val LOCAL_KEY = "disabled_components"
    private const val MAX_ENTRIES = 20_000

    fun key(user: Int, component: ComponentName): String =
        key(user, component.packageName, component.className)

    fun key(user: Int, packageName: String, className: String): String =
        "$user|$packageName|$className"

    fun isValid(key: String): Boolean {
        val parts = key.split('|', limit = 3)
        if (parts.size != 3) return false
        val user = parts[0].toIntOrNull() ?: return false
        val packageName = parts[1]
        val className = parts[2]
        return user >= 0 && ComponentStatePolicy.valid(packageName, className, user)
    }

    fun sanitize(values: Collection<String>): Set<String> = values.asSequence()
        .filter(::isValid)
        .distinct()
        .take(MAX_ENTRIES)
        .toSet()
}

/** Local source of truth plus a best-effort mirror into libxposed remote preferences. */
class PersistentComponentStore(private val app: ListCleanerApp) {
    private val prefs = app.getSharedPreferences(PersistentComponentState.LOCAL_PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun disabledKeys(): Set<String> = PersistentComponentState.sanitize(
        prefs.getStringSet(PersistentComponentState.LOCAL_KEY, emptySet()).orEmpty()
    )

    fun isDisabled(component: RootComponent): Boolean =
        PersistentComponentState.key(component.user, component.component) in disabledKeys()

    /**
     * Update local intent first, then mirror the complete set to system_server's remote preferences.
     * A remote write failure does not discard the local intent; the next screen refresh retries it.
     */
    @Synchronized
    fun setDisabled(component: RootComponent, disabled: Boolean): Boolean {
        val key = PersistentComponentState.key(component.user, component.component)
        val next = disabledKeys().toMutableSet().apply {
            if (disabled) add(key) else remove(key)
        }.toSet()
        if (!prefs.edit().putStringSet(PersistentComponentState.LOCAL_KEY, next).commit()) {
            Log.e(TAG, "Failed to persist desired component state for $key")
            return false
        }
        return syncRemote(next)
    }

    @Synchronized
    fun syncRemote(): Boolean = syncRemote(disabledKeys())

    private fun syncRemote(values: Set<String>): Boolean {
        val session = app.currentSession() ?: return false
        return runCatching {
            val remote = session.service.getRemotePreferences(RuleRepository.REMOTE_PREFS)
            remote.edit()
                .putStringSet(PersistentComponentState.REMOTE_KEY, HashSet(values))
                .commit()
        }.onFailure {
            Log.w(TAG, "Unable to mirror persistent component state to Xposed service", it)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "ListCleaner.ComponentStore"
    }
}
