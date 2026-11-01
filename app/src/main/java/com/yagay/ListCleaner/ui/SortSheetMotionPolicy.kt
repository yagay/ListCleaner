package com.yagay.ListCleaner.ui

/**
 * Keep the sort sheet close to the top without moving its content beyond the
 * bottom edge. The former 23% viewport inset created a huge empty strip.
 */
internal fun sortSheetTopInsetPx(viewportPx: Float, preferredInsetPx: Float): Float =
    preferredInsetPx.coerceAtLeast(0f).coerceAtMost(viewportPx.coerceAtLeast(0f) * 0.10f)

/** One resting height: short drags rebound, downward swipes dismiss. */
internal enum class SortSheetRelease { OPEN, DISMISS }

internal fun sortSheetRelease(
    dragOffsetPx: Float,
    dismissDistancePx: Float,
    releaseVelocityPx: Float,
    flingThresholdPx: Float,
): SortSheetRelease =
    if (dragOffsetPx >= dismissDistancePx ||
        (dragOffsetPx > 0f && releaseVelocityPx > flingThresholdPx)
    ) SortSheetRelease.DISMISS else SortSheetRelease.OPEN
