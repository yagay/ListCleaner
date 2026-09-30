package com.yagay.ListCleaner.ui

import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.ComponentPersistenceResult
import com.yagay.ListCleaner.data.ComponentRootCommand
import com.yagay.ListCleaner.data.PersistentComponentStore
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Owns Root component scanning/mutation state so MainViewModel can focus on resolver rules and module state. */
internal class RootComponentsController(
    private val app: ListCleanerApp,
    private val scope: CoroutineScope
) {
    private class RootPolicyPersistenceException : IllegalStateException()

    private val catalog = RootComponentCatalog(app)
    private val persistentComponents = PersistentComponentStore(app)

    private val mutableScan = MutableStateFlow(
        RootComponentScan(warning = app.getString(R.string.root_not_scanned))
    )
    val scan: StateFlow<RootComponentScan> = mutableScan.asStateFlow()

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    private val mutableRootNotice = MutableStateFlow<String?>(null)
    val rootNotice: StateFlow<String?> = mutableRootNotice.asStateFlow()
    private var refreshPending = false

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

    private fun persistConfirmedState(target: RootComponent, disabled: Boolean) {
        when (persistentComponents.setDisabled(target, disabled)) {
            ComponentPersistenceResult.LOCAL_FAILED -> throw RootPolicyPersistenceException()
            ComponentPersistenceResult.LOCAL_SAVED ->
                Log.w(TAG, "Component policy saved locally but remote mirror is pending for ${target.id}")
            ComponentPersistenceResult.FULLY_SYNCED -> Unit
        }
    }

    private fun mutationFailureText(failure: Exception): String =
        if (failure is RootPolicyPersistenceException) {
            app.getString(R.string.root_persistence_failed)
        } else {
            app.getString(R.string.root_operation_not_allowed)
        }

    fun refresh() {
        if (mutableBusy.value) {
            refreshPending = true
            return
        }
        mutableBusy.value = true
        scope.launch {
            try {
                val protocolBefore = app.runtime.value.componentDiscoveryProtocol
                mutableScan.value = withContext(Dispatchers.IO) { catalog.scan() }
                try {
                    withContext(Dispatchers.IO) {
                        persistentComponents.syncRemote()
                        app.synchronize()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w(TAG, "Root runtime synchronization failed after local scan", failure)
                }
                val protocolAfter = app.runtime.value.componentDiscoveryProtocol
                if (protocolAfter != protocolBefore) {
                    mutableScan.value = withContext(Dispatchers.IO) { catalog.scan() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Root component scan failed", failure)
                mutableMessage.value = app.getString(R.string.root_scan_failed)
            } finally {
                mutableBusy.value = false
                if (refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
    }

    fun change(target: RootComponent, enable: Boolean) = change(listOf(target), enable)

    fun change(visibleTargets: List<RootComponent>, enable: Boolean) {
        val targets = visibleTargets.filter {
            it.blocked == null && it.enabled != null && it.enabled != enable
        }
        mutateBatch(
            visibleTargets = targets,
            initialMessage = app.getString(R.string.root_requesting_verify),
            targetEnable = { enable },
            progressMessage = { index, total, target ->
                app.getString(
                    if (enable) R.string.root_progress_enable else R.string.root_progress_disable,
                    index,
                    total,
                    target.label
                )
            },
            successMessage = { completed, lastResult ->
                if (targets.distinctRootTargets().size == 1) lastResult else app.getString(
                    if (enable) R.string.root_batch_enabled else R.string.root_batch_disabled,
                    completed
                )
            },
            stoppedMessage = { completed, total, reason ->
                app.getString(R.string.root_batch_stopped, completed, total, reason)
            },
            operationName = "mutation",
        )
    }

    fun invert(visibleTargets: List<RootComponent>) {
        mutateBatch(
            visibleTargets = visibleTargets.filter { it.blocked == null && it.enabled != null },
            initialMessage = app.getString(R.string.root_requesting_invert),
            targetEnable = { it.enabled == false },
            progressMessage = { index, total, target ->
                app.getString(R.string.root_progress_invert, index, total, target.label)
            },
            successMessage = { completed, _ -> app.getString(R.string.root_batch_inverted, completed) },
            stoppedMessage = { completed, total, reason ->
                app.getString(R.string.root_invert_stopped, completed, total, reason)
            },
            operationName = "inversion",
        )
    }

    private fun List<RootComponent>.distinctRootTargets(): List<RootComponent> =
        distinctBy { "${it.user}|${it.component.flattenToString()}" }

    private fun mutateBatch(
        visibleTargets: List<RootComponent>,
        initialMessage: String,
        targetEnable: (RootComponent) -> Boolean,
        progressMessage: (index: Int, total: Int, target: RootComponent) -> String,
        successMessage: (completed: Int, lastResult: String) -> String,
        stoppedMessage: (completed: Int, total: Int, reason: String) -> String,
        operationName: String,
    ) {
        if (mutableBusy.value) return
        val targets = visibleTargets.distinctRootTargets()
        if (targets.isEmpty()) return
        mutableRootNotice.value = null
        mutableBusy.value = true
        mutableMessage.value = initialMessage
        scope.launch {
            withContext(Dispatchers.IO) {
                val completedTargets = mutableListOf<RootComponent>()
                var lastResult = ""
                try {
                    catalog.requireRoot()
                    for (target in targets) {
                        coroutineContext.ensureActive()
                        mutableMessage.value = progressMessage(
                            completedTargets.size + 1,
                            targets.size,
                            target,
                        )
                        val enable = targetEnable(target)
                        lastResult = withContext(NonCancellable) { catalog.change(target, enable) }
                        persistConfirmedState(target, disabled = !enable)
                        completedTargets += target
                    }
                    mutableMessage.value = successMessage(completedTargets.size, lastResult)
                } catch (cancelled: CancellationException) {
                    Log.i(TAG, "Root component $operationName cancelled after ${completedTargets.size}/${targets.size}")
                    throw cancelled
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    val message = rootAccessMessage(failure)
                    mutableMessage.value = message
                    mutableRootNotice.value = message
                } catch (failure: Exception) {
                    Log.e(TAG, "Root component $operationName failed after ${completedTargets.size}/${targets.size}", failure)
                    mutableMessage.value = stoppedMessage(
                        completedTargets.size,
                        targets.size,
                        mutationFailureText(failure),
                    )
                } finally {
                    if (completedTargets.isNotEmpty()) {
                        withContext(NonCancellable) { app.synchronize() }
                        refreshAfterMutation(completedTargets)
                    }
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
