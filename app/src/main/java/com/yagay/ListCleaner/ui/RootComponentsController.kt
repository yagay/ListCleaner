package com.yagay.ListCleaner.ui

import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.ComponentRootCommand
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.data.RootComponentCatalog
import com.yagay.ListCleaner.data.RootComponentScan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns Root component scanning/mutation state so MainViewModel can focus on resolver rules and module state. */
internal class RootComponentsController(
    private val app: ListCleanerApp,
    private val scope: CoroutineScope
) {
    private val catalog = RootComponentCatalog(app)

    private val mutableScan = MutableStateFlow(
        RootComponentScan(warning = app.getString(R.string.root_not_scanned))
    )
    val scan: StateFlow<RootComponentScan> = mutableScan

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage

    private val mutableRootNotice = MutableStateFlow<String?>(null)
    val rootNotice: StateFlow<String?> = mutableRootNotice

    val lastOperation: String get() = catalog.lastOperation

    fun dismissRootNotice() { mutableRootNotice.value = null }

    private fun rootAccessMessage(failure: ComponentRootCommand.RootAccessException): String {
        val guidance = app.getString(R.string.root_guidance)
        return app.getString(
            when (failure.reason) {
                ComponentRootCommand.RootFailureReason.UNAVAILABLE -> R.string.root_access_unavailable
                ComponentRootCommand.RootFailureReason.TIMEOUT -> R.string.root_access_timeout
                ComponentRootCommand.RootFailureReason.DENIED -> R.string.root_access_denied
            },
            guidance
        )
    }

    fun refresh() {
        if (mutableBusy.value) return
        mutableBusy.value = true
        scope.launch {
            try {
                mutableScan.value = withContext(Dispatchers.IO) { catalog.scan() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Root component scan failed", failure)
                mutableMessage.value = app.getString(R.string.root_scan_failed)
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun change(target: RootComponent, enable: Boolean) = change(listOf(target), enable)

    fun change(visibleTargets: List<RootComponent>, enable: Boolean) {
        if (mutableBusy.value) return
        val targets = visibleTargets.filter {
            it.blocked == null && it.enabled != null && it.enabled != enable
        }.distinctBy { "${it.user}|${it.component.flattenToString()}" }
        if (targets.isEmpty()) return
        mutableRootNotice.value = null
        mutableBusy.value = true
        mutableMessage.value = app.getString(R.string.root_requesting_verify)
        scope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                var completed = 0
                var operationStarted = false
                try {
                    catalog.requireRoot()
                    var result = ""
                    for (target in targets) {
                        operationStarted = true
                        mutableMessage.value = app.getString(
                            if (enable) R.string.root_progress_enable else R.string.root_progress_disable,
                            completed + 1,
                            targets.size,
                            target.label
                        )
                        result = catalog.change(target, enable)
                        completed++
                    }
                    mutableMessage.value = if (targets.size == 1) {
                        result
                    } else {
                        app.getString(
                            if (enable) R.string.root_batch_enabled else R.string.root_batch_disabled,
                            completed
                        )
                    }
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    val message = rootAccessMessage(failure)
                    mutableMessage.value = message
                    mutableRootNotice.value = message
                } catch (failure: Exception) {
                    Log.e(TAG, "Root component mutation failed after $completed/${targets.size}", failure)
                    mutableMessage.value = app.getString(
                        R.string.root_batch_stopped,
                        completed,
                        targets.size,
                        app.getString(R.string.root_operation_not_allowed)
                    )
                } finally {
                    if (operationStarted) refreshAfterMutation()
                    mutableBusy.value = false
                }
            }
        }
    }

    fun invert(visibleTargets: List<RootComponent>) {
        if (mutableBusy.value) return
        val targets = visibleTargets.filter {
            it.blocked == null && it.enabled != null
        }.distinctBy { "${it.user}|${it.component.flattenToString()}" }
        if (targets.isEmpty()) return
        mutableRootNotice.value = null
        mutableBusy.value = true
        mutableMessage.value = app.getString(R.string.root_requesting_invert)
        scope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                var completed = 0
                var operationStarted = false
                try {
                    catalog.requireRoot()
                    for (target in targets) {
                        operationStarted = true
                        mutableMessage.value = app.getString(
                            R.string.root_progress_invert,
                            completed + 1,
                            targets.size,
                            target.label
                        )
                        catalog.change(target, target.enabled == false)
                        completed++
                    }
                    mutableMessage.value = app.getString(R.string.root_batch_inverted, completed)
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    val message = rootAccessMessage(failure)
                    mutableMessage.value = message
                    mutableRootNotice.value = message
                } catch (failure: Exception) {
                    Log.e(TAG, "Root component inversion failed after $completed/${targets.size}", failure)
                    mutableMessage.value = app.getString(
                        R.string.root_invert_stopped,
                        completed,
                        targets.size,
                        app.getString(R.string.root_operation_not_allowed)
                    )
                } finally {
                    if (operationStarted) refreshAfterMutation()
                    mutableBusy.value = false
                }
            }
        }
    }

    private fun refreshAfterMutation() {
        runCatching { catalog.scan() }
            .onSuccess { mutableScan.value = it }
            .onFailure { failure ->
                Log.e(TAG, "Post-mutation Root component scan failed", failure)
                mutableScan.value = RootComponentScan(
                    warning = app.getString(R.string.root_post_scan_failed)
                )
            }
    }

    private companion object {
        const val TAG = "ListCleaner.Root"
    }
}
