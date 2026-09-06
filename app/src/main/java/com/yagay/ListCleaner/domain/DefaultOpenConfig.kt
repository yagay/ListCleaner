package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

@Serializable
enum class OpenPreset(val title: String, val description: String) {
    BROWSER("网页链接", "http / https 链接"),
    PDF("PDF", "application/pdf"),
    IMAGE("图片", "image/*"),
    VIDEO("视频", "video/*"),
    AUDIO("音频", "audio/*"),
    TEXT("文本", "text/*"),
    ARCHIVE("压缩包", "ZIP / RAR / 7Z / TAR / GZIP")
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
    if (kind == IntentKind.BROWSER && (scheme.equals("http", true) || scheme.equals("https", true))) return OpenPreset.BROWSER
    if (kind != IntentKind.OPEN) return null
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return when {
        mime == "application/pdf" -> OpenPreset.PDF
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
