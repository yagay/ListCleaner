package com.yagay.ListCleaner.domain

/** MIME families observed at the system Sharesheet boundary, not guessed from shortcut IDs. */
enum class DirectShareType(val mime: String) {
    TEXT("text/*"), IMAGE("image/*"), VIDEO("video/*"),
    AUDIO("audio/*"), FILE("application/*");

    companion object {
        fun fromMime(mime: String?): DirectShareType? {
            val value = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val major = value.substringBefore('/', "")
            return when (major) {
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

object DirectShareTypeRules {
    private const val MARKER = "#mime#"
    /** Per-family rules live in the existing per-user selected ComponentRule set and backups. */
    fun typed(base: ComponentRule, type: DirectShareType): ComponentRule {
        require(base.kind == IntentKind.DIRECT_SHARE)
        return base.copy(className = base.className.substringBefore(MARKER) + MARKER + type.name)
    }
    fun base(rule: ComponentRule): ComponentRule =
        if (rule.kind == IntentKind.DIRECT_SHARE) rule.copy(className = rule.className.substringBefore(MARKER))
        else rule
    fun family(rule: ComponentRule): DirectShareType? =
        if (rule.kind == IntentKind.DIRECT_SHARE)
            DirectShareType.fromName(rule.className.substringAfter(MARKER, ""))
        else null

    /** An unknown or broad query must never accidentally activate family-specific rules. */
    fun selectedFor(
        selected: Set<String>,
        ruleIds: Collection<String?>,
        family: DirectShareType?,
    ): Set<String> {
        if (selected.isEmpty()) return emptySet()
        return ruleIds.filterNotNull().filterTo(linkedSetOf()) { id ->
            id in selected || (family != null &&
                (id + MARKER + family.name) in selected)
        }
    }

    /** Only a Sharesheet getShareTargets match can establish that a family was observed. */
    fun evidence(type: DirectShareType): String = "DIRECT_SHARE_MATCHED_MIME=${type.name}"
    fun observed(evidence: List<String>): Set<DirectShareType> =
        evidence.asSequence().filter { it.startsWith("DIRECT_SHARE_MATCHED_MIME=") }
            .mapNotNull { DirectShareType.fromName(it.substringAfter('=')) }.toSet()
}
