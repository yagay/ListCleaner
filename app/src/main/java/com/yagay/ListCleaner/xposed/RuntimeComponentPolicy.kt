package com.yagay.ListCleaner.xposed

import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind

/** Immutable process-local policy used by all List Cleaner Xposed adapters. */
internal data class RuntimeComponentPolicySnapshot(
    val authoritative: Boolean = false,
    val managerAppId: Int = -1,
    val protectedComponents: Set<String> = emptySet(),
    val displayMode: DisplayMode = DisplayMode.HIDE_SELECTED,
    val entryRules: Set<String> = emptySet(),
    val entryRulesByKind: Map<IntentKind, Set<String>> = emptyMap(),
    val selectedPackagesByKind: Map<IntentKind, Set<String>> = emptyMap(),
    val entryPriorities: Map<IntentKind, List<String>> = emptyMap(),
    val digest: String = "",
) {
    fun selected(kind: IntentKind): Set<String> = entryRulesByKind[kind].orEmpty()
    fun selectedPackages(kind: IntentKind): Set<String> = selectedPackagesByKind[kind].orEmpty()
    fun priorities(kind: IntentKind): List<String> = entryPriorities[kind].orEmpty()
}

/** Pure compiler: parsing/indexing happens on config publication, never on framework hot paths. */
internal object RuntimePolicyCompiler {
    fun compile(
        managerAppId: Int,
        protectedComponents: Set<String>,
        displayMode: DisplayMode,
        entryRules: Collection<String>,
        entryPriorities: Map<IntentKind, List<String>>,
        digest: String,
        authoritative: Boolean = digest.isNotEmpty(),
    ): RuntimeComponentPolicySnapshot {
        val canonical = linkedSetOf<String>()
        val byKind = linkedMapOf<IntentKind, MutableSet<String>>()
        val packagesByKind = linkedMapOf<IntentKind, MutableSet<String>>()
        entryRules.forEach { id ->
            val rule = ComponentRule.fromId(id) ?: return@forEach
            canonical += rule.id
            byKind.getOrPut(rule.kind) { linkedSetOf() } += rule.id
            packagesByKind.getOrPut(rule.kind) { linkedSetOf() } += rule.packageName
        }
        val priorities = entryPriorities.asSequence()
            .filter { (kind, _) -> kind.isSelectableEntryKind() }
            .associate { (kind, packages) -> kind to packages.distinct() }
        return RuntimeComponentPolicySnapshot(
            authoritative = authoritative,
            managerAppId = managerAppId,
            protectedComponents = protectedComponents.toSet(),
            displayMode = displayMode,
            entryRules = canonical,
            entryRulesByKind = byKind.mapValues { (_, rules) -> rules.toSet() },
            selectedPackagesByKind = packagesByKind.mapValues { (_, packages) -> packages.toSet() },
            entryPriorities = priorities,
            digest = digest,
        )
    }
}

/** One volatile publication makes every field from one config generation visible atomically. */
internal object RuntimeComponentPolicy {
    private data class Generation(
        val primary: RuntimeComponentPolicySnapshot,
        val otherUsers: Map<Int, RuntimeComponentPolicySnapshot>,
    )

    // One volatile reference guarantees the primary and profile policies belong to the same
    // configuration generation. A two-field publication can briefly mix user 0/user 10 rules.
    @Volatile private var generation = Generation(RuntimeComponentPolicySnapshot(), emptyMap())

    fun publish(snapshot: RuntimeComponentPolicySnapshot) {
        publishForUsers(snapshot, emptyMap())
    }

    fun publishForUsers(
        primary: RuntimeComponentPolicySnapshot,
        otherUsers: Map<Int, RuntimeComponentPolicySnapshot>,
    ) {
        generation = Generation(primary, otherUsers.toMap())
    }

    /** Missing user policies fail open; they never inherit another user's selected entries. */
    fun snapshot(userId: Int = 0): RuntimeComponentPolicySnapshot {
        val current = generation
        if (userId == 0) return current.primary
        return current.otherUsers[userId] ?: current.primary.copy(
            displayMode = DisplayMode.SHOW_ALL,
            entryRules = emptySet(),
            entryRulesByKind = emptyMap(),
            selectedPackagesByKind = emptyMap(),
            entryPriorities = emptyMap(),
        )
    }
}

internal fun fallbackRuntimePolicy(
    managerAppId: Int,
    displayMode: DisplayMode,
    entryRules: Set<String>,
    entryPriorities: Map<IntentKind, List<String>>,
): RuntimeComponentPolicySnapshot = RuntimePolicyCompiler.compile(
    managerAppId = managerAppId,
    protectedComponents = emptySet(),
    displayMode = displayMode,
    entryRules = entryRules,
    entryPriorities = entryPriorities,
    digest = "",
    authoritative = false,
)
