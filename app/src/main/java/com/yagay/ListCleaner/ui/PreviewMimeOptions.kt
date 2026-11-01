package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.observedShareTypeOptions

/**
 * Preview-only type options. Scanning and rules stay unified; selecting an
 * option creates a different real Android intent, never a cleanup sub-rule.
 */
internal data class PreviewMimeOption(
    val scenario: SystemProbeScenario,
    val mimeType: String? = null,
    val openPreset: OpenPreset? = null,
)

internal fun previewMimeOptions(
    kind: IntentKind,
    candidates: Collection<ComponentCandidate>,
    config: OpenTypeConfig,
): List<PreviewMimeOption> = when (kind) {
    IntentKind.OPEN -> config.configuredPresets().flatMap { preset ->
        val mimes = if (preset.isCustom) {
            config.customDefinitions[preset]?.mimeTypes.orEmpty().sorted()
        } else listOfNotNull(scopedProbeMime(preset, config.customDefinitions))
        when {
            mimes.isNotEmpty() -> mimes.map { mime ->
                PreviewMimeOption(previewScenarioForMime(mime), mimeType = mime, openPreset = preset)
            }
            scopedProbeUri(preset) != null ->
                listOf(PreviewMimeOption(scopedProbeScenario(kind, preset), openPreset = preset))
            else -> emptyList()
        }
    }
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE -> {
        // Standard scenarios remain testable if discovery was incomplete.
        // Also preserve every observed concrete MIME even when several map
        // to one category: JPEG and PNG may invoke different actual handlers.
        val defaults = (availableProbeScenarios(kind) + SystemProbeScenario.ANY)
            .mapNotNull { scenario ->
                scenario.mimeType?.let { PreviewMimeOption(scenario, mimeType = it) }
            }
        val found = observedShareTypeOptions(candidates, kind, config.customDefinitions)
            .flatMap { type ->
                type.mimeTypes.map { mime ->
                    PreviewMimeOption(
                        scenario = previewScenarioForMime(mime),
                        mimeType = mime,
                        openPreset = type.preset,
                    )
                }
            }
        (defaults + found).distinctBy { it.mimeType }
    }
    else -> emptyList()
}

internal fun previewScenarioForMime(mime: String): SystemProbeScenario = when {
    mime.startsWith("image/") -> SystemProbeScenario.IMAGE
    mime == "application/pdf" -> SystemProbeScenario.PDF
    mime.startsWith("video/") -> SystemProbeScenario.VIDEO
    mime.startsWith("audio/") -> SystemProbeScenario.AUDIO
    mime == "*/*" -> SystemProbeScenario.ANY
    else -> SystemProbeScenario.TEXT
}

@Composable
internal fun previewMimeLabel(option: PreviewMimeOption, config: OpenTypeConfig): String {
    val name = option.openPreset?.let { config.localizedTitle(it) }
        ?: stringResource(option.scenario.titleRes)
    val mime = option.mimeType
    return if (mime == null || mime == option.scenario.mimeType && option.openPreset == null)
        name else "$name · $mime"
}

internal fun previewMimeRequest(
    kind: IntentKind,
    userId: Int,
    option: PreviewMimeOption,
    focusPackage: String? = null,
    focusClass: String? = null,
): SystemProbeRequest {
    require(kind in setOf(IntentKind.OPEN, IntentKind.SHARE, IntentKind.SHARE_MULTIPLE))
    return SystemProbeRequest(
        entryKind = kind,
        userId = userId,
        scenario = option.scenario,
        openPreset = option.openPreset.takeIf { kind == IntentKind.OPEN },
        customMimeType = option.mimeType.takeIf { kind == IntentKind.OPEN },
        shareMimeType = option.mimeType.takeIf { kind != IntentKind.OPEN },
        focusPackage = focusPackage,
        focusClass = focusClass,
        directLaunch = true,
    )
}
