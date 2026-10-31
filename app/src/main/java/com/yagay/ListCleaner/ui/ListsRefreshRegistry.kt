package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * One toolbar refresh for Lists. The active editor provides the operation;
 * no editor renders a second refresh control. Unmounting a previous editor
 * never clears a newer editor's callback.
 */
internal class ListsRefreshRegistry {
    private var owner: Any? = null
    private var action: (() -> Unit)? = null
    var available by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(false)
        private set

    fun register(owner: Any, enabled: Boolean, refresh: () -> Unit) {
        this.owner = owner
        action = refresh
        available = true
        this.enabled = enabled
    }

    fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        action = null
        available = false
        enabled = false
    }

    fun refresh() {
        if (enabled) action?.invoke()
    }
}

/** The operation can change during composition without leaving a stale callback. */
@Composable
internal fun RegisterListsRefresh(
    registry: ListsRefreshRegistry?,
    enabled: Boolean = true,
    onRefresh: () -> Unit,
) {
    val owner = remember { Any() }
    val currentAction = rememberUpdatedState(onRefresh)
    val invokeRefresh: () -> Unit = remember { { currentAction.value.invoke() } }
    SideEffect { registry?.register(owner, enabled, invokeRefresh) }
    DisposableEffect(registry, owner) {
        onDispose { registry?.release(owner) }
    }
}
