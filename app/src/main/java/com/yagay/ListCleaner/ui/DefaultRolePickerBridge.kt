package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind

/**
 * Resolves a real, system-registered PermissionController role picker.
 * Does not fabricate an Activity name, mutate role holders, or rely on an
 * ordinary app's visibility into protected system activities.
 */
internal object DefaultRolePickerBridge {
    private val componentPattern =
        Regex("""([A-Za-z_][A-Za-z0-9_.]+)/([A-Za-z_.$][A-Za-z0-9_.$]*)""")

    internal fun roleName(kind: IntentKind): String = when (kind) {
        IntentKind.BROWSER -> "android.app.role.BROWSER"
        IntentKind.HOME -> "android.app.role.HOME"
        IntentKind.CALL_SCREENING -> "android.app.role.CALL_SCREENING"
        IntentKind.ASSISTANT -> "android.app.role.ASSISTANT"
        else -> error("Not a RoleController default-app picker: $kind")
    }

    internal fun discoverCommand(userId: Int): String =
        AssistantRolePickerBridge.discoverCommand(userId)

    internal fun systemPackageQueryCommand(userId: Int): String =
        AssistantRolePickerBridge.systemPackageQueryCommand(userId)

    internal fun systemPackages(output: String): Set<String> =
        AssistantRolePickerBridge.systemPackages(output)

    internal fun selectRegisteredComponent(
        output: String, installedSystemPackages: Set<String>,
    ): String? = AssistantRolePickerBridge.selectRegisteredComponent(
        output, installedSystemPackages,
    )

    internal fun launchCommand(
        userId: Int, component: String, kind: IntentKind,
    ): String = AssistantRolePickerBridge.launchCommand(userId, component, roleName(kind))

    private fun normalize(component: String): String? {
        val match = componentPattern.matchEntire(component) ?: return null
        val pkg = match.groupValues[1]
        val cls = match.groupValues[2]
        return "$pkg/" + if (cls.startsWith('.')) pkg + cls else cls
    }

    /** am start -W can succeed after an OEM redirect. Check the *exact* component. */
    internal fun launchAccepted(output: String, component: String): Boolean {
        if (!AssistantRolePickerBridge.launchAccepted(output, component)) return false
        val expected = normalize(component) ?: return false
        return output.lineSequence()
            .filter { it.trim().startsWith("Activity: ") }
            .mapNotNull { line ->
                componentPattern.find(line.substringAfter("Activity: "))?.value
            }
            .any { normalize(it) == expected }
    }

    internal fun foregroundComponent(dumpsys: String): String? {
        val lines = dumpsys.lineSequence().filter {
            it.contains("topResumedActivity=") || it.contains("mResumedActivity:") ||
                it.contains("ResumedActivity:")
        }
        // Prefer the top-resumed Activity; only use old Android field names
        // when the modern field is unavailable.
        val preferred = lines.firstOrNull { it.contains("topResumedActivity=") }
            ?: dumpsys.lineSequence().firstOrNull {
                it.contains("mResumedActivity:") || it.contains("ResumedActivity:")
            }
        return preferred?.let { componentPattern.find(it)?.value }
    }

    internal fun foregroundMatches(dumpsys: String, component: String): Boolean =
        normalize(foregroundComponent(dumpsys).orEmpty()) != null &&
            normalize(foregroundComponent(dumpsys).orEmpty()) == normalize(component)

    internal fun foregroundPackage(dumpsys: String): String? =
        foregroundComponent(dumpsys)?.substringBefore('/')
}
