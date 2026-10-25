package com.yagay.ListCleaner.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Stable synthetic class-name keys for logical entry surfaces that are not one real component. */
object SyntheticEntryKeys {
    private const val SHORTCUT_MARKER = "#shortcut#"
    private const val DIRECT_SHARE_MARKER = "#direct#"

    fun shortcutItemClass(activityClass: String?, shortcutId: String): String =
        "@entry" + SHORTCUT_MARKER + token(shortcutId)

    /** Canonicalizes older activity-qualified launcher shortcut keys without losing their ID hash. */
    fun canonicalShortcutClass(className: String): String {
        val suffix = className.substringAfter(SHORTCUT_MARKER, "")
        return if (suffix.length == 24 && suffix.all { it in '0'..'9' || it in 'a'..'f' })
            "@entry" + SHORTCUT_MARKER + suffix
        else className
    }

    fun directShareClass(targetClass: String?, shortcutId: String): String =
        baseClass(targetClass) + DIRECT_SHARE_MARKER + token(shortcutId)

    /** Stable per-root identity without storing a raw provider root ID in rules. */
    fun documentRootClass(authority: String, rootId: String): String {
        require(authority.isNotBlank() && rootId.isNotBlank())
        return "@docroot_" + token(authority.length.toString() + ":" + authority + ":" + rootId.length + ":" + rootId)
    }

    fun packageScopedClass(kind: IntentKind): String = when (kind) {
        IntentKind.ASSISTANT -> "@assistant"
        IntentKind.HOME -> "@home"
        IntentKind.BROWSER -> "@browser"
        IntentKind.CALL_SCREENING -> "@call_screening"
        IntentKind.VPN -> "@vpn"
        IntentKind.AUTOFILL -> "@autofill"
        IntentKind.CREDENTIAL_PROVIDER -> "@credential_provider"
        else -> error("${kind.name} is not package-scoped")
    }

    fun packageScopedRule(kind: IntentKind, packageName: String): ComponentRule {
        require(kind.isPackageScopedEntry()) { "${kind.name} is not package-scoped" }
        return ComponentRule(kind, packageName, packageScopedClass(kind))
    }

    /** Compatibility helpers retained for existing callers/tests. */
    fun assistantPackageClass(): String = packageScopedClass(IntentKind.ASSISTANT)

    fun assistantPackageRule(packageName: String): ComponentRule =
        packageScopedRule(IntentKind.ASSISTANT, packageName)

    fun normalizePackageScopedRule(rule: ComponentRule): ComponentRule =
        if (rule.kind.isPackageScopedEntry()) packageScopedRule(rule.kind, rule.packageName) else rule

    private fun baseClass(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: "@entry"

    private fun token(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
