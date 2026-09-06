package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

@Serializable
enum class OpenPreset(val title: String, val description: String) {
    BROWSER("网页链接", "http / https 链接"),
    PDF("PDF", "application/pdf"),
    WORD("Word", "DOC / DOCX"),
    EXCEL("Excel", "XLS / XLSX"),
    POWERPOINT("PowerPoint", "PPT / PPTX"),
    EPUB("电子书", "EPUB"),
    APK("Android 安装包", "APK"),
    TORRENT("BT 种子", ".torrent"),
    MARKDOWN("Markdown", "text/markdown"),
    CSV("CSV", "text/csv"),
    JSON("JSON", "application/json"),
    XML("XML", "application/xml / text/xml"),
    SVG("SVG", "image/svg+xml"),
    GIF("GIF", "image/gif"),
    IMAGE("图片", "image/*"),
    VIDEO("视频", "video/*"),
    AUDIO("音频", "audio/*"),
    TEXT("文本", "text/*"),
    ARCHIVE("压缩包", "ZIP / RAR / 7Z / TAR / GZIP"),
    MAGNET("磁力链接", "magnet:"),
    GEO("地图位置", "geo:"),
    MAILTO("邮件链接", "mailto:"),
    TEL("电话链接", "tel:"),
    SMS("短信链接", "sms: / smsto:")
}

@Serializable
data class DefaultOpenConfig(
    val preferred: Map<OpenPreset, String> = emptyMap()
) {
    fun validated(): DefaultOpenConfig {
        require(preferred.size <= OpenPreset.entries.size)
        preferred.forEach { (preset, id) ->
            val rule = requireNotNull(ComponentRule.fromId(id)) { "默认打开组件无效" }
            require(rule.id == id) { "默认打开组件必须使用规范化类名" }
            require(if (preset == OpenPreset.BROWSER) rule.kind == IntentKind.BROWSER else rule.kind == IntentKind.OPEN) {
                "默认打开组件分类不匹配"
            }
        }
        return this
    }
}

fun matchOpenPreset(kind: IntentKind, mimeType: String?, scheme: String?): OpenPreset? {
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
    return when {
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
        // SVG is XML-based, so it must beat the generic +xml rule.
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
}

/** Uses scan evidence so the rules/sort pages only show components that actually matched this preset probe. */
fun ComponentCandidate.matchesOpenPreset(preset: OpenPreset): Boolean {
    if (preset == OpenPreset.BROWSER || rule.kind != IntentKind.OPEN) return false
    return evidence.any { line ->
        val mime = Regex("(?:^|\\s)mime=([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
        val scheme = Regex("(?:^|\\s)scheme=([^\\s]+)").find(line)?.groupValues?.getOrNull(1)
        matchOpenPreset(IntentKind.OPEN, mime, scheme) == preset
    }
}
