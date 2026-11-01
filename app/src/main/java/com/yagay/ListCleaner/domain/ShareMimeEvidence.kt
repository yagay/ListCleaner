package com.yagay.ListCleaner.domain

/**
 * Share/Multi Share resolve different Intent actions. Keep their observed MIME
 * inventories separate and do not present retained-only (unavailable) rules as
 * evidence that an Android target supports a format.
 */
private val shareProbeMime = Regex("""(?:^|\s)mime=([^\s]+)""")
private val validShareMime = Regex("""^(?:\*/\*|[a-z0-9!#$&^_.+-]+/(?:[a-z0-9!#$&^_.+-]+|\*))$""")

private fun shareMimeProbes(item: ComponentCandidate): Set<String> {
    val prefix = when (item.rule.kind) {
        IntentKind.SHARE -> "SEND "
        IntentKind.SHARE_MULTIPLE -> "SEND_MULTIPLE "
        else -> return emptySet()
    }
    return item.evidence.asSequence().filter { line ->
        line.startsWith(prefix) || line.startsWith("BROAD $prefix")
    }.mapNotNull { line ->
        shareProbeMime.find(line)?.groupValues?.getOrNull(1)
            ?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { it.length <= 127 && validShareMime.matches(it) }
    }.toSet()
}

fun observedShareMimeTypes(
    candidates: Collection<ComponentCandidate>,
    kind: IntentKind,
): List<String> {
    if (kind != IntentKind.SHARE && kind != IntentKind.SHARE_MULTIPLE) return emptyList()
    val groups = listOf("text", "image", "video", "audio", "application")
    return candidates.asSequence()
        .filter { it.rule.kind == kind && it.isCatalogCandidate }
        .flatMap { shareMimeProbes(it).asSequence() }
        .distinct()
        .sortedWith(compareBy<String> {
            groups.indexOf(it.substringBefore('/')).let { index ->
                if (index == -1) groups.size else index
            }
        }.thenBy { it })
        .toList()
}

/**
 * A wildcard receiver can handle more than one tested MIME. Matching a
 * selected type includes such targets; choosing wildcard itself shows only
 * receivers actually observed from that wildcard probe.
 */
fun ComponentCandidate.matchesShareMimeType(selected: String): Boolean {
    if (rule.kind !in setOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE)) return false
    val type = selected.trim().lowercase()
    if (type.length > 127 || !validShareMime.matches(type)) return false
    return shareMimeProbes(this).any { observed ->
        observed == type || (type != "*/*" && (
            observed == "*/*" ||
                (observed.endsWith("/*") && observed.substringBefore('/') == type.substringBefore('/'))
        ))
    }
}
