package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind

/** Parameters alter the real Android Intent query, not a simulated candidate filter. */
internal enum class SystemProbeScenario(val titleRes: Int, val mimeType: String? = null) {
    TEXT(R.string.system_probe_scenario_text, "text/plain"),
    IMAGE(R.string.system_probe_scenario_image, "image/png"),
    PDF(R.string.system_probe_scenario_pdf, "application/pdf"),
    VIDEO(R.string.system_probe_scenario_video, "video/mp4"),
    AUDIO(R.string.system_probe_scenario_audio, "audio/mpeg"),
    ANY(R.string.system_probe_scenario_any, "*/*"),
    MAIL(R.string.system_probe_scenario_email),
    SMS(R.string.system_probe_scenario_sms),
    HTTP(R.string.system_probe_scenario_http),
    HTTPS(R.string.system_probe_scenario_https),
}

/** Only scenarios whose Intent can actually be built are offered in the UI. */
internal fun availableProbeScenarios(kind: IntentKind): List<SystemProbeScenario> = when (kind) {
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE ->
        listOf(SystemProbeScenario.TEXT, SystemProbeScenario.IMAGE, SystemProbeScenario.PDF,
            SystemProbeScenario.VIDEO, SystemProbeScenario.AUDIO)
    IntentKind.OPEN, IntentKind.EDIT ->
        listOf(SystemProbeScenario.TEXT, SystemProbeScenario.IMAGE, SystemProbeScenario.PDF,
            SystemProbeScenario.VIDEO, SystemProbeScenario.AUDIO)
    IntentKind.PICK ->
        listOf(SystemProbeScenario.IMAGE, SystemProbeScenario.VIDEO, SystemProbeScenario.ANY)
    IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT ->
        listOf(SystemProbeScenario.ANY, SystemProbeScenario.TEXT, SystemProbeScenario.IMAGE,
            SystemProbeScenario.PDF, SystemProbeScenario.VIDEO, SystemProbeScenario.AUDIO)
    IntentKind.SEND_TO -> listOf(SystemProbeScenario.MAIL, SystemProbeScenario.SMS)
    IntentKind.BROWSER, IntentKind.DEEP_LINK ->
        listOf(SystemProbeScenario.HTTPS, SystemProbeScenario.HTTP)
    IntentKind.PHOTO_SOURCE -> listOf(SystemProbeScenario.IMAGE)
    else -> emptyList()
}

internal fun defaultProbeScenario(kind: IntentKind): SystemProbeScenario =
    availableProbeScenarios(kind).firstOrNull() ?: SystemProbeScenario.TEXT

/**
 * Keep the preview's initial test aligned to the selected list subtype.
 * The MIME override below covers the many Open presets that have no dedicated menu item.
 */
internal fun scopedProbeScenario(
    kind: IntentKind,
    preset: com.yagay.ListCleaner.domain.OpenPreset? = null,
    directShareType: com.yagay.ListCleaner.domain.DirectShareType? = null,
): SystemProbeScenario = when {
    kind == IntentKind.DIRECT_SHARE -> when (directShareType) {
        com.yagay.ListCleaner.domain.DirectShareType.IMAGE -> SystemProbeScenario.IMAGE
        com.yagay.ListCleaner.domain.DirectShareType.VIDEO -> SystemProbeScenario.VIDEO
        com.yagay.ListCleaner.domain.DirectShareType.AUDIO -> SystemProbeScenario.AUDIO
        com.yagay.ListCleaner.domain.DirectShareType.FILE -> SystemProbeScenario.PDF
        else -> SystemProbeScenario.TEXT
    }
    kind == IntentKind.OPEN -> when (preset) {
        com.yagay.ListCleaner.domain.OpenPreset.PDF -> SystemProbeScenario.PDF
        com.yagay.ListCleaner.domain.OpenPreset.IMAGE,
        com.yagay.ListCleaner.domain.OpenPreset.SVG,
        com.yagay.ListCleaner.domain.OpenPreset.GIF -> SystemProbeScenario.IMAGE
        com.yagay.ListCleaner.domain.OpenPreset.VIDEO -> SystemProbeScenario.VIDEO
        com.yagay.ListCleaner.domain.OpenPreset.AUDIO -> SystemProbeScenario.AUDIO
        else -> defaultProbeScenario(kind)
    }
    else -> defaultProbeScenario(kind)
}

internal fun scopedProbeMime(
    preset: com.yagay.ListCleaner.domain.OpenPreset?,
    custom: Map<com.yagay.ListCleaner.domain.OpenPreset, com.yagay.ListCleaner.domain.CustomOpenDefinition> = emptyMap(),
): String? = when (preset) {
    com.yagay.ListCleaner.domain.OpenPreset.PDF -> "application/pdf"
    com.yagay.ListCleaner.domain.OpenPreset.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    com.yagay.ListCleaner.domain.OpenPreset.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    com.yagay.ListCleaner.domain.OpenPreset.POWERPOINT -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    com.yagay.ListCleaner.domain.OpenPreset.EPUB -> "application/epub+zip"
    com.yagay.ListCleaner.domain.OpenPreset.APK -> "application/vnd.android.package-archive"
    com.yagay.ListCleaner.domain.OpenPreset.TORRENT -> "application/x-bittorrent"
    com.yagay.ListCleaner.domain.OpenPreset.MARKDOWN -> "text/markdown"
    com.yagay.ListCleaner.domain.OpenPreset.CSV -> "text/csv"
    com.yagay.ListCleaner.domain.OpenPreset.JSON -> "application/json"
    com.yagay.ListCleaner.domain.OpenPreset.XML -> "application/xml"
    com.yagay.ListCleaner.domain.OpenPreset.SVG -> "image/svg+xml"
    com.yagay.ListCleaner.domain.OpenPreset.GIF -> "image/gif"
    com.yagay.ListCleaner.domain.OpenPreset.IMAGE -> "image/png"
    com.yagay.ListCleaner.domain.OpenPreset.VIDEO -> "video/mp4"
    com.yagay.ListCleaner.domain.OpenPreset.AUDIO -> "audio/mpeg"
    com.yagay.ListCleaner.domain.OpenPreset.TEXT -> "text/plain"
    com.yagay.ListCleaner.domain.OpenPreset.ARCHIVE -> "application/zip"
    else -> preset?.takeIf { it.isCustom }?.let { custom[it]?.mimeTypes?.sorted()?.firstOrNull() }
}

/** Scheme-based Open kinds use URI data, not a made-up text/plain file. */
internal fun scopedProbeUri(preset: com.yagay.ListCleaner.domain.OpenPreset?): String? = when (preset) {
    com.yagay.ListCleaner.domain.OpenPreset.MAGNET -> "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
    com.yagay.ListCleaner.domain.OpenPreset.GEO -> "geo:0,0?q=London"
    com.yagay.ListCleaner.domain.OpenPreset.MAILTO -> "mailto:preview@example.invalid"
    com.yagay.ListCleaner.domain.OpenPreset.TEL -> "tel:0000000000"
    com.yagay.ListCleaner.domain.OpenPreset.SMS -> "smsto:0000000000"
    else -> null
}
