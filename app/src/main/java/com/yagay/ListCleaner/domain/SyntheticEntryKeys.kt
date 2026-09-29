package com.yagay.ListCleaner.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Stable synthetic class-name keys for logical entry surfaces that are not one real component. */
object SyntheticEntryKeys {
    private const val SHORTCUT_MARKER = "#shortcut#"
    private const val DIRECT_SHARE_MARKER = "#direct#"
    private const val ASSISTANT_PACKAGE_MARKER = "@assistant"

    fun shortcutItemClass(activityClass: String?, shortcutId: String): String =
        baseClass(activityClass) + SHORTCUT_MARKER + token(shortcutId)

    fun directShareClass(targetClass: String?, shortcutId: String): String =
        baseClass(targetClass) + DIRECT_SHARE_MARKER + token(shortcutId)

    /** Assistant is an Android Role and is selected per package, not per activity/service component. */
    fun assistantPackageClass(): String = ASSISTANT_PACKAGE_MARKER

    fun assistantPackageRule(packageName: String): ComponentRule =
        ComponentRule(IntentKind.ASSISTANT, packageName, ASSISTANT_PACKAGE_MARKER)

    fun normalizePackageScopedRule(rule: ComponentRule): ComponentRule = when (rule.kind) {
        IntentKind.ASSISTANT -> assistantPackageRule(rule.packageName)
        else -> rule
    }

    private fun baseClass(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: "@entry"

    private fun token(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
