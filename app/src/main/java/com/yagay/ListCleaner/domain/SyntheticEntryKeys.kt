package com.yagay.ListCleaner.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Stable synthetic class-name keys for non-component shortcut surfaces. */
object SyntheticEntryKeys {
    private const val SHORTCUT_MARKER = "#shortcut#"
    private const val DIRECT_SHARE_MARKER = "#direct#"

    fun shortcutItemClass(activityClass: String?, shortcutId: String): String =
        baseClass(activityClass) + SHORTCUT_MARKER + token(shortcutId)

    fun directShareClass(targetClass: String?, shortcutId: String): String =
        baseClass(targetClass) + DIRECT_SHARE_MARKER + token(shortcutId)

    private fun baseClass(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: "@entry"

    private fun token(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
