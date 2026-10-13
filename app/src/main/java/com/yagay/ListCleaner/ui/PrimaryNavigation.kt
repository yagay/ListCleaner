package com.yagay.ListCleaner.ui

/** Root components remain accessible from Rules, but are not a top-level tab. */
internal val primaryDestinations: List<Destination> = listOf(
    Destination.RULES,
    Destination.PRIORITY,
    Destination.DASHBOARD
)

internal fun Destination.primaryTab(): Destination =
    if (this == Destination.TILES) Destination.RULES else this
