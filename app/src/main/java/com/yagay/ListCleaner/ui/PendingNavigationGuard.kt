package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R

/**
 * Guards navigation away from an unsaved editor (including spaces and bottom tabs).
 * In-flight Root mutation completes before the deferred navigation is allowed.
 */
class PendingNavigationGuard {
    private var owner: Any? = null
    private var pendingCount by mutableIntStateOf(0)
    val hasChanges: Boolean get() = pendingCount > 0
    private var apply: (() -> Boolean)? = null
    private var discard: (() -> Unit)? = null
    private var rootEditor = false
    private var navigation: (() -> Unit)? = null
    var showPrompt by mutableStateOf(false)
        private set
    private var awaitingRoot = false
    private var observedRootBusy = false

    fun register(
        owner: Any, count: Int, apply: () -> Boolean, discard: () -> Unit,
        rootEditor: Boolean = false
    ) {
        this.owner = owner
        pendingCount = count
        this.apply = apply
        this.discard = discard
        this.rootEditor = rootEditor
    }

    fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        pendingCount = 0
        apply = null
        discard = null
        rootEditor = false
    }

    fun request(next: () -> Unit) {
        if (awaitingRoot) return
        if (pendingCount > 0) {
            navigation = next
            showPrompt = true
        } else next()
    }

    fun stay() {
        navigation = null
        showPrompt = false
    }

    fun discardAndLeave() {
        val next = navigation ?: return stay()
        discard?.invoke()
        stay()
        next()
    }

    fun applyAndLeave() {
        val next = navigation ?: return stay()
        val started = apply?.invoke() ?: false
        if (!started) return stay()
        showPrompt = false
        if (rootEditor) {
            // Root's busy flow can update on the next frame; never switch spaces mid-batch.
            awaitingRoot = true
            observedRootBusy = false
        } else {
            stay()
            next()
        }
    }

    fun observeRootBusy(busy: Boolean) {
        if (!awaitingRoot) return
        if (busy) observedRootBusy = true
        if (!busy && observedRootBusy) {
            awaitingRoot = false
            observedRootBusy = false
            val next = navigation
            stay()
            next?.invoke()
        }
    }
}

@Composable
internal fun RegisterPendingNavigation(
    guard: PendingNavigationGuard?,
    owner: Any,
    count: Int,
    apply: () -> Boolean,
    discard: () -> Unit,
    rootEditor: Boolean = false
) {
    SideEffect { guard?.register(owner, count, apply, discard, rootEditor) }
    DisposableEffect(guard, owner) {
        onDispose { guard?.release(owner) }
    }
}

@Composable
internal fun PendingNavigationDialog(guard: PendingNavigationGuard) {
    if (!guard.showPrompt) return
    AdaptiveAlertDialog(
        onDismissRequest = guard::stay,
        title = { AdaptiveText(stringResource(R.string.lc_pending_leave_title)) },
        text = { AdaptiveText(stringResource(R.string.lc_pending_leave_body)) },
        confirmButton = {
            AdaptiveTextButton(onClick = guard::applyAndLeave) {
                AdaptiveText(stringResource(R.string.lc_pending_leave_apply))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = guard::discardAndLeave) {
                AdaptiveText(stringResource(R.string.lc_pending_leave_discard))
            }
            AdaptiveTextButton(onClick = guard::stay) {
                AdaptiveText(stringResource(R.string.lc_pending_leave_stay))
            }
        }
    )
}
