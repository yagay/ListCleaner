package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class CustomOpenDefinition(
    val title: String,
    val mimeTypes: Set<String> = emptySet(),
    val extensions: Set<String> = emptySet()
) {
    fun validated(): CustomOpenDefinition {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 24 && cleanTitle.none { it.isISOControl() }) { "custom_open_invalid_title" }
        require(mimeTypes.size <= 24 && mimeTypes.all(::validMime)) { "custom_open_invalid_mime" }
        require(extensions.size <= 48 && extensions.all(::validExtension)) { "custom_open_invalid_extension" }
        require(mimeTypes.isNotEmpty() || extensions.isNotEmpty()) { "custom_open_missing_matcher" }
        return copy(
            title = cleanTitle,
            mimeTypes = mimeTypes.map { it.trim().lowercase() }.toSet(),
            extensions = extensions.map { it.trim().removePrefix(".").lowercase() }.toSet()
        )
    }

    private fun validMime(value: String): Boolean {
        val mime = value.trim().lowercase()
        if (mime.length !in 3..127 || '/' !in mime || mime.any { it.isWhitespace() || it.isISOControl() }) return false
        val (type, subtype) = mime.split('/', limit = 2).let { it[0] to it[1] }
        return type.isNotEmpty() && subtype.isNotEmpty() && '*' !in type && (subtype == "*" || '*' !in subtype)
    }

    private fun validExtension(value: String): Boolean {
        val ext = value.trim().removePrefix(".").lowercase()
        return ext.length in 1..24 && ext.all { it.isLetterOrDigit() || it in setOf('_', '-') }
    }
}

/**
 * Process-local registry used by the four-argument matcher call sites inside the hooked Android
 * processes. ModuleConfig.validated() refreshes it whenever a new remote snapshot is decoded.
 */
object CustomOpenRegistry {
    private val definitions = AtomicReference<Map<OpenPreset, CustomOpenDefinition>>(emptyMap())
    fun replace(value: Map<OpenPreset, CustomOpenDefinition>) { definitions.set(value.toMap()) }
    fun snapshot(): Map<OpenPreset, CustomOpenDefinition> = definitions.get()
}

/** Per-open-type rules and ordering. Generic OPEN rules/priorities remain the fallback. */
@Serializable
data class OpenTypeConfig(
    val rules: Map<OpenPreset, Set<String>> = emptyMap(),
    val priorities: Map<OpenPreset, List<String>> = emptyMap(),
    val customDefinitions: Map<OpenPreset, CustomOpenDefinition> = emptyMap()
) {
    fun validated(): OpenTypeConfig {
        require(OpenPreset.BROWSER !in rules && OpenPreset.BROWSER !in priorities) { "browser_not_open_type" }
        require(rules.size <= OpenPreset.entries.size && priorities.size <= OpenPreset.entries.size)
        val cleanCustom = customDefinitions.mapValues { (preset, definition) ->
            require(preset.isCustom) { "custom_definition_requires_custom_slot" }
            definition.validated()
        }
        require(cleanCustom.size <= OpenPreset.CUSTOM_SLOTS.size) { "too_many_custom_open_types" }
        require(rules.keys.filter { it.isCustom }.all { it in cleanCustom }) { "custom_rule_missing_definition" }
        require(priorities.keys.filter { it.isCustom }.all { it in cleanCustom }) { "custom_priority_missing_definition" }
        require(rules.values.all { ids ->
            ids.size <= 2_000 && ids.all { id ->
                val parsed = ComponentRule.fromId(id)
                parsed != null && parsed.id == id && parsed.kind == IntentKind.OPEN
            }
        }) { "invalid_open_type_rules" }
        require(priorities.values.all { packages ->
            packages.size <= 200 && packages.distinct().size == packages.size && packages.all {
                it.isNotBlank() && it.length <= 255 && '|' !in it && it.none { ch -> ch.isWhitespace() || ch.isISOControl() }
            }
        }) { "invalid_open_type_priorities" }
        CustomOpenRegistry.replace(cleanCustom)
        return if (cleanCustom == customDefinitions) this else copy(customDefinitions = cleanCustom)
    }

    fun selectedRules(preset: OpenPreset): Set<ComponentRule> =
        rules[preset].orEmpty().mapNotNull(ComponentRule::fromId).toSet()

    fun configuredPresets(): List<OpenPreset> =
        OpenPreset.entries.filter { it != OpenPreset.BROWSER && (!it.isCustom || it in customDefinitions) }
}
