package com.yagay.ListCleaner.domain

/**
 * Shared Quick Settings TileService contract for management discovery and live API previews.
 *
 * Root component management intentionally discovers disabled components (so a user can
 * re-enable them), whereas an "available right now" preview must omit disabled services.
 * Using one declaration check avoids mismatched counts from inconsistent eligibility rules.
 */
object TileServiceEligibility {
    const val REQUIRED_PERMISSION = "android.permission.BIND_QUICK_SETTINGS_TILE"

    fun declared(permission: String?, exported: Boolean): Boolean =
        permission == REQUIRED_PERMISSION && exported

    fun active(
        permission: String?, exported: Boolean,
        componentEnabled: Boolean, applicationEnabled: Boolean,
    ): Boolean = declared(permission, exported) && componentEnabled && applicationEnabled
}
