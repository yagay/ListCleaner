package com.yagay.ListCleaner.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.IntentKind

/** Entry and Root use the exact same icon and mode-switch interaction.
 * Each screen still owns its separate ordering backend.
 */
@Composable
internal fun WorkspaceSortShortcut(kind: IntentKind?, ordering: Boolean = false, onOpen: () -> Unit) =
    WorkspaceSortModeAction(
        supported = ordering || (kind != null && kind in HomeLayoutPolicy.sortableKinds),
        ordering = ordering,
        onOpen = onOpen,
    )

@Composable
internal fun WorkspaceSortModeAction(
    supported: Boolean,
    ordering: Boolean = false,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    WorkspaceToolbarAction(
        onClick = {
            if (supported) onOpen()
            else Toast.makeText(context, R.string.priority_unsupported_current_list, Toast.LENGTH_SHORT).show()
        },
        active = ordering
    ) {
            val iconTint = when {
                !supported -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ordering -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            AdaptiveIcon(
                Icons.AutoMirrored.Rounded.Sort,
                stringResource(if (ordering) R.string.nav_rules else R.string.nav_priority),
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
    }
}

/**
 * An in-window bottom sheet avoids the system-navigation-bar flash caused by
 * ModalBottomSheet's separate Dialog window. The Cleanup LazyColumn remains
 * composed and keeps its own scroll / expansion / filter / pending selections.
 *
 * Always compose this host, even when visible=false: AnimatedVisibility keeps
 * its children alive until the slide-out completes before disposing the editor.
 */
@Composable
internal fun WorkspaceSortEditorOverlay(
    visible: Boolean,
    guard: PendingNavigationGuard,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    val flingThreshold = with(density) { 640.dp.toPx() }
    BackHandler(enabled = visible && !guard.showPrompt) { guard.request(currentDismiss) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportPx = with(density) { maxHeight.toPx() }
        // One resting height only: show the editor near the top immediately,
        // then allow downward drag to dismiss without an intermediate stop.
        val topInsetPx = sortSheetTopInsetPx(
            viewportPx = viewportPx,
            preferredInsetPx = with(density) { 48.dp.toPx() },
        )
        val dismissDistancePx = with(density) { 72.dp.toPx() }
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        LaunchedEffect(visible) {
            if (visible) {
                dragging = false
                dragOffsetPx = 0f
            }
        }
        val animatedOffsetPx by animateFloatAsState(
            targetValue = dragOffsetPx,
            animationSpec = if (dragging) snap() else tween(250, easing = FastOutSlowInEasing),
            label = "sort-sheet-drag-offset",
        )

        fun moveSheet(delta: Float): Float {
            val previous = dragOffsetPx
            dragOffsetPx = (previous + delta).coerceIn(0f, viewportPx * 0.9f)
            if (dragOffsetPx != previous) dragging = true
            return dragOffsetPx - previous
        }

        fun settleSheet(velocity: Float) {
            val outcome = sortSheetRelease(
                dragOffsetPx = dragOffsetPx,
                dismissDistancePx = dismissDistancePx,
                releaseVelocityPx = velocity,
                flingThresholdPx = flingThreshold,
            )
            dragging = false
            if (outcome == SortSheetRelease.DISMISS) {
                // If there are pending edits, restore the sheet and let the
                // existing Apply / Discard / Stay confirmation handle dismissal.
                if (guard.hasChanges) dragOffsetPx = 0f
                guard.request(currentDismiss)
            } else {
                // Insufficient travel: animate back to the sole open anchor.
                dragOffsetPx = 0f
            }
        }

        // List scrolling stays independent. At the top of the list, downward
        // overscroll drags the sheet; upward input returns a dragged sheet to
        // its only open position before scrolling the list again.
        val scrollConnection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || dragOffsetPx <= 0f) return Offset.Zero
                return Offset(0f, moveSheet(available.y))
            }

            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                return Offset(0f, moveSheet(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!dragging) return Velocity.Zero
                settleSheet(available.y)
                return Velocity(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!dragging) return Velocity.Zero
                settleSheet(available.y)
                return Velocity(0f, available.y)
            }
        }
        val handleState = rememberDraggableState { delta -> moveSheet(delta) }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(240)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { guard.request(currentDismiss) },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                // Resize the panel from its TOP while keeping the bottom
                // anchored to the page edge. Translating a full-height panel
                // down hid part of its editor beneath the bottom navigation.
                modifier = Modifier.fillMaxWidth()
                    .height(with(density) {
                        (viewportPx - topInsetPx - animatedOffsetPx).coerceAtLeast(0f).toDp()
                    })
                    .nestedScroll(scrollConnection),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column {
                    // Keep a compact dedicated drag area. The previous 40dp
                    // row plus the Material3 DragHandle's own padding looked
                    // like an empty bar above both ordering lists.
                    Box(
                        Modifier.fillMaxWidth().height(24.dp).draggable(
                            state = handleState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { dragging = true },
                            onDragStopped = { velocity -> settleSheet(velocity) },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.width(42.dp).height(5.dp)
                                .background(
                                    // Strong surface contrast in both light and dark themes.
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    RoundedCornerShape(50),
                                )
                        )
                    }
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
        PendingNavigationDialog(guard)
    }
}
