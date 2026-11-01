package com.yagay.ListCleaner.ui

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
