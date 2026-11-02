package com.yagay.ListCleaner.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import kotlin.math.roundToInt

/** Reordering UI favourites never changes the underlying rule or priority order. */
internal fun <T> movePinnedCategory(items: List<T>, item: T, by: Int): List<T> {
    val from = items.indexOf(item)
    if (from < 0 || by == 0) return items
    val to = (from + by).coerceIn(0, items.lastIndex)
    if (from == to) return items
    return items.toMutableList().apply {
        removeAt(from)
        add(to, item)
    }
}

/** The pinned sets remain the source of truth. Only display order is shared
 * by the Controls home. Existing Entry and Component picker pins stay in sync.
 */
internal fun homeFavoriteKey(category: CleanupCategoryId): String = when (category) {
    is CleanupCategoryId.Entry -> "ENTRY:${category.kind.name}"
    is CleanupCategoryId.Root -> "COMPONENT:${category.kind.name}"
    is CleanupCategoryId.Managed -> "MANAGED:${category.surface.name}"
}

internal fun homeFavoriteCategories(
    prefs: UiPreferences, available: List<CleanupCategoryId>,
): List<CleanupCategoryId> {
    val pinned = available.filter { category -> when (category) {
        is CleanupCategoryId.Entry -> category.kind in prefs.pinnedEntryKinds
        is CleanupCategoryId.Root -> category.kind in prefs.pinnedRootKinds
        is CleanupCategoryId.Managed -> false
    } }
    val byKey = pinned.associateBy(::homeFavoriteKey)
    val sorted = prefs.homePinnedCategoryOrder.mapNotNull(byKey::get).distinct()
    return sorted + pinned.filterNot { it in sorted }
}

/** Update both existing pin stores atomically; preserve out-of-scope pins. */
internal fun updateHomeFavorites(
    prefs: UiPreferences,
    chosen: List<CleanupCategoryId>,
    available: List<CleanupCategoryId>,
): UiPreferences {
    val visibleEntries = available.filterIsInstance<CleanupCategoryId.Entry>().map { it.kind }.toSet()
    val visibleRoots = available.filterIsInstance<CleanupCategoryId.Root>().map { it.kind }.toSet()
    val retainedEntries = prefs.pinnedEntryKinds.filterNot { it in visibleEntries }
    val retainedRoots = prefs.pinnedRootKinds.filterNot { it in visibleRoots }
    val selectedEntries = chosen.filterIsInstance<CleanupCategoryId.Entry>().map { it.kind }
    val selectedRoots = chosen.filterIsInstance<CleanupCategoryId.Root>().map { it.kind }
    val availableKeys = available.map(::homeFavoriteKey).toSet()
    // Other category IDs are not exposed in the chooser, so retain their order.
    val retainedOrder = prefs.homePinnedCategoryOrder.filterNot { it in availableKeys }
    return prefs.copy(
        pinnedEntryKinds = (selectedEntries + retainedEntries).distinct(),
        pinnedRootKinds = (selectedRoots + retainedRoots).distinct(),
        homePinnedCategoryOrder = (chosen.map(::homeFavoriteKey) + retainedOrder).distinct(),
    )
}

/** Keep the original favorites order, then append newly checked categories.
 * Existing pinned categories outside this chooser remain untouched.
 */
internal fun <T> updatePinnedFavorites(
    previous: List<T>, available: List<T>, selected: Set<T>
): List<T> = previous.filter { it !in available || it in selected } +
    available.filter { it in selected && it !in previous }

/** Add/remove multiple favorites without changing cleanup or component rules. */
@Composable
internal fun <T> CategoryPinnedPickerDialog(
    available: List<T>,
    pinned: List<T>,
    label: @Composable (T) -> String,
    onSave: (List<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(available, pinned) {
        mutableStateOf(pinned.filter { it in available }.toSet())
    }
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.lc_home_favorites_choose)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                available.forEach { item ->
                    WorkspaceFlatMenuRow(
                        title = label(item),
                        selected = item in selected,
                        onClick = {
                            selected = if (item in selected) selected - item else selected + item
                        },
                        compactOption = true,
                    )
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = {
                onSave(updatePinnedFavorites(pinned, available, selected))
                onDismiss()
            }) {
                AdaptiveText(stringResource(R.string.lc_mode_confirm_apply))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** Manage on its own surface so long-press dragging does not compete with popup scrolling. */
@Composable
internal fun <T> CategoryPinnedManagerDialog(
    items: List<T>,
    label: @Composable (T) -> String,
    onChange: (List<T>) -> Unit,
    onDismiss: () -> Unit
) {
    val rowPixels = with(LocalDensity.current) { 52.dp.toPx() }
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.category_manage_pins)) },
        text = {
            Column(Modifier.heightIn(max = 384.dp).verticalScroll(rememberScrollState())) {
                AdaptiveText(stringResource(R.string.category_drag_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    var draggedY by remember(item) { mutableFloatStateOf(0f) }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .offset { IntOffset(0, draggedY.roundToInt()) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdaptiveIcon(
                            Icons.Rounded.DragIndicator,
                            stringResource(R.string.category_drag_help),
                            modifier = Modifier.size(44.dp).pointerInput(item, rowPixels) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, delta ->
                                        change.consume()
                                        draggedY += delta.y
                                    },
                                    onDragEnd = {
                                        val steps = (draggedY / rowPixels).roundToInt()
                                        draggedY = 0f
                                        if (steps != 0) onChange(movePinnedCategory(items, item, steps))
                                    },
                                    onDragCancel = { draggedY = 0f }
                                )
                            }
                        )
                        AdaptiveText(label(item), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        AdaptiveIconButton(onClick = { onChange(items - item) }) {
                            AdaptiveIcon(Icons.Rounded.Close, stringResource(R.string.category_unpin))
                        }
                    }
                    AdaptiveHorizontalDivider(thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = { onChange(emptyList()) }) {
                AdaptiveText(stringResource(R.string.category_reset_pins))
            }
        }
    )
}
