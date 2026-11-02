package com.yagay.ListCleaner.ui

/**
 * OnePlus / ColorOS-specific Settings subpage captured on the actual device.
 *
 * Activity: com.android.settings/.SubSettings
 * :settings:show_fragment:
 *   com.oplus.settings.feature.accessibility.AccessibilityDownloadedSettings
 *
 * This route opens only a Settings UI. It never enables or disables services.
 * On other Android systems the public Accessibility Settings action remains
 * the fallback; AOSP Downloaded apps is a PreferenceCategory, not an Activity.
 */
internal object OplusAccessibilityDownloadedRoute {
    const val ACTIVITY = "com.android.settings/.SubSettings"
    const val FRAGMENT =
        "com.oplus.settings.feature.accessibility.AccessibilityDownloadedSettings"
    private const val ACTION = "android.intent.action.MAIN"

    fun supportsManufacturer(manufacturer: String): Boolean =
        manufacturer.lowercase() in setOf("oneplus", "oppo", "realme")

    fun rootLaunchCommand(userId: Int): String {
        require(userId in 0..21474)
        // Exact values were captured from Settings with an observational Hook.
        // Quote the extra keys to keep the shell argv unambiguous.
        return "am start -W --user $userId -n $ACTIVITY -a $ACTION" +
            " --es ':settings:show_fragment' $FRAGMENT" +
            " --ez ':settings:is_second_layer_page' true"
    }

    /** A successful Settings launch must not be mistaken for the correct page. */
    fun activityLaunchAccepted(output: String): Boolean =
        output.contains("Status: ok", ignoreCase = true) &&
            !listOf("Permission Denial", "SecurityException", "Error:",
                "Status: error", "Status: timeout", "Status: canceled")
                .any { output.contains(it, ignoreCase = true) } &&
            output.lineSequence().any { line ->
                line.trim().startsWith("Activity: ") &&
                    line.substringAfter("Activity: ").trim().let {
                        it == ACTIVITY ||
                            it == "com.android.settings/com.android.settings.SubSettings"
                    }
            }

    fun actualFragmentVisible(dump: String): Boolean = dump.contains(FRAGMENT)
}
