package com.yagay.ListCleaner.domain

import android.content.IntentFilter
import android.content.pm.ResolveInfo

/**
 * Distinguish generic web browsers from host/path-specific App Link handlers.
 * This is used only after the Intent itself has already been classified as an http/https web VIEW.
 */
fun ResolveInfo.webTargetKind(): IntentKind {
    val resolvedFilter = filter
    val authorityCount = runCatching { resolvedFilter?.countDataAuthorities() ?: -1 }.getOrDefault(-1)
    val hasWebScheme = resolvedFilter?.let(::hasHttpScheme) ?: false
    return classifyWebTarget(handleAllWebDataURI, authorityCount, resolvedFilter != null, hasWebScheme)
}

internal fun classifyWebTarget(
    handlesAllWebUrls: Boolean,
    authorityCount: Int,
    filterKnown: Boolean,
    hasWebScheme: Boolean
): IntentKind = when {
    handlesAllWebUrls -> IntentKind.BROWSER
    authorityCount > 0 -> IntentKind.DEEP_LINK
    filterKnown && hasWebScheme -> IntentKind.BROWSER
    else -> IntentKind.DEEP_LINK
}

private fun hasHttpScheme(filter: IntentFilter): Boolean {
    for (index in 0 until filter.countDataSchemes()) {
        if (filter.getDataScheme(index)?.lowercase() in setOf("http", "https")) return true
    }
    return false
}
