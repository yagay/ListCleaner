package com.yagay.ListCleaner.ui

/**
 * Only changes the preview's suggested filename, never its cleanup rule,
 * destination or selected file. Android's file picker must still decide
 * which actual files are shown for ACTION_GET_CONTENT / OPEN_DOCUMENT.
 */
internal fun previewExtensionForMime(mimeType: String?): String {
    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return when (mime) {
        "text/plain" -> "txt"
        "text/html" -> "html"
        "text/markdown" -> "md"
        "text/csv" -> "csv"
        "text/calendar" -> "ics"
        "text/vcard", "text/x-vcard" -> "vcf"
        "application/pdf" -> "pdf"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        "image/avif" -> "avif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/tiff" -> "tif"
        "image/bmp" -> "bmp"
        "image/*" -> "png"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/x-matroska" -> "mkv"
        "video/3gpp" -> "3gp"
        "video/*" -> "mp4"
        "audio/mpeg" -> "mp3"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        "audio/ogg" -> "ogg"
        "audio/mp4" -> "m4a"
        "audio/*" -> "mp3"
        "application/zip", "application/x-zip-compressed" -> "zip"
        "application/vnd.rar", "application/x-rar-compressed" -> "rar"
        "application/x-7z-compressed" -> "7z"
        "application/x-tar" -> "tar"
        "application/gzip" -> "gz"
        "application/epub+zip" -> "epub"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "application/vnd.oasis.opendocument.text" -> "odt"
        "application/vnd.oasis.opendocument.spreadsheet" -> "ods"
        "application/vnd.oasis.opendocument.presentation" -> "odp"
        "application/rtf" -> "rtf"
        "application/vnd.android.package-archive" -> "apk"
        "application/x-bittorrent" -> "torrent"
        "application/json" -> "json"
        "application/xml", "text/xml" -> "xml"
        "application/yaml", "text/yaml" -> "yaml"
        "*/*", "application/octet-stream" -> "bin"
        else -> {
            // The extension of an unknown MIME cannot be inferred from its
            // name reliably. Never silently mislabel it as text/plain.
            "bin"
        }
    }
}

internal fun previewDocumentName(mimeType: String?, baseName: String): String {
    val stem = baseName.substringBeforeLast('.').ifBlank { "ListCleaner-preview" }
    return "$stem.${previewExtensionForMime(mimeType)}"
}

internal fun previewMimeForScenario(
    kind: com.yagay.ListCleaner.domain.IntentKind,
    scenario: SystemProbeScenario,
    request: SystemProbeRequest?,
): String = when (kind) {
    com.yagay.ListCleaner.domain.IntentKind.OPEN ->
        request?.customMimeType ?: scenario.mimeType ?: "application/octet-stream"
    com.yagay.ListCleaner.domain.IntentKind.SHARE,
    com.yagay.ListCleaner.domain.IntentKind.SHARE_MULTIPLE ->
        request?.shareMimeType ?: scenario.mimeType ?: "text/plain"
    com.yagay.ListCleaner.domain.IntentKind.DIRECT_SHARE ->
        request?.directShareType?.mime ?: scenario.mimeType ?: "text/plain"
    com.yagay.ListCleaner.domain.IntentKind.PICK ->
        scenario.mimeType ?: "image/*"
    com.yagay.ListCleaner.domain.IntentKind.CREATE_DOCUMENT,
    com.yagay.ListCleaner.domain.IntentKind.GET_CONTENT,
    com.yagay.ListCleaner.domain.IntentKind.OPEN_DOCUMENT ->
        scenario.mimeType ?: "*/*"
    else -> scenario.mimeType ?: "*/*"
}
