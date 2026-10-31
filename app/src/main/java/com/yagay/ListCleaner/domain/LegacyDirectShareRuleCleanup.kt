package com.yagay.ListCleaner.domain

/**
 * One-time migration of selections created by the old Direct Share discovery.
 *
 * This only changes saved cleanup rules, typed rules, per-kind ordering and
 * custom titles. Observation cache and other kinds are never touched.
 * Invoked once from RuleRepository; do not apply this transform on every load.
 */
object LegacyDirectShareRuleCleanup {
    fun remove(config: ModuleConfig): ModuleConfig = config.copy(
        rules = config.rules.filterNotTo(linkedSetOf()) { it.kind == IntentKind.DIRECT_SHARE },
        directShareTypes = DirectShareTypeConfig(),
        priorities = withoutDirectSharePriorities(config.priorities),
        userSpaces = config.userSpaces.mapValues { (_, space) -> remove(space) },
    )

    private fun remove(space: UserSpaceRules): UserSpaceRules = space.copy(
        rules = space.rules.filterNotTo(linkedSetOf()) { it.kind == IntentKind.DIRECT_SHARE },
        directShareTypes = DirectShareTypeConfig(),
        priorities = withoutDirectSharePriorities(space.priorities),
    )

    private fun withoutDirectSharePriorities(priority: PriorityConfig): PriorityConfig =
        priority.copy(
            apps = priority.apps - IntentKind.DIRECT_SHARE,
            titles = priority.titles.filterKeys { !it.startsWith("DIRECT_SHARE|") },
        )
}
