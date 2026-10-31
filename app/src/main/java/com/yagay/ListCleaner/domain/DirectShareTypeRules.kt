package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** MIME families matched by the system Sharesheet, never guessed from shortcut IDs. */
enum class DirectShareType(val mime: String) {
    TEXT("text/*"), IMAGE("image/*"), VIDEO("video/*"),
    AUDIO("audio/*"), FILE("application/*");

    companion object {
        fun fromMime(mime: String?): DirectShareType? {
            val value = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when (value.substringBefore('/', "")) {
                "text" -> TEXT
                "image" -> IMAGE
                "video" -> VIDEO
                "audio" -> AUDIO
                "application" -> FILE
                else -> null
            }
        }
        fun fromName(value: String): DirectShareType? = entries.firstOrNull { it.name == value }
    }
}

/**
 * OpenTypeConfig-style independent rules. Generic DIRECT_SHARE rules live in
 * ModuleConfig.rules and apply to all MIME types. Each family's ID set is
 * saved separately per user; changing one family never edits the generic set.
 */
@Serializable
data class DirectShareTypeConfig(
    val rules: Map<DirectShareType, Set<String>> = emptyMap(),
) {
    fun validated(): DirectShareTypeConfig {
        require(rules.size <= DirectShareType.entries.size)
        require(rules.values.all { ids ->
            ids.size <= 2_000 && ids.all { id ->
                val parsed = ComponentRule.fromId(id)
                parsed != null && parsed.id == id && parsed.kind == IntentKind.DIRECT_SHARE &&
                    DirectShareTypeRules.family(parsed) == null
            }
        }) { "invalid_direct_share_type_rules" }
        return this
    }

    fun selectedRules(type: DirectShareType): Set<ComponentRule> =
        rules[type].orEmpty().mapNotNull(ComponentRule::fromId).toSet()

    /** Compiled only for runtime filtering; these synthetic IDs are never stored. */
    fun runtimeIds(): Set<String> = buildSet {
        rules.forEach { (type, ids) ->
            ids.forEach { id -> ComponentRule.fromId(id)?.let { rule ->
                add(DirectShareTypeRules.typed(rule, type).id)
            } }
        }
    }
}

object DirectShareTypeRules {
    private const val MARKER = "#mime#"
    /** Runtime-only ID encoding, not used for persistent typed rules. */
    fun typed(base: ComponentRule, type: DirectShareType): ComponentRule {
        require(base.kind == IntentKind.DIRECT_SHARE)
        return base.copy(className = base.className.substringBefore(MARKER) + MARKER + type.name)
    }
    fun base(rule: ComponentRule): ComponentRule =
        if (rule.kind == IntentKind.DIRECT_SHARE)
            rule.copy(className = rule.className.substringBefore(MARKER))
        else rule
    fun family(rule: ComponentRule): DirectShareType? =
        if (rule.kind == IntentKind.DIRECT_SHARE)
            DirectShareType.fromName(rule.className.substringAfter(MARKER, ""))
        else null

    /** Unknown or ambiguous MIME does not activate per-type rules. */
    fun selectedFor(
        selected: Set<String>,
        ruleIds: Collection<String?>,
        family: DirectShareType?,
    ): Set<String> =
        ruleIds.filterNotNull().filterTo(linkedSetOf()) { id ->
            id in selected || (family != null && (id + MARKER + family.name) in selected)
        }

    fun evidence(type: DirectShareType): String = "DIRECT_SHARE_MATCHED_MIME=${type.name}"
    fun observed(evidence: List<String>): Set<DirectShareType> =
        evidence.asSequence().filter { it.startsWith("DIRECT_SHARE_MATCHED_MIME=") }
            .mapNotNull { DirectShareType.fromName(it.substringAfter('=')) }.toSet()
}
