package com.yagay.ListCleaner.ui

import android.util.Log
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.ResolverScopeDetector
import com.yagay.ListCleaner.data.ScopeDetection
import com.yagay.ListCleaner.domain.RuntimeProtocol
import com.yagay.ListCleaner.runtime.ServiceSession
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RunningTargetStatus(val processName: String, val state: String, val version: Long)

data class ModuleStatus(
    val connected: Boolean = false,
    val apiVersion: Int? = null,
    val grantedScope: Set<String> = emptySet(),
    val runningTargets: List<RunningTargetStatus> = emptyList(),
    val detection: ScopeDetection = ScopeDetection(),
    val scopeKnown: Boolean = false,
    val requesting: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val missingScope: Set<String> get() = detection.recommended - grantedScope
    val extraScope: Set<String> get() = grantedScope - detection.hosts.map { it.packageName }.toSet()
    val resolverLoaded: Boolean get() = runningTargets.any { target ->
        RuntimeProtocol.current(target.state, target.version, BuildConfig.VERSION_CODE.toLong()) &&
            detection.hosts.any { host -> host.packageName != "system" && host.processName == target.processName }
    }
    val outdated: Boolean get() = runningTargets.any {
        !RuntimeProtocol.current(it.state, it.version, BuildConfig.VERSION_CODE.toLong())
    }
}

