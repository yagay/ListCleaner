package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/**
 * Portable backup. Source IDs identify entries in this archive only: importing onto a different
 * device always uses explicit source-to-target mappings provided by the user.
 */
@Serializable
data class SpaceBackupEntry(
    val sourceUserId: Int,
    val sourceName: String,
    val settings: UserSpaceRules,
    /** Informational only; importing never runs Root commands or changes component protection. */
    val rootDisabledComponents: Set<String> = emptySet(),
)

@Serializable
data class MultiSpaceBackup(
    val version: Int = VERSION,
    val format: String = FORMAT,
    val spaces: List<SpaceBackupEntry>,
) {
    fun validated(): MultiSpaceBackup {
        require(version == VERSION && format == FORMAT) { "Unsupported space backup format" }
        require(spaces.isNotEmpty() && spaces.size <= 16) { "Invalid number of spaces" }
        require(spaces.map { it.sourceUserId }.distinct().size == spaces.size) { "Duplicate source space" }
        spaces.forEach {
            require(it.sourceUserId in 0..99999) { "Invalid source Android user" }
            require(it.sourceName.isNotBlank() && it.sourceName.length <= 100) { "Invalid space name" }
            it.settings.validated()
            require(it.rootDisabledComponents.size <= 20_000) { "Too many component records" }
            require(it.rootDisabledComponents.all { key ->
                val parts = key.split('|', limit = 3)
                parts.size == 3 && parts[0] == it.sourceUserId.toString() &&
                    ComponentStatePolicy.valid(parts[1], parts[2], it.sourceUserId)
            }) { "Invalid component record" }
        }
        return this
    }

    /**
     * Rejects missing sources, duplicate targets and implicit positional mapping.
     * Unselected sources are simply excluded from the mapping.
     */
    fun mapped(mappings: Map<Int, Int>): Map<Int, UserSpaceRules> {
        validated()
        require(mappings.isNotEmpty()) { "Select at least one target space" }
        require(mappings.keys.all { source -> spaces.any { it.sourceUserId == source } })
        require(mappings.values.all { it in 0..99999 })
        require(mappings.values.size == mappings.values.toSet().size) { "Each destination can be selected once" }
        val bySource = spaces.associateBy { it.sourceUserId }
        return mappings.entries.associate { (source, target) ->
            target to requireNotNull(bySource[source]).settings.validated()
        }
    }

    companion object {
        const val FORMAT = "listcleaner-spaces"
        const val VERSION = 13
    }
}
