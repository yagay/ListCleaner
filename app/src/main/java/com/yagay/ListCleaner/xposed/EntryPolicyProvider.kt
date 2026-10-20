package com.yagay.ListCleaner.xposed

import android.content.SharedPreferences
import com.yagay.ListCleaner.domain.IntentKind

/**
 * Shared policy source for specialized Xposed adapters.
 *
 * A verified process-local runtime snapshot always wins. Until the runtime bridge has published one,
 * adapters read the same bounded RemotePreferences fallback. Keeping this decision in one place
 * prevents each hook module from drifting into subtly different fallback behavior.
 */
internal class EntryPolicyProvider(
    preferences: SharedPreferences,
    kinds: Set<IntentKind>,
    includePriorities: Boolean = true,
    record: (String) -> Unit,
) {
    private val ownedKinds = kinds.toSet()
    private val fallback = RemoteEntryPolicyFallback(
        preferences = preferences,
        selectRules = { config ->
            config.rules.asSequence()
                .filter { it.kind in ownedKinds }
                .mapTo(linkedSetOf()) { it.id }
        },
        selectPriorities = { config ->
            if (includePriorities) config.priorities.apps.filterKeys { it in ownedKinds }
            else emptyMap()
        },
        record = record,
    )

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            fallback.start()
            started = true
        }
    }

    fun snapshot(userId: Int = 0): RuntimeComponentPolicySnapshot {
        start()
        val runtime = RuntimeComponentPolicy.snapshot(userId)
        if (runtime.authoritative) return runtime
        // Legacy RemotePreferences have no per-user policy; never reuse user 0's rules.
        if (userId != 0) return runtime.copy(displayMode = com.yagay.ListCleaner.domain.DisplayMode.SHOW_ALL)

        val local = fallback.snapshot()
        return fallbackRuntimePolicy(
            managerAppId = local.managerAppId,
            displayMode = local.displayMode,
            entryRules = local.rules,
            entryPriorities = local.priorities,
        )
    }
}
