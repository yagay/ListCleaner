package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** Per-open-type rules and ordering. Generic OPEN rules/priorities remain the fallback. */
@Serializable
data class OpenTypeConfig(
    val rules: Map<OpenPreset, Set<String>> = emptyMap(),
    val priorities: Map<OpenPreset, List<String>> = emptyMap()
) {
    fun validated(): OpenTypeConfig {
        require(OpenPreset.BROWSER !in rules && OpenPreset.BROWSER !in priorities) { "浏览器不属于打开方式类型规则" }
        require(rules.size <= OpenPreset.entries.size && priorities.size <= OpenPreset.entries.size)
        require(rules.values.all { ids ->
            ids.size <= 2_000 && ids.all { id ->
                val parsed = ComponentRule.fromId(id)
                parsed != null && parsed.id == id && parsed.kind == IntentKind.OPEN
            }
        }) { "打开类型规则无效" }
        require(priorities.values.all { packages ->
            packages.size <= 200 && packages.distinct().size == packages.size && packages.all {
                it.isNotBlank() && it.length <= 255 && '|' !in it && it.none { ch -> ch.isWhitespace() || ch.isISOControl() }
            }
        }) { "打开类型排序配置无效" }
        return this
    }

    fun selectedRules(preset: OpenPreset): Set<ComponentRule> =
        rules[preset].orEmpty().mapNotNull(ComponentRule::fromId).toSet()
}
