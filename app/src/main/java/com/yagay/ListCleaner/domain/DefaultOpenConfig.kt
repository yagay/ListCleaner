package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

@Serializable
enum class OpenPreset {
    BROWSER,
    PDF,
    WORD,
    EXCEL,
    POWERPOINT,
    EPUB,
    APK,
    TORRENT,
    MARKDOWN,
    CSV,
    JSON,
    XML,
    SVG,
    GIF,
    IMAGE,
    VIDEO,
    AUDIO,
    TEXT,
    ARCHIVE,
    MAGNET,
    GEO,
    MAILTO,
    TEL,
    SMS,
    CUSTOM_1,
    CUSTOM_2,
    CUSTOM_3,
    CUSTOM_4,
    CUSTOM_5,
    CUSTOM_6,
    CUSTOM_7,
    CUSTOM_8;

    val isCustom: Boolean get() = this in CUSTOM_SLOTS

    companion object {
        val CUSTOM_SLOTS = listOf(CUSTOM_1, CUSTOM_2, CUSTOM_3, CUSTOM_4, CUSTOM_5, CUSTOM_6, CUSTOM_7, CUSTOM_8)
    }
}

/** Deprecated backup compatibility only. Runtime no longer applies a forced default handler. */
@Serializable
data class DefaultOpenConfig(
    val preferred: Map<OpenPreset, String> = emptyMap()
) {
    fun validated(): DefaultOpenConfig {
        require(preferred.size <= OpenPreset.entries.size)
        preferred.forEach { (preset, id) ->
            val rule = requireNotNull(ComponentRule.fromId(id)) { "invalid_default_open_component" }
            require(rule.id == id) { "default_open_component_not_normalized" }
            require(if (preset == OpenPreset.BROWSER) rule.kind == IntentKind.BROWSER else rule.kind == IntentKind.OPEN) {
                "default_open_component_kind_mismatch"
            }
        }
        return this
    }
}

fun matchOpenPreset(
    kind: IntentKind,
    mimeType: String?,
    scheme: String?,
    fileNameOrPath: String? = null,
    customDefinitions: Map<OpenPreset, CustomOpenDefinition> = CustomOpenRegistry.snapshot()
): OpenPreset? {
    val normalizedScheme = scheme?.lowercase()
    if (kind == IntentKind.BROWSER && normalizedScheme in setOf("http", "https")) return OpenPreset.BROWSER
    if (kind != IntentKind.OPEN) return null

    when (normalizedScheme) {
        "magnet" -> return OpenPreset.MAGNET
        "geo" -> return OpenPreset.GEO
        "mailto" -> return OpenPreset.MAILTO
        "tel" -> return OpenPreset.TEL
        "sms", "smsto" -> return OpenPreset.SMS
    }

    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    customDefinitions.entries.firstOrNull { (_, definition) ->
        definition.mimeTypes.any { configured ->
            configured == mime || configured.endsWith("/*") && mime.startsWith(configured.removeSuffix("*"))
        }
    }?.let { return it.key }

    val mimePreset = when {
        mime == "application/pdf" -> OpenPreset.PDF
        mime in setOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document") -> OpenPreset.WORD
        mime in setOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") -> OpenPreset.EXCEL
        mime in setOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation") -> OpenPreset.POWERPOINT
        mime == "application/epub+zip" -> OpenPreset.EPUB
        mime == "application/vnd.android.package-archive" -> OpenPreset.APK
        mime == "application/x-bittorrent" -> OpenPreset.TORRENT
        mime in setOf("text/markdown", "text/x-markdown") -> OpenPreset.MARKDOWN
        mime in setOf("text/csv", "application/csv") -> OpenPreset.CSV
        mime in setOf("application/json", "text/json") || mime.endsWith("+json") -> OpenPreset.JSON
        mime == "image/svg+xml" -> OpenPreset.SVG
        mime == "image/gif" -> OpenPreset.GIF
        mime in setOf("application/xml", "text/xml") || mime.endsWith("+xml") -> OpenPreset.XML
        mime.startsWith("image/") -> OpenPreset.IMAGE
        mime.startsWith("video/") -> OpenPreset.VIDEO
        mime.startsWith("audio/") -> OpenPreset.AUDIO
        mime.startsWith("text/") -> OpenPreset.TEXT
        mime in setOf(
            "application/zip", "application/x-zip-compressed", "application/x-rar-compressed",
            "application/vnd.rar", "application/x-7z-compressed", "application/x-tar",
            "application/gzip", "application/x-gzip"
        ) -> OpenPreset.ARCHIVE
        else -> null
    }
    if (mimePreset != null) return mimePreset

    val extension = normalizedExtension(fileNameOrPath)
    if (extension != null) {
        customDefinitions.entries.firstOrNull { (_, definition) -> extension in definition.extensions }?.let { return it.key }
    }

    if (mime.isNotEmpty() && mime !in OPAQUE_FILE_MIMES) return null
    return matchOpenPresetByExtension(fileNameOrPath)
}

private val OPAQUE_FILE_MIMES = setOf(
    "*/*",
    "application/octet-stream",
    "binary/octet-stream",
    "application/x-download"
)

private fun normalizedExtension(fileNameOrPath: String?): String? {
    val clean = fileNameOrPath?.trim()?.substringBefore('?')?.substringBefore('#')?.lowercase().orEmpty()
    if (clean.isEmpty()) return null
    return clean.substringAfterLast('/', clean).substringAfterLast('.', "").takeIf { it.isNotEmpty() }
}

private fun matchOpenPresetByExtension(fileNameOrPath: String?): OpenPreset? = when (normalizedExtension(fileNameOrPath)) {
    "pdf" -> OpenPreset.PDF
    "doc", "docx" -> OpenPreset.WORD
    "xls", "xlsx" -> OpenPreset.EXCEL
    "ppt", "pptx" -> OpenPreset.POWERPOINT
    "epub" -> OpenPreset.EPUB
    "apk", "apks", "xapk" -> OpenPreset.APK
    "torrent" -> OpenPreset.TORRENT
    "md", "markdown" -> OpenPreset.MARKDOWN
    "csv" -> OpenPreset.CSV
    "json" -> OpenPreset.JSON
    "xml" -> OpenPreset.XML
    "svg" -> OpenPreset.SVG
    "gif" -> OpenPreset.GIF
    "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif", "avif" -> OpenPreset.IMAGE
    "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp" -> OpenPreset.VIDEO
    "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus" -> OpenPreset.AUDIO
    "txt", "log", "ini", "conf", "cfg" -> OpenPreset.TEXT
    "zip", "rar", "7z", "tar", "gz", "gzip", "tgz", "bz2", "xz" -> OpenPreset.ARCHIVE
    else -> null
}

fun ComponentCandidate.matchesOpenPreset(
    preset: OpenPreset,
    customDefinitions: Map<OpenPreset, CustomOpenDefinition> = CustomOpenRegistry.snapshot()
): Boolean {
    if (preset == OpenPreset.BROWSER || rule.kind != IntentKind.OPEN) return false
    return evidence.any { line ->
        val mime = Regex("(?:^|\\s)mime=([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
        val scheme = Regex("(?:^|\\s)scheme=([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
        val sample = Regex("(?:^|\\s)sample=([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
        matchOpenPreset(IntentKind.OPEN, mime, scheme, sample, customDefinitions) == preset
    }
}
