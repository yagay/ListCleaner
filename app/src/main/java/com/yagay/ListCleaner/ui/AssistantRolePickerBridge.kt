package com.yagay.ListCleaner.ui

/**
 * Pure protocol for the system assistant ROLE_ASSISTANT picker.
 *
 * AOSP Settings routes its "Default digital assistant app" preference to
 * PermissionController's exported DefaultAppActivity (MANAGE_DEFAULT_APP).
 * Discover the *registered* Activity as Root/shell: normal app-side
 * PackageManager visibility is not an authority for privileged Activities.
 *
 * No role changes or package mutations are performed by these commands.
 */
internal object AssistantRolePickerBridge {
    private const val ACTION = "android.intent.action.MANAGE_DEFAULT_APP"
    private const val CATEGORY = "android.intent.category.DEFAULT"
    private const val ROLE_EXTRA = "android.intent.extra.ROLE_NAME"
    private const val ROLE = "android.app.role.ASSISTANT"
    private val componentPattern =
        Regex("""([A-Za-z_][A-Za-z0-9_.]+)/([A-Za-z_.$][A-Za-z0-9_.$]*)""")

    internal fun discoverCommand(userId: Int): String {
        require(userId >= 0)
        return "cmd package query-activities --brief --components --user $userId" +
            " -a $ACTION -c $CATEGORY"
    }

    internal fun systemPackageQueryCommand(userId: Int): String {
        require(userId >= 0)
        return "cmd package list packages -s --user $userId"
    }

    internal fun systemPackages(output: String): Set<String> =
        output.lineSequence().mapNotNull {
            it.trim().takeIf { line -> line.startsWith("package:") }
                ?.removePrefix("package:")
                ?.takeIf { pkg -> pkg.matches(Regex("[A-Za-z_][A-Za-z0-9_.]*")) }
        }.toSet()

    /**
     * Accept only registered PermissionController role UI Activities belonging
     * to a package verified by the OS as a system application. This prevents
     * both an unrelated Settings resolver and a third-party spoofed handler.
     */
    internal fun selectRegisteredComponent(
        output: String, installedSystemPackages: Set<String>
    ): String? {
        return componentPattern.findAll(output).map { it.groupValues[1] to it.groupValues[2] }
            .firstOrNull { (pkg, activity) ->
                pkg in installedSystemPackages &&
                    pkg.contains("permission", ignoreCase = true) &&
                    activity.substringAfterLast('.').equals("DefaultAppActivity",
                        ignoreCase = true)
            }?.let { (pkg, activity) -> "$pkg/$activity" }
    }

    internal fun launchCommand(userId: Int, component: String): String {
        require(userId >= 0)
        require(componentPattern.matchEntire(component) != null)
        require(component.substringBefore('/').contains("permission", ignoreCase = true))
        require(component.substringAfter('/').substringAfterLast('.')
            .equals("DefaultAppActivity", ignoreCase = true))
        return "am start -W --user $userId -n $component" +
            " -a $ACTION -c $CATEGORY --es $ROLE_EXTRA $ROLE"
    }

    internal fun launchAccepted(output: String, component: String): Boolean {
        if (!output.contains("Status: ok", ignoreCase = true)) return false
        if (listOf("Error:", "Error type", "Permission Denial", "SecurityException",
                "Status: timeout", "Status: canceled", "Status: error").any {
                output.contains(it, ignoreCase = true)
            }) return false
        // Activity manager can normalize short class names into fully-qualified names.
        val pkg = component.substringBefore('/')
        return output.lineSequence().any {
            it.trim().startsWith("Activity: ") &&
                it.substringAfter("Activity: ").trim().startsWith("$pkg/")
        }
    }

    /**
     * A successful am start -W is not proof that the requested UI survived an
     * OEM redirect. The foreground Activity must still belong to the picker.
     */
    internal fun foregroundPackage(dumpsys: String): String? {
        val line = dumpsys.lineSequence().firstOrNull {
            it.contains("topResumedActivity=") ||
                it.contains("mResumedActivity:") ||
                it.contains("ResumedActivity:")
        } ?: return null
        return componentPattern.find(line)?.groupValues?.get(1)
    }

    internal fun foregroundMatches(dumpsys: String, component: String): Boolean {
        val pkg = foregroundPackage(dumpsys) ?: return false
        return pkg == component.substringBefore('/')
    }
}
