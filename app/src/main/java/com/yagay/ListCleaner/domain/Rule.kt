package com.yagay.ListCleaner.domain

import android.content.Intent
import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class IntentKind(val action: String) {
    SHARE(Intent.ACTION_SEND),
    SHARE_MULTIPLE(Intent.ACTION_SEND_MULTIPLE),
    OPEN(Intent.ACTION_VIEW),
    BROWSER(Intent.ACTION_VIEW),
    PROCESS_TEXT(Intent.ACTION_PROCESS_TEXT)
}

@Serializable
data class ComponentRule(val kind: IntentKind, val packageName: String, val className: String) {
    val id: String get() = "${kind.name}|$packageName|${if (className.startsWith('.')) packageName + className else className}"

    fun isValid(): Boolean =
        packageName.isNotBlank() && packageName.length <= 255 &&
            className.isNotBlank() && className.length <= 512 &&
            !packageName.contains('|') && !className.contains('|') &&
            packageName.none { it.isWhitespace() || it.isISOControl() } &&
            className.none { it.isWhitespace() || it.isISOControl() }

    companion object {
        fun fromId(id: String): ComponentRule? {
            val parts = id.split('|', limit = 3)
            return if (parts.size == 3) runCatching {
                ComponentRule(IntentKind.valueOf(parts[0]), parts[1],
                    if (parts[2].startsWith('.')) parts[1] + parts[2] else parts[2]).takeIf(ComponentRule::isValid)
            }.getOrNull() else null
        }
    }
}

data class ComponentCandidate(
    val rule: ComponentRule,
    val appLabel: String,
    val activityLabel: String,
    val appIcon: Bitmap? = null,
    val evidence: List<String> = emptyList(),
    val restricted: Boolean = false,
    val unavailable: Boolean = false,
    val broadMatch: Boolean = false
) {
    val isCatalogCandidate: Boolean get() = !unavailable && !restricted
    val normalizedAppLabel: String by lazy(LazyThreadSafetyMode.NONE) { appLabel.lowercase() }
    private val normalizedSearch: String by lazy(LazyThreadSafetyMode.NONE) {
        buildString(appLabel.length + activityLabel.length + rule.packageName.length + rule.className.length + 3) {
            append(appLabel.lowercase())
            append('\u0000')
            append(activityLabel.lowercase())
            append('\u0000')
            append(rule.packageName.lowercase())
            append('\u0000')
            append(rule.className.lowercase())
        }
    }

    fun matchesQuery(query: String): Boolean {
        val needle = query.trim()
        return needle.isEmpty() || normalizedSearch.contains(needle.lowercase())
    }
}

@Serializable
data class RuleBackup(
    val version: Int = 1,
    val blacklist: Boolean,
    val rules: Set<ComponentRule>,
    val priorities: PriorityConfig = PriorityConfig(),
    val displayMode: DisplayMode? = null,
    /** Source-compatibility only. Not written to new backups. */
    @Transient val tiles: TileConfig = TileConfig(),
    val hiddenFromApps: Set<String> = emptySet(),
    /** Source-compatibility only. Not written to new backups. */
    @Transient val defaultOpen: DefaultOpenConfig = DefaultOpenConfig(),
    val openTypes: OpenTypeConfig = OpenTypeConfig(),
    /** User choice only; derived full-package targets are rebuilt from current catalog/rules. */
    val visibilityScopes: Set<VisibilityScope> = emptySet()
)
