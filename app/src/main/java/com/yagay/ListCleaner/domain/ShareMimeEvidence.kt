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

// Share uses the same familiar file-type names as Open With. One type may
// contain several MIME probes, but only types observed for this Intent action
// are offered. Unknown MIME values remain independently selectable.
data class ShareTypeOption(
    val key: String,
    val preset: OpenPreset? = null,
    val mimeTypes: List<String>,
) {
    val probeMime: String get() = mimeTypes.first()
}

fun observedShareTypeOptions(
    candidates: Collection<ComponentCandidate>,
    kind: IntentKind,
    customDefinitions: Map<OpenPreset, CustomOpenDefinition> = emptyMap(),
): List<ShareTypeOption> {
    val mimeTypes = observedShareMimeTypes(candidates, kind)
    val grouped = linkedMapOf<String, MutableList<String>>()
    for (mime in mimeTypes) {
        val key = shareTypeKeyForMime(mime, customDefinitions) ?: continue
        grouped.getOrPut(key) { mutableListOf() }.add(mime)
    }
    return grouped.map { (key, mimes) ->
        ShareTypeOption(
            key = key,
            preset = key.removePrefix("preset:").takeIf { key.startsWith("preset:") }
                ?.let(OpenPreset::valueOf),
            mimeTypes = mimes,
        )
    }.sortedWith(compareBy<ShareTypeOption> {
        when {
            it.key == "universal" -> -1
            it.preset != null -> it.preset.ordinal
            else -> OpenPreset.entries.size
        }
    }.thenBy { it.key })
}

fun ComponentCandidate.matchesShareTypeOption(option: ShareTypeOption): Boolean =
    option.mimeTypes.any(::matchesShareMimeType)

// Same key derivation in manager UI and in the resolver hook.
fun validShareTypeKey(key: String): Boolean =
    key == "universal" ||
        (key.startsWith("preset:") &&
            runCatching { OpenPreset.valueOf(key.removePrefix("preset:")) }
                .getOrNull()?.let { it != OpenPreset.BROWSER } == true) ||
        (key.startsWith("mime:") &&
            key.removePrefix("mime:").let { it.length <= 127 && validShareMime.matches(it) })

fun shareTypeKeyForMime(
    mimeType: String?,
    customDefinitions: Map<OpenPreset, CustomOpenDefinition> = CustomOpenRegistry.snapshot(),
): String? {
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        ?.takeIf { it.isNotEmpty() && it.length <= 127 && validShareMime.matches(it) }
        ?: return null
    if (mime == "*/*") return "universal"
    val preset = matchOpenPreset(IntentKind.OPEN, mime, null, customDefinitions = customDefinitions)
    return if (preset != null && preset != OpenPreset.BROWSER) "preset:${preset.name}"
        else "mime:$mime"
}
