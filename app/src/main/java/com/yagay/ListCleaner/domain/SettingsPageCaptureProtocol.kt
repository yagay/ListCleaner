package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** Metadata-only observation of an explicitly visited Settings page.
 * Never contains URI data, extras bundles, user content, or a copied View hierarchy.
 */
@Serializable
data class CapturedSettingsPage(
    val packageName: String,
    val activityClass: String,
    val fragmentClass: String = "",
    val action: String = "",
    val roleName: String = "",
    val userId: Int,
    val capturedAtMillis: Long,
)

object SettingsPageCaptureProtocol {
    const val METHOD_PUBLISH = "publish_settings_page_v1"
    const val EXTRA_PAGE = "page"
    const val PREFS = "settings_page_capture_v1"
    const val MAX_AGE_MS = 180_000L
    const val MAX_ITEMS = 24
    const val MAX_PAYLOAD_CHARS = 4_096
    val HOST_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    fun valid(page: CapturedSettingsPage): Boolean {
        if (page.packageName !in HOST_PACKAGES || page.userId !in 0..99_999 ||
            page.capturedAtMillis <= 0) return false
        // Google PermissionController APKs sometimes retain the AOSP class namespace.
        if (page.activityClass.length > 240 ||
            HOST_PACKAGES.none { page.activityClass.startsWith(it + ".") }) return false
        if (page.fragmentClass.length > 240 || page.fragmentClass.any { it.isISOControl() } ||
            page.action.length > 160 || page.roleName.length > 120) return false
        if (page.action.isNotEmpty() && !page.action.startsWith("android.")) return false
        if (page.roleName.isNotEmpty() && !page.roleName.startsWith("android.app.role.")) return false
        // Fragments from other packages are never accepted as remotely supplied launch data.
        if (page.fragmentClass.isNotEmpty() &&
            !page.fragmentClass.startsWith("com.android.settings.") &&
            !page.fragmentClass.startsWith("com.android.permissioncontroller.") &&
            !page.fragmentClass.startsWith("com.google.android.permissioncontroller.")) return false
        return true
    }
}
