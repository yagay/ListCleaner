package com.yagay.ListCleaner.ui

/**
 * Read-only diagnostic metadata for Settings navigation. AOSP's
 * "user_installed_services_category" is a PreferenceCategory rather than a
 * separately exported Activity. OEMs may create a private SubSettings page.
 *
 * Only record destination class names and extra-key names, never app labels,
 * package lists, enabled services or arbitrary Bundle values.
 */
internal object SettingsAccessibilityNavigationProbe {
    const val SHOW_FRAGMENT = ":settings:show_fragment"
    const val FRAGMENT_ARGS = ":settings:show_fragment_args"
    const val FRAGMENT_ARG_KEY = ":settings:fragment_args_key"

    fun isSubSettings(activityClass: String): Boolean =
        activityClass.startsWith("com.android.settings.") &&
            activityClass.substringAfterLast('.').contains("SubSettings")

    fun safeClassName(value: String?): String? =
        value?.takeIf { it.length in 3..180 &&
            it.matches(Regex("""[A-Za-z_][A-Za-z0-9_.$]*""")) }

    fun safeExtraKeys(keys: Collection<String>): String =
        keys.asSequence()
            .filter { it.length in 1..100 &&
                it.matches(Regex("""[A-Za-z_:.][A-Za-z0-9_:.$-]*""")) }
            .distinct().sorted().take(36).joinToString(",")
}
