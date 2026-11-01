package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SortSheetMotionPolicyTest {
    private fun release(drag: Float, velocity: Float = 0f) = sortSheetRelease(
        dragOffsetPx = drag,
        dismissDistancePx = 72f,
        releaseVelocityPx = velocity,
        flingThresholdPx = 640f,
    )

    @Test fun sheetTopGapIsCompactAndRespectsSmallViewports() {
        assertEquals(48f, sortSheetTopInsetPx(1000f, 48f), 0.001f)
        assertEquals(30f, sortSheetTopInsetPx(300f, 48f), 0.001f)
        assertEquals(0f, sortSheetTopInsetPx(0f, 48f), 0.001f)
        assertEquals(0f, sortSheetTopInsetPx(600f, -20f), 0.001f)
    }

    @Test fun singleDownwardDragDismissesWithoutIntermediatePosition() {
        assertEquals(SortSheetRelease.DISMISS, release(72f))
        assertEquals(SortSheetRelease.DISMISS, release(160f))
        assertEquals(SortSheetRelease.DISMISS, release(15f, velocity = 900f))
    }

    @Test fun shortOrUpwardGesturesReturnToSameOpenPosition() {
        assertEquals(SortSheetRelease.OPEN, release(0f))
        assertEquals(SortSheetRelease.OPEN, release(35f))
        assertEquals(SortSheetRelease.OPEN, release(71f))
        assertEquals(SortSheetRelease.OPEN, release(0f, velocity = 950f))
        assertEquals(SortSheetRelease.OPEN, release(20f, velocity = -900f))
    }
}
