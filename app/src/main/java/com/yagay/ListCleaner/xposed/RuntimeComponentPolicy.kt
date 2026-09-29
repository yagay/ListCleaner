package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind

/**
 * Process-local authoritative component/entry policy shared by the List Cleaner Xposed entries.
 *
 * Entry rules are indexed by kind when the verified config is published so hook hot paths never
 * need to repeatedly parse ComponentRule ids and rebuild temporary Sets.
 */
internal data class RuntimeComponentPolicySnapshot(
    val authoritative: Boolean = false,
    val managerAppId: Int = -1,
    val protectedComponents: Set<String> = emptySet(),
    val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
    val entryRules: Set<String> = emptySet(),
    val entryRulesByKind: Map<IntentKind, Set<String>> = emptyMap(),
    val entryPriorities: Map<IntentKind, List<String>> = emptyMap(),
    val digest: String = "",
) {
    fun selected(kind: IntentKind): Set<String> = entryRulesByKind[kind].orEmpty()
    fun priorities(kind: IntentKind): List<String> = entryPriorities[kind].orEmpty()
}

internal object RuntimeComponentPolicy {
    private data class PendingEntryPolicy(
        val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
        val entryRules: Set<String> = emptySet(),
        val entryRulesByKind: Map<IntentKind, Set<String>> = emptyMap(),
        val entryPriorities: Map<IntentKind, List<String>> = emptyMap(),
    )

    @Volatile private var value = RuntimeComponentPolicySnapshot()
    @Volatile private var pendingEntryPolicy = PendingEntryPolicy()

    /** Resolver snapshot construction stages non-destructive entry rules for the next atomic commit. */
    fun publishEntryRules(
        displayMode: DisplayMode,
        entryRules: Set<String>,
        entryPriorities: Map<IntentKind, List<String>>,
    ) {
        val canonical = linkedSetOf<String>()
        val byKind = linkedMapOf<IntentKind, MutableSet<String>>()
        entryRules.forEach { id ->
            val rule = ComponentRule.fromId(id) ?: return@forEach
            canonical += rule.id
            byKind.getOrPut(rule.kind) { linkedSetOf() } += rule.id
        }
        pendingEntryPolicy = PendingEntryPolicy(
            displayMode = displayMode,
            entryRules = canonical,
            entryRulesByKind = byKind.mapValues { (_, rules) -> rules.toSet() },
            entryPriorities = entryPriorities.mapValues { (_, packages) -> packages.toList() },
        )
    }

    /** Root/component identity and staged entry rules become authoritative in one volatile write. */
    fun publish(managerAppId: Int, protectedComponents: Set<String>, digest: String) {
        val pending = pendingEntryPolicy
        value = RuntimeComponentPolicySnapshot(
            authoritative = digest.isNotEmpty(),
            managerAppId = managerAppId,
            protectedComponents = protectedComponents.toSet(),
            displayMode = pending.displayMode,
            entryRules = pending.entryRules,
            entryRulesByKind = pending.entryRulesByKind,
            entryPriorities = pending.entryPriorities,
            digest = digest,
        )
    }

    fun snapshot(): RuntimeComponentPolicySnapshot = value
}

internal fun fallbackRuntimePolicy(
    managerAppId: Int,
    displayMode: DisplayMode,
    entryRules: Set<String>,
    entryPriorities: Map<IntentKind, List<String>>,
): RuntimeComponentPolicySnapshot {
    val byKind = linkedMapOf<IntentKind, MutableSet<String>>()
    val canonical = linkedSetOf<String>()
    entryRules.forEach { id ->
        val rule = ComponentRule.fromId(id) ?: return@forEach
        canonical += rule.id
        byKind.getOrPut(rule.kind) { linkedSetOf() } += rule.id
    }
    return RuntimeComponentPolicySnapshot(
        managerAppId = managerAppId,
        displayMode = displayMode,
        entryRules = canonical,
        entryRulesByKind = byKind.mapValues { (_, rules) -> rules.toSet() },
        entryPriorities = entryPriorities.mapValues { (_, packages) -> packages.toList() },
    )
}
