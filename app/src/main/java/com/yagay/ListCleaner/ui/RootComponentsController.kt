package com.yagay.ListCleaner.ui

import android.util.Log
import android.os.SystemClock
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.runtime.ComponentReconcileReceiver
import com.yagay.ListCleaner.data.ComponentPersistenceResult
import com.yagay.ListCleaner.data.ComponentRootCommand
import com.yagay.ListCleaner.data.PersistentComponentStore
import com.yagay.ListCleaner.data.PersistentComponentState
import android.content.pm.PackageManager
import com.yagay.ListCleaner.runtime.ComponentReconcileJobService
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.data.RootComponentCatalog
import com.yagay.ListCleaner.data.ProfileComponentCatalog
import com.yagay.ListCleaner.data.RootComponentScan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class RootBatchProgress(val completed: Int, val total: Int)

/** Owns Root component scanning/mutation state so MainViewModel can focus on resolver rules and module state. */
internal class RootComponentsController(
    private val app: ListCleanerApp,
    private val scope: CoroutineScope
) {
    private class RootPolicyPersistenceException : IllegalStateException()

    private val catalog = RootComponentCatalog(app) { app.runtime.value.componentDiscoveryProtocol }
    private val profileCatalog = ProfileComponentCatalog(app)
    @Volatile private var targetUserId = app.spaces.currentUserId

    fun switchSpace(userId: Int) {
        if (targetUserId == userId) return
        check(!mutableBusy.value) { "Cannot switch spaces while Root components are busy" }
        targetUserId = userId
        mutablePaused.value = persistentComponents.isPaused(userId)
        lastFullScanElapsed = -1L
        lastAutoScanAttemptElapsed = -1L
        lastScannedPackageRevision = -1L
        refreshPending = false
        mutableMessage.value = null
        mutableScan.value = RootComponentScan(warning = app.getString(R.string.root_not_scanned))
    }
    private val persistentComponents = PersistentComponentStore(app)
    private val mutablePaused = MutableStateFlow(persistentComponents.isPaused(targetUserId))
    val paused: StateFlow<Boolean> = mutablePaused.asStateFlow()

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
    private var lastAutoScanAttemptElapsed = -1L
    private var lastFullScanElapsed = -1L
    private var lastScannedPackageRevision = -1L
    private val cachePrefs = app.getSharedPreferences(
        ComponentReconcileReceiver.COMPONENT_CACHE_PREFS,
        android.content.Context.MODE_PRIVATE
    )

    private fun packageChangeRevision(): Long =
        cachePrefs.getLong(ComponentReconcileReceiver.COMPONENT_CACHE_REVISION, 0L)
    private var mutationJob: Job? = null
    private val mutableBatchProgress = MutableStateFlow<RootBatchProgress?>(null)
    val batchProgress: StateFlow<RootBatchProgress?> = mutableBatchProgress.asStateFlow()

    fun cancelBatch() { mutationJob?.cancel() }

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

    /**
     * Component enumeration is expensive. Opening the Cleanup homepage never triggers it:
     * lazily scan on the first component editor visit, then reuse results for ten minutes.
     * Failed attempts have a short retry cooldown; the explicit refresh action bypasses both.
     */
    fun refreshIfStale() {
        val now = SystemClock.elapsedRealtime()
        if (!ComponentScanRefreshPolicy.shouldRefresh(
                nowElapsedMs = now,
                lastFullScanElapsedMs = lastFullScanElapsed,
                lastAttemptElapsedMs = lastAutoScanAttemptElapsed,
                busy = mutableBusy.value,
                packageRevisionChanged = packageChangeRevision() != lastScannedPackageRevision,
                scanMode = UiPreferencesRuntime.current.scanMode,
                freshnessMs = UiPreferencesRuntime.current.scanCacheMinutes * 60_000L,
                retryAfterFailureMs = UiPreferencesRuntime.current.scanRetrySeconds * 1000L
            )) return
        refresh()
    }

    fun refresh() {
        if (mutableBusy.value) {
            refreshPending = true
            return
        }
        mutableBusy.value = true
        lastAutoScanAttemptElapsed = SystemClock.elapsedRealtime()
        mutableMessage.value = null
        scope.launch {
            try {
                val packageRevisionBefore = packageChangeRevision()
                val protocolBefore = app.runtime.value.componentDiscoveryProtocol
                mutableScan.value = withContext(Dispatchers.IO) {
                    if (targetUserId == app.spaces.currentUserId) catalog.scan()
                    else profileCatalog.scan(targetUserId)
                }
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
                    mutableScan.value = withContext(Dispatchers.IO) {
                    if (targetUserId == app.spaces.currentUserId) catalog.scan()
                    else profileCatalog.scan(targetUserId)
                }
                }
                // A partial scan must retry after the short cooldown rather than appearing fresh.
                lastFullScanElapsed = ComponentScanRefreshPolicy.completedFullScanAt(
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                    warning = mutableScan.value.warning,
                    packageRevisionChanged = packageRevisionBefore != packageChangeRevision()
                )
                if (lastFullScanElapsed >= 0L) {
                    lastScannedPackageRevision = packageRevisionBefore
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Root component scan failed", failure)
                // A failed explicit scan cannot leave a previous success marked fresh.
                lastFullScanElapsed = -1L
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


    /**
     * Transition only verified ListCleaner-managed component identities.
     * The saved disabled set remains untouched while the global mode is paused.
     */
    fun setPaused(paused: Boolean) {
        if (mutableBusy.value) return
        val userId = targetUserId
        mutableBusy.value = true
        mutableRootNotice.value = null
        mutableMessage.value = app.getString(R.string.root_mode_processing)
        val operation = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val full = if (userId == app.spaces.currentUserId) catalog.scan()
                        else profileCatalog.scan(userId)
                    check(full.warning.isBlank()) { app.getString(R.string.root_scan_failed) }
                    val managed = persistentComponents.disabledKeys().filterTo(linkedSetOf()) {
                        PersistentComponentState.parse(it)?.user == userId
                    }
                    val found = full.items.associateBy {
                        PersistentComponentState.key(it.user, it.component)
                    }
                    val targets = managed.mapNotNull { found[it] }.distinctBy { it.id }
                        .filter { it.blocked == null && it.enabled != null &&
                            (if (paused) it.overrideState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                             else it.overrideState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
                    if (!persistentComponents.setPaused(userId, paused)) {
                        error(app.getString(R.string.root_persistence_failed))
                    }
                    mutablePaused.value = paused
                    // Publish the current guard mode before modifying Android's state.
                    persistentComponents.syncRemote()
                    app.synchronize()
                    var completed = 0
                    var failures = 0
                    targets.forEach { target ->
                        try {
                            if (userId == app.spaces.currentUserId) {
                                catalog.change(target, enable = paused)
                            } else {
                                profileCatalog.change(target, enable = paused)
                            }
                            completed++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            failures++
                            Log.w(TAG, "Managed Root mode component transition failed", failure)
                        }
                    }
                    mutableMessage.value = app.getString(
                        if (failures == 0) R.string.root_mode_completed else R.string.root_mode_partial,
                        completed, failures
                    )
                    if (failures > 0 && !paused) {
                        ComponentReconcileJobService.schedule(app, "root_mode_retry")
                    }
                    mutableScan.value = if (userId == app.spaces.currentUserId) catalog.scan()
                        else profileCatalog.scan(userId)
                    lastFullScanElapsed = -1L
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ComponentRootCommand.RootAccessException) {
                mutableMessage.value = rootAccessMessage(failure)
                mutableRootNotice.value = mutableMessage.value
            } catch (failure: Exception) {
                Log.e(TAG, "Root global mode transition failed", failure)
                mutableMessage.value = app.getString(R.string.root_mode_failed)
            } finally {
                mutableBusy.value = false
                mutationJob = null
                if (refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
        mutationJob = operation
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
            progressMessage = { _, _, target ->
                app.getString(
                    if (enable) R.string.root_progress_enable_plain else R.string.root_progress_disable_plain,
                    target.label
                )
            },
            successMessage = { _, lastResult ->
                if (targets.distinctRootTargets().size == 1) lastResult else app.getString(
                    if (enable) R.string.root_batch_enabled_plain else R.string.root_batch_disabled_plain
                )
            },
            stoppedMessage = { _, _, reason ->
                app.getString(R.string.root_batch_stopped_plain, reason)
            },
            operationName = "mutation",
        )
    }

    /** One verified Root operation for mixed enable/disable requests; never race two batches. */
    fun changeDesired(desired: Map<RootComponent, Boolean>) {
        val eligible = desired.filter { (target, enable) ->
            target.blocked == null && target.enabled != null && target.enabled != enable
        }
        mutateBatch(
            visibleTargets = eligible.keys.toList(),
            initialMessage = app.getString(R.string.root_requesting_verify),
            targetEnable = { target -> eligible.getValue(target) },
            progressMessage = { _, _, target ->
                app.getString(
                    if (eligible.getValue(target)) R.string.root_progress_enable_plain
                    else R.string.root_progress_disable_plain,
                    target.label
                )
            },
            successMessage = { _, lastResult -> lastResult },
            stoppedMessage = { _, _, reason ->
                app.getString(R.string.root_batch_stopped_plain, reason)
            },
            operationName = "mixed-mutation",
        )
    }

    fun invert(visibleTargets: List<RootComponent>) {
        mutateBatch(
            visibleTargets = visibleTargets.filter { it.blocked == null && it.enabled != null },
            initialMessage = app.getString(R.string.root_requesting_invert),
            targetEnable = { it.enabled == false },
            progressMessage = { _, _, target ->
                app.getString(R.string.root_progress_invert_plain, target.label)
            },
            successMessage = { _, _ -> app.getString(R.string.root_batch_inverted_plain) },
            stoppedMessage = { _, _, reason ->
                app.getString(R.string.root_invert_stopped_plain, reason)
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
        if (mutableBusy.value || persistentComponents.isPaused(targetUserId)) return
        val targets = visibleTargets.distinctRootTargets()
        if (targets.isEmpty() || targets.any { it.user != targetUserId }) return
        mutableRootNotice.value = null
        mutableBusy.value = true
        mutableMessage.value = initialMessage
        mutableBatchProgress.value = RootBatchProgress(0, targets.size)
        val operation = scope.launch {
            withContext(Dispatchers.IO) {
                val completedTargets = mutableListOf<RootComponent>()
                // Also refresh a target when the root command succeeds but saving its state fails.
                val attemptedTargets = mutableListOf<RootComponent>()
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
                        // A single component change and its verified local persistence are atomic
                        // with respect to cancelling the remaining batch.
                        withContext(NonCancellable) {
                            attemptedTargets += target
                            lastResult = if (target.user == app.spaces.currentUserId) {
                                catalog.change(target, enable)
                            } else {
                                profileCatalog.change(target, enable)
                            }
                            persistConfirmedState(target, disabled = !enable)
                            completedTargets += target
                            mutableBatchProgress.value = RootBatchProgress(completedTargets.size, targets.size)
                        }
                    }
                    mutableMessage.value = successMessage(completedTargets.size, lastResult)
                } catch (cancelled: CancellationException) {
                    Log.i(TAG, "Root component $operationName cancelled after ${completedTargets.size}/${targets.size}")
                    mutableMessage.value = app.getString(R.string.root_batch_cancelled_plain)
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
                    try {
                        if (attemptedTargets.isNotEmpty()) {
                            withContext(NonCancellable) {
                                // Keep the UI responsive even when remote sync fails.
                                try {
                                    app.synchronize()
                                } catch (failure: Exception) {
                                    Log.w(TAG, "Post-mutation runtime sync failed", failure)
                                }
                                refreshAfterMutation(attemptedTargets)
                            }
                        }
                    } finally {
                        mutableBusy.value = false
                        mutableBatchProgress.value = null
                        mutationJob = null
                        if (refreshPending) {
                            refreshPending = false
                            refresh()
                        }
                    }
                }
            }
        }
        mutationJob = operation
        // The coroutine may be cancelled before it enters Dispatchers.IO and its inner
        // finally executes; completion still has to release the UI's busy flag.
        operation.invokeOnCompletion {
            if (mutationJob === operation) {
                mutationJob = null
                mutableBusy.value = false
                mutableBatchProgress.value = null
                if (refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
    }

    private fun refreshAfterMutation(targets: List<RootComponent>) {
        runCatching {
            if (targetUserId == app.spaces.currentUserId) {
                when (UiPreferencesRuntime.current.componentRefresh) {
                    UiComponentRefresh.CHANGED_ONLY -> catalog.refreshItems(mutableScan.value, targets)
                    UiComponentRefresh.FULL -> catalog.scan()
                }
            } else profileCatalog.scan(targetUserId)
        }
            .onSuccess { updated ->
                mutableScan.value = updated
                if (updated.warning.isNotBlank()) lastFullScanElapsed = -1L
            }
            .onFailure { failure ->
                Log.e(TAG, "Post-mutation Root component refresh failed", failure)
                lastFullScanElapsed = -1L
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
