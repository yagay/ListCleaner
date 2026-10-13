package com.yagay.ListCleaner.ui

import androidx.compose.ui.state.ToggleableState

/**
 * A shared contract for selection summaries across rule and Root component lists.
 * An empty or non-editable group must never be presented as fully selected.
 */
internal fun listSelectionState(selectedCount: Int, eligibleCount: Int): ToggleableState =
    when {
        eligibleCount <= 0 || selectedCount <= 0 -> ToggleableState.Off
        selectedCount >= eligibleCount -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
