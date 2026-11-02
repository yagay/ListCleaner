package com.yagay.ListCleaner.ui

import android.content.ComponentName
import android.content.Intent

/**
 * AOSP Settings routes one installed AccessibilityService through its
 * permission-guarded detail action, not the general Accessibility page.
 * This bridge only creates a navigation request; it never toggles a service.
 */
internal object AccessibilityServiceDetailRoute {
    internal const val ACTION = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    internal const val SETTINGS_PACKAGE = "com.android.settings"
    private val safeComponent = Regex(
        """[A-Za-z_][A-Za-z0-9_.]*/[A-Za-z_.$][A-Za-z0-9_.$]*"""
    )

    /** Pure string validation also works in local JVM regression tests. */
    internal fun flattenedService(packageName: String?, serviceName: String?): String? {
        if (packageName.isNullOrBlank() || serviceName.isNullOrBlank()) return null
        val name = if (serviceName.startsWith(".")) packageName + serviceName else serviceName
        val flattened = "$packageName/$name"
        return flattened.takeIf(safeComponent::matches)
    }

    internal fun component(packageName: String?, serviceName: String?): ComponentName? =
        flattenedService(packageName, serviceName)?.let(ComponentName::unflattenFromString)

    internal fun detailIntent(component: ComponentName): Intent =
        Intent(ACTION).setPackage(SETTINGS_PACKAGE)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Validated, quoted-free shell argument: arbitrary user text cannot become a command. */
    internal fun rootLaunchCommand(userId: Int, component: ComponentName): String =
        rootLaunchCommand(userId, component.flattenToString())

    internal fun rootLaunchCommand(userId: Int, service: String): String {
        require(userId >= 0 && safeComponent.matches(service))
        return "am start -W --user $userId" +
            " -a $ACTION -c android.intent.category.DEFAULT -p $SETTINGS_PACKAGE" +
            " --es android.intent.extra.COMPONENT_NAME $service"
    }

    private val settingsActivity = Regex(
        """com\\.android\\.settings/[A-Za-z_.$][A-Za-z_.$0-9]*"""
    )

    /** Query the actual device's registered detail Activity, not an implicit parent. */
    internal fun discoverCommand(userId: Int): String {
        require(userId >= 0)
        return "cmd package query-activities --brief --components --user $userId" +
            " -a $ACTION -c android.intent.category.DEFAULT"
    }

    internal fun selectRegisteredDetails(output: String): String? =
        settingsActivity.findAll(output).map { it.value }.firstOrNull { candidate ->
            val cls = candidate.substringAfter('/')
            cls.substringAfterLast('.').contains("Accessibility", ignoreCase = true) &&
                cls.contains("Detail", ignoreCase = true)
        }

    internal fun rootLaunchCommand(
        userId: Int, service: String, activity: String,
    ): String {
        require(userId >= 0 && safeComponent.matches(service))
        require(activity == selectRegisteredDetails(activity))
        // The Activity name may contain '
}
 (Settings$Accessibility...).
        // Shell single-quotes protect it from variable expansion.
        return "am start -W --user $userId -n '$activity'" +
            " -a $ACTION -c android.intent.category.DEFAULT" +
            " --es android.intent.extra.COMPONENT_NAME $service"
    }

    internal fun launchAccepted(result: String, activity: String): Boolean =
        activity == selectRegisteredDetails(activity) &&
            result.contains("Status: ok", ignoreCase = true) &&
            !listOf("Permission Denial", "SecurityException", "Error:", "Status: timeout",
                "Status: canceled", "Status: error").any {
                result.contains(it, ignoreCase = true)
            } &&
            result.lineSequence().any { line ->
                line.trim().startsWith("Activity: ") &&
                    line.substringAfter("Activity: ").trim().startsWith(SETTINGS_PACKAGE + "/")
            }

    /**
     * AOSP detail trampoline finishes and opens a SubSettings fragment.
     * Merely seeing com.android.settings (or SubSettings) is NOT success.
     */
    internal fun detailFragmentVisible(dump: String): Boolean =
        listOf(
            "ToggleAccessibilityServicePreferenceFragment",
            "AccessibilityServiceDetailFragment",
            "AccessibilityServiceDetailsFragment",
        ).any(dump::contains)

    internal fun parentAccessibilityVisible(dump: String): Boolean =
        !detailFragmentVisible(dump) &&
            (dump.contains("AccessibilitySettings") ||
                dump.contains("AccessibilityMain") ||
                dump.contains("AccessibilityFragment"))
}
