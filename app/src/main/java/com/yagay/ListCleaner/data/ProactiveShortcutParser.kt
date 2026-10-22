package com.yagay.ListCleaner.data

/**
 * AOSP "cmd shortcut get-shortcuts" uses ShortcutInfo.toDumpString, not JSON.
 * Treat its output as untrusted diagnostics: do not persist raw intents, persons or private extras.
 */
internal data class ProactiveShortcutRecord(
    val packageName: String,
    val shortcutId: String,
    val activity: String?,
    val label: String,
)

internal object ProactiveShortcutParser {
    const val MARKER = "LC_SHORTCUT_PACKAGE:"
    private val packageLine = Regex("^" + MARKER + "([A-Za-z][A-Za-z0-9_.]*)$")
    private val start = Regex("ShortcutInfo \\{id=([^,\\r\\n]{1,300}),")
    private val labels = Regex("(?m)^\\s*shortLabel=([^,\\r\\n]{1,160})")
    private val activities = Regex("(?m)^\\s*activity=([^\\r\\n]{1,300})")
    private val categories = Regex("(?m)^\\s*categories=\\[([^]\\r\\n]+)]")
    private val flags = Regex("flags=0x[0-9a-fA-F]+ \\[([^]]*)]")

    fun parse(output: String, allowedPackages: Set<String>): List<ProactiveShortcutRecord> {
        val byKey = linkedMapOf<String, ProactiveShortcutRecord>()
        var currentPackage: String? = null
        val block = StringBuilder()
        fun flush() {
            val pkg = currentPackage ?: return
            val text = block.toString()
            val entries = start.findAll(text).toList()
            entries.forEachIndexed { index, match ->
                val id = match.groupValues[1].trim()
                val section = text.substring(match.range.first,
                    entries.getOrNull(index + 1)?.range?.first ?: text.length)
                val tags = flags.find(section)?.groupValues?.getOrNull(1).orEmpty()
                if (Regex("\\bDis\\b").containsMatchIn(tags)) return@forEachIndexed
                if (!Regex("\\bDyn\\b|\\bMan\\b").containsMatchIn(tags)) return@forEachIndexed
                if (categories.find(section)?.groupValues?.getOrNull(1)?.isBlank() != false) {
                    return@forEachIndexed
                }
                val activity = activities.find(section)?.groupValues?.getOrNull(1)
                    ?.trim()?.takeIf { it != "null" && it.isNotEmpty() }
                val label = labels.find(section)?.groupValues?.getOrNull(1)
                    ?.trim()?.takeIf { it != "null" }.orEmpty()
                byKey["$pkg|$id"] = ProactiveShortcutRecord(pkg, id, activity, label)
            }
            block.setLength(0)
        }
        output.lineSequence().forEach { line ->
            val match = packageLine.matchEntire(line.trim())
            if (match != null) {
                flush()
                currentPackage = match.groupValues[1].takeIf { it in allowedPackages }
            } else if (currentPackage != null && block.length < 100_000) {
                block.append(line).append('\n')
            }
        }
        flush()
        return byKey.values.take(512)
    }
}
