package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SortSheetMotionPolicyTest {
    private fun release(
        expanded: Boolean,
        position: Float,
        velocity: Float = 0f,
    ) = sortSheetRelease(
        wasExpanded = expanded,
        positionPx = position,
        partialOffsetPx = 250f,
        thresholdPx = 60f,
        releaseVelocityPx = velocity,
        flingThresholdPx = 600f,
    )

    @Test fun sheetTopGapIsCompactAndRespectsSmallViewports() {
        // A 1000px view formerly left a 230px gap.
        assertEquals(48f, sortSheetTopInsetPx(1000f, 48f), 0.001f)
        assertEquals(30f, sortSheetTopInsetPx(300f, 48f), 0.001f)
        assertEquals(0f, sortSheetTopInsetPx(0f, 48f), 0.001f)
        assertEquals(0f, sortSheetTopInsetPx(600f, -20f), 0.001f)
    }

    @Test fun upwardDragOrFlingExpandsToTop() {
        assertEquals(SortSheetRelease.EXPANDED, release(false, 160f))
        assertEquals(SortSheetRelease.EXPANDED, release(false, 245f, -800f))
        assertEquals(SortSheetRelease.EXPANDED, release(true, 20f))
    }

    @Test fun firstDownwardDragCollapsesThenSecondDismisses() {
        assertEquals(SortSheetRelease.PARTIAL, release(true, 85f))
        assertEquals(SortSheetRelease.PARTIAL, release(true, 20f, 900f))
        assertEquals(SortSheetRelease.DISMISS, release(false, 330f))
        assertEquals(SortSheetRelease.DISMISS, release(false, 255f, 850f))
    }

    @Test fun smallGesturesReturnToTheirOwnAnchor() {
        assertEquals(SortSheetRelease.PARTIAL, release(false, 265f))
        assertEquals(SortSheetRelease.PARTIAL, release(false, 232f))
        assertEquals(SortSheetRelease.EXPANDED, release(true, 40f))
    }
}
