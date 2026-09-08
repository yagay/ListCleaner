package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

@Serializable
data class TileConfig(val enabled: Boolean = false, val hidden: Set<String> = emptySet()) {
    fun validated(): TileConfig {
        require(hidden.size <= 512 && hidden.all { TilePolicy.canonical(it) == it }) {
            "invalid_legacy_tile_config"
        }
        return this
    }
}