/** Owns LSPosed service/session state, scope requests and hot reload operations. */
class ModuleRuntimeController(
    private val app: ListCleanerApp,
    private val scope: CoroutineScope
) {
    private val scopeDetector = ResolverScopeDetector(app)
    private var statusGeneration = 0L
    private var updateGeneration = 0L
    private var scopeGeneration = 0L
    private var scopeRequestInFlight = false

    @Volatile
    private var cachedDetection: ScopeDetection? = null

    private val mutableStatus = MutableStateFlow(ModuleStatus())
    val status: StateFlow<ModuleStatus> = mutableStatus

    private val mutableUpdating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = mutableUpdating

    private val mutableUpdateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = mutableUpdateMessage

    suspend fun readStatus(
        session: ServiceSession? = app.currentSession(),
        refreshDetection: Boolean = false
    ): ModuleStatus {
        val generation = ++statusGeneration
        val status = withContext(Dispatchers.IO) {
            val detection = if (refreshDetection || cachedDetection == null) {
                scopeDetector.detect().also { cachedDetection = it }
            } else requireNotNull(cachedDetection)
            var result = ModuleStatus(connected = session != null, detection = detection)
            val service = session?.service
            if (service != null) {
                try {
                    result = result.copy(
                        apiVersion = service.apiVersion,
                        grantedScope = service.scope.toSet(),
                        scopeKnown = true
                    )
                    result = if ((result.apiVersion ?: 0) >= 102) {
                        result.copy(runningTargets = service.runningTargets.map {
                            RunningTargetStatus(it.processName, it.state.name, it.loadedVersionCode)
                        })
                    } else result.copy(error = app.getString(R.string.module_target_detection_unsupported))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Module status read failed", failure)
                    result = result.copy(error = app.getString(R.string.module_status_read_failed))
                }
            }
            result
        }
        if (generation == statusGeneration && app.isCurrent(session)) {
            mutableStatus.value = status.copy(requesting = scopeRequestInFlight)
        }
        return status
    }

    fun refresh(sync: Boolean = true) {
        scope.launch {
            readStatus(refreshDetection = true)
            if (sync) app.synchronize()
        }
    }

    fun applyUpdate(onFinished: () -> Unit) {
        if (mutableUpdating.value) return
        val generation = ++updateGeneration
        mutableUpdating.value = true
        mutableUpdateMessage.value = app.getString(R.string.update_detecting_targets)
        scope.launch {
            val session = app.currentSession()
            fun current(): Boolean = generation == updateGeneration && app.isCurrent(session)
            try {
                val active = session ?: error(app.getString(R.string.update_lsposed_disconnected))
                val bound = active.service
                val targets = withContext(Dispatchers.IO) { bound.runningTargets }
                if (!current()) return@launch
                val pending = targets.filter {
                    !RuntimeProtocol.current(it.state.name, it.loadedVersionCode, BuildConfig.VERSION_CODE.toLong())
                }
                val messages = mutableListOf<String>()
                for (target in pending) {
                    if (!current()) return@launch
                    try {
                        if (target.state == HookedTarget.State.RELOADING) {
                            messages += app.getString(R.string.update_target_reloading, target.processName)
                            continue
                        }
                        if (target.loadedVersionCode < 19) {
                            messages += app.getString(
                                R.string.update_target_too_old,
                                target.processName,
                                target.loadedVersionCode
                            )
                            continue
                        }
                        val result = withTimeoutOrNull(15_000) {
                            requestHotReload(bound, target)
                        }
                        if (!current()) return@launch
                        val resultText = when (result?.status()) {
                            HotReloadResult.Status.SUCCEEDED -> app.getString(R.string.update_succeeded)
                            HotReloadResult.Status.UNSUPPORTED -> app.getString(R.string.update_unsupported)
                            HotReloadResult.Status.FAILED -> app.getString(
                                R.string.update_failed,
                                app.getString(R.string.update_old_module_rejected)
                            )
                            HotReloadResult.Status.PROCESS_DIED -> app.getString(R.string.update_process_died)
                            HotReloadResult.Status.IN_PROGRESS -> app.getString(R.string.update_in_progress)
                            null -> app.getString(R.string.update_timeout)
                        }
                        messages += app.getString(R.string.update_target_result, target.processName, resultText)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Log.e(TAG, "Hot reload request failed for ${target.processName}", failure)
                        if (!current()) return@launch
                        messages += app.getString(
                            R.string.update_request_failed,
                            target.processName,
                            app.getString(R.string.update_old_module_rejected)
                        )
                    }
                }
                if (!current()) return@launch
                mutableUpdateMessage.value = if (messages.isEmpty()) {
                    app.getString(R.string.update_nothing_pending)
                } else messages.joinToString("\n")
                onFinished()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Hot update check failed", failure)
                if (current()) {
                    mutableUpdateMessage.value = app.getString(
                        R.string.update_check_failed,
                        app.getString(R.string.update_old_module_rejected)
                    )
                }
            } finally {
                if (generation == updateGeneration) {
                    if (!app.isCurrent(session)) mutableUpdateMessage.value = null
                    mutableUpdating.value = false
                }
            }
        }
    }

    fun requestScope() {
        if (scopeRequestInFlight) return
        val generation = ++scopeGeneration
        scopeRequestInFlight = true
        mutableStatus.value = mutableStatus.value.copy(requesting = true, message = null)
        scope.launch {
            val session = app.currentSession()
            fun current(): Boolean = generation == scopeGeneration && app.isCurrent(session)
            try {
                val active = session ?: error(app.getString(R.string.scope_lsposed_not_connected))
                val service = active.service
                val currentStatus = readStatus(active)
                check(current()) { app.getString(R.string.scope_service_changed_retry) }
                check(currentStatus.scopeKnown) { currentStatus.error ?: app.getString(R.string.scope_granted_read_failed) }
                check(currentStatus.detection.recommended.isNotEmpty()) { app.getString(R.string.scope_no_auto_host) }
                val missing = currentStatus.missingScope.toList()
                if (missing.isEmpty()) {
                    if (current()) {
                        mutableStatus.value = currentStatus.copy(
                            requesting = true,
                            message = app.getString(R.string.scope_all_recommended_granted)
                        )
                    }
                    return@launch
                }
                val approved = withTimeoutOrNull(120_000) {
                    suspendCancellableCoroutine<List<String>> { continuation ->
                        service.requestScope(missing, object : XposedService.OnScopeEventListener {
                            override fun onScopeRequestApproved(approved: List<String>) {
                                if (continuation.isActive) continuation.resume(approved)
                            }

                            override fun onScopeRequestFailed(message: String) {
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                            }
                        })
                    }
                }
                check(current()) { app.getString(R.string.scope_service_changed_result) }
                val refreshed = readStatus(active)
                val message = when {
                    approved == null -> app.getString(R.string.scope_request_timeout)
                    !refreshed.scopeKnown -> app.getString(R.string.scope_result_unverified)
                    refreshed.missingScope.isNotEmpty() -> app.getString(
                        R.string.scope_still_missing,
                        refreshed.missingScope.joinToString()
                    )
                    else -> app.getString(R.string.scope_granted_confirmed)
                }
                if (current()) mutableStatus.value = refreshed.copy(message = message, requesting = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Scope request failed", failure)
                if (current()) {
                    mutableStatus.value = mutableStatus.value.copy(error = app.getString(R.string.scope_request_failed))
                }
            } finally {
                if (generation == scopeGeneration) {
                    scopeRequestInFlight = false
                    if (app.isCurrent(session)) {
                        mutableStatus.value = mutableStatus.value.copy(requesting = false)
                    } else {
                        readStatus()
                    }
                }
            }
        }
    }

    private suspend fun requestHotReload(bound: XposedService, target: HookedTarget): HotReloadResult =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                bound.hotReloadModule(target, null) { _, reply ->
                    if (continuation.isActive) continuation.resume(reply)
                }
            }
        }

    private companion object {
        const val TAG = "ListCleaner.ModuleRuntime"
    }
}
