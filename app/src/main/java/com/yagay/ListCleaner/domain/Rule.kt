package com.yagay.ListCleaner.domain

import android.content.Intent
import android.graphics.Bitmap
import kotlinx.serialization.Serializable

@Serializable
enum class IntentKind(val action: String) {
    SHARE(Intent.ACTION_SEND),
    SHARE_MULTIPLE(Intent.ACTION_SEND_MULTIPLE),
    SEND_TO(Intent.ACTION_SENDTO),
    OPEN(Intent.ACTION_VIEW),
    BROWSER(Intent.ACTION_VIEW),
    DEEP_LINK(Intent.ACTION_VIEW),
    DIAL(Intent.ACTION_DIAL),
    GET_CONTENT(Intent.ACTION_GET_CONTENT),
    CAPTURE_IMAGE("android.media.action.IMAGE_CAPTURE"),
    CAPTURE_VIDEO("android.media.action.VIDEO_CAPTURE"),
    RECORD_AUDIO("android.provider.MediaStore.RECORD_SOUND"),
    PROCESS_TEXT(Intent.ACTION_PROCESS_TEXT),
    /** Launcher long-press/static/dynamic shortcut surface. Discovered synthetically, not by Intent resolution. */
    LAUNCHER_SHORTCUT("com.yagay.ListCleaner.action.LAUNCHER_SHORTCUT"),
    /** Storage Access Framework DocumentsProvider surface. Discovered via queryIntentContentProviders. */
    DOCUMENT_PROVIDER("android.content.action.DOCUMENTS_PROVIDER")
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
    val appType: AppType = AppType.USER,
    val evidence: List<String> = emptyList(),
    val restricted: Boolean = false,
    val unavailable: Boolean = false,
    val broadMatch: Boolean = false,
    val browserHosts: Set<String> = emptySet()
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
    val hiddenFromApps: Set<String> = emptySet(),
    val openTypes: OpenTypeConfig = OpenTypeConfig(),
    /** User choice only; derived full-package targets are rebuilt from current catalog/rules. */
    val visibilityScopes: Set<VisibilityScope> = emptySet(),
    val browserLinks: BrowserLinkConfig = BrowserLinkConfig()
)
