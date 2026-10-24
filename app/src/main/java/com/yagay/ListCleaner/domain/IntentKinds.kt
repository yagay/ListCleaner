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
    // A few file providers place the usable file URI in ClipData instead of data.
    // Evaluate all supplied file URI candidates through the same typed-open policy.
    val candidates = buildList {
        effective.data?.let { uri ->
            add(uri.lastPathSegment ?: uri.path)
            if (uri.path != uri.lastPathSegment) add(uri.path)
        }
        effective.clipData?.let { clips ->
            for (index in 0 until clips.itemCount) {
                val uri = clips.getItemAt(index).uri ?: continue
                if (uri.scheme == "content" || uri.scheme == "file") {
                    add(uri.lastPathSegment ?: uri.path)
                }
            }
        }
    }
    return if (candidates.any { filePath ->
        matchOpenPreset(IntentKind.OPEN, mime, scheme, filePath) != null
    }) IntentKind.OPEN else null
}
