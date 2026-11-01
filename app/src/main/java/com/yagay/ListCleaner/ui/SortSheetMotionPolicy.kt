package com.yagay.ListCleaner.ui

/**
 * Keep the sort sheet close to the top without moving its content beyond the
 * bottom edge. The former 23% viewport inset created a huge empty strip.
 */
internal fun sortSheetTopInsetPx(viewportPx: Float, preferredInsetPx: Float): Float =
    preferredInsetPx.coerceAtLeast(0f).coerceAtMost(viewportPx.coerceAtLeast(0f) * 0.10f)

/** Two-stage sheet transitions shared by the sort handle and nested list scroll. */
internal enum class SortSheetRelease { EXPANDED, PARTIAL, DISMISS }

internal fun sortSheetRelease(
    wasExpanded: Boolean,
    positionPx: Float,
    partialOffsetPx: Float,
    thresholdPx: Float,
    releaseVelocityPx: Float,
    flingThresholdPx: Float,
): SortSheetRelease {
    if (!wasExpanded && (positionPx > partialOffsetPx + thresholdPx ||
            (releaseVelocityPx > flingThresholdPx && positionPx >= partialOffsetPx))) {
        return SortSheetRelease.DISMISS
    }
    val shouldExpand = if (wasExpanded) {
        !(positionPx > thresholdPx || releaseVelocityPx > flingThresholdPx)
    } else {
        positionPx < partialOffsetPx - thresholdPx || releaseVelocityPx < -flingThresholdPx
    }
    return if (shouldExpand) SortSheetRelease.EXPANDED else SortSheetRelease.PARTIAL
}
