package com.yagay.ListCleaner.domain

import android.content.Intent

fun Intent.intentKind(resolvedType: String? = null): IntentKind? {
    val effective = selector ?: this
    if (effective.action == Intent.ACTION_MAIN && effective.categories?.contains(Intent.CATEGORY_HOME) == true) {
        return IntentKind.HOME
    }
    val mime = effective.type ?: resolvedType
    val scheme = effective.data?.scheme
    val classified = IntentClassification.classify(effective.action, scheme, mime)
    if (classified != null) return IntentKind.valueOf(classified)

    // Some document providers expose a usable PDF/file name in the content URI
    // but supply no MIME type. Reuse the same preset matching as the rule editor
    // instead of maintaining a second extension list in the resolver.
    if (effective.action != Intent.ACTION_VIEW ||
        (scheme != null && scheme != "content" && scheme != "file")) return null
    val normalizedMime = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    if (normalizedMime.isNotEmpty() &&
        normalizedMime !in setOf("*/*", "application/octet-stream", "binary/octet-stream", "application/x-download")) return null
    val filePath = effective.data?.lastPathSegment ?: effective.data?.path
    return if (matchOpenPreset(IntentKind.OPEN, mime, scheme, filePath) != null) IntentKind.OPEN else null
}
