package com.yagay.ListCleaner.domain

import android.content.Intent
import android.graphics.Bitmap
import kotlinx.serialization.Serializable

@Serializable
enum class IntentKind(val action: String, val title: String) {
    SHARE(Intent.ACTION_SEND, "分享"),
    SHARE_MULTIPLE(Intent.ACTION_SEND_MULTIPLE, "多文件分享"),
    OPEN(Intent.ACTION_VIEW, "打开方式"),
    BROWSER(Intent.ACTION_VIEW, "浏览器"),
    PROCESS_TEXT(Intent.ACTION_PROCESS_TEXT, "文本处理");

    val shortTitle: String get() = if (this == PROCESS_TEXT) "文本处理" else title
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
    // Only non-exported foreign components, not raw enabled fields or manager permissions.
    val restricted: Boolean = false,
    val unavailable: Boolean = false,
    val broadMatch: Boolean = false
) {
    // One predicate shared by rules, priority suggestions and diagnostic summaries.
    val isCatalogCandidate: Boolean get() = !unavailable && !restricted

    fun matchesQuery(query: String): Boolean = query.isBlank() ||
        appLabel.contains(query, true) || activityLabel.contains(query, true) ||
        rule.packageName.contains(query, true) || rule.className.contains(query, true)
}

@Serializable
data class RuleBackup(
    val version: Int = 1,
    val blacklist: Boolean,
    val rules: Set<ComponentRule>,
    val priorities: PriorityConfig = PriorityConfig(),
    val displayMode: DisplayMode? = null,
    val tiles: TileConfig = TileConfig(),
    val hiddenFromApps: Set<String> = emptySet()
)
