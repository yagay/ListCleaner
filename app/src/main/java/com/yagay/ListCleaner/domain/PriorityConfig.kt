package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

@Serializable
data class PriorityConfig(
    val apps: Map<IntentKind, List<String>> = emptyMap(),
    val titles: Map<String, String> = emptyMap()
) {
    fun validated(): PriorityConfig {
        require(apps.values.all { packages ->
            packages.size <= 200 && packages.distinct().size == packages.size &&
                packages.all { it.isNotBlank() && it.length <= 255 && '|' !in it }
        }) { "invalid_priority_config" }
        require(titles.size <= 2_000 && titles.all { (key, value) ->
            val parsed = ComponentRule.fromId(key)
            parsed != null && parsed.id == key && value.isNotBlank() && value.length <= 64 &&
                value.none { it.isISOControl() }
        }) { "invalid_component_title_config" }
        return this
    }
}

/** Stable promotion only: never inject targets or move targets between user/profile slots. */
fun <T> prioritizeApps(
    candidates: List<T>, packages: List<String>, packageOf: (T) -> String, profileOf: (T) -> Int
): List<T> {
    if (packages.isEmpty() || candidates.size < 2) return candidates
    val ranks = packages.withIndex().associate { it.value to it.index }
    val result = candidates.toMutableList()
    candidates.indices.groupBy { profileOf(candidates[it]) }.values.forEach { positions ->
        val ordered = positions.map { candidates[it] }.sortedBy { ranks[packageOf(it)] ?: Int.MAX_VALUE }
        positions.forEachIndexed { index, position -> result[position] = ordered[index] }
    }
    return result
}
