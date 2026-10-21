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
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE ->
        listOf(SystemProbeScenario.TEXT, SystemProbeScenario.IMAGE, SystemProbeScenario.PDF)
    IntentKind.OPEN ->
        listOf(SystemProbeScenario.TEXT, SystemProbeScenario.IMAGE, SystemProbeScenario.PDF)
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
