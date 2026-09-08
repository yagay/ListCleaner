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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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
            withContext(Dispatchers.IO) {
                val completedTargets = mutableListOf<RootComponent>()
                try {
                    catalog.requireRoot()
                    var result = ""
                    for (target in targets) {
                        coroutineContext.ensureActive()
                        mutableMessage.value = app.getString(
                            if (enable) R.string.root_progress_enable else R.string.root_progress_disable,
                            completedTargets.size + 1,
                            targets.size,
                            target.label
                        )
                        result = withContext(NonCancellable) { catalog.change(target, enable) }
                        completedTargets += target
                    }
                    mutableMessage.value = if (targets.size == 1) {
                        result
                    } else {
                        app.getString(
                            if (enable) R.string.root_batch_enabled else R.string.root_batch_disabled,
                            completedTargets.size
                        )
                    }
                } catch (cancelled: CancellationException) {
                    Log.i(TAG, "Root component batch cancelled after ${completedTargets.size}/${targets.size}")
                    throw cancelled
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    val message = rootAccessMessage(failure)
                    mutableMessage.value = message
                    mutableRootNotice.value = message
                } catch (failure: Exception) {
                    Log.e(TAG, "Root component mutation failed after ${completedTargets.size}/${targets.size}", failure)
                    mutableMessage.value = app.getString(
                        R.string.root_batch_stopped,
                        completedTargets.size,
                        targets.size,
                        app.getString(R.string.root_operation_not_allowed)
                    )
                } finally {
                    if (completedTargets.isNotEmpty()) refreshAfterMutation(completedTargets)
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
            withContext(Dispatchers.IO) {
                val completedTargets = mutableListOf<RootComponent>()
                try {
                    catalog.requireRoot()
                    for (target in targets) {
                        coroutineContext.ensureActive()
                        mutableMessage.value = app.getString(
                            R.string.root_progress_invert,
                            completedTargets.size + 1,
                            targets.size,
                            target.label
                        )
                        withContext(NonCancellable) { catalog.change(target, target.enabled == false) }
                        completedTargets += target
                    }
                    mutableMessage.value = app.getString(R.string.root_batch_inverted, completedTargets.size)
                } catch (cancelled: CancellationException) {
                    Log.i(TAG, "Root component inversion cancelled after ${completedTargets.size}/${targets.size}")
                    throw cancelled
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    val message = rootAccessMessage(failure)
                    mutableMessage.value = message
                    mutableRootNotice.value = message
                } catch (failure: Exception) {
                    Log.e(TAG, "Root component inversion failed after ${completedTargets.size}/${targets.size}", failure)
                    mutableMessage.value = app.getString(
                        R.string.root_invert_stopped,
                        completedTargets.size,
                        targets.size,
                        app.getString(R.string.root_operation_not_allowed)
                    )
                } finally {
                    if (completedTargets.isNotEmpty()) refreshAfterMutation(completedTargets)
                    mutableBusy.value = false
                }
            }
        }
    }

    private fun refreshAfterMutation(targets: List<RootComponent>) {
        runCatching { catalog.refreshItems(mutableScan.value, targets) }
            .onSuccess { mutableScan.value = it }
            .onFailure { failure ->
                Log.e(TAG, "Post-mutation Root component refresh failed", failure)
                mutableScan.value = mutableScan.value.copy(
                    warning = app.getString(R.string.root_post_scan_failed),
                    observedAt = System.currentTimeMillis()
                )
            }
    }

    private companion object {
        const val TAG = "ListCleaner.Root"
    }
}
