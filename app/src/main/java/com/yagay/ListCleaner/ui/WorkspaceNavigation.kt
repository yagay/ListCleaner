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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val animatedDrag by animateFloatAsState(
        targetValue = dragDistance,
        animationSpec = if (dragging) snap() else spring(stiffness = Spring.StiffnessMedium),
        label = "sort-sheet-handle-drag",
    )
    val handleDrag = rememberDraggableState { amount ->
        dragDistance = (dragDistance + amount).coerceAtLeast(0f)
    }
    BackHandler(enabled = visible && !guard.showPrompt) { guard.request(currentDismiss) }

    Box(Modifier.fillMaxSize()) {
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
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .offset { IntOffset(0, animatedDrag.roundToInt()) },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column {
                    Box(
                        Modifier.fillMaxWidth().height(40.dp).draggable(
                            state = handleDrag,
                            orientation = Orientation.Vertical,
                            onDragStarted = { dragging = true },
                            onDragStopped = { velocity ->
                                val close = dragDistance > thresholdPx || velocity > 1100f
                                dragging = false
                                dragDistance = 0f
                                if (close) guard.request(currentDismiss)
                            },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
        // Confirmation remains above the sheet without tearing down the editor.
        PendingNavigationDialog(guard)
    }
}
