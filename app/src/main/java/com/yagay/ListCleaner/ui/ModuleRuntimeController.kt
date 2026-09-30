package com.yagay.ListCleaner.ui

import android.util.Log
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.ResolverScopeDetector
import com.yagay.ListCleaner.data.ScopeDetection
import com.yagay.ListCleaner.domain.RuntimeProtocol
import com.yagay.ListCleaner.runtime.ServiceSession
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        RuntimeProtocol.hookCompatible(
            target.state,
            target.version,
            BuildConfig.HOOK_COMPAT_VERSION_CODE,
            BuildConfig.VERSION_CODE.toLong()
        ) &&
            detection.hosts.any { host -> host.packageName != "system" && host.processName == target.processName }
    }
    val outdated: Boolean get() = runningTargets.any {
        !RuntimeProtocol.hookCompatible(
            it.state,
            it.version,
            BuildConfig.HOOK_COMPAT_VERSION_CODE,
            BuildConfig.VERSION_CODE.toLong()
        )
    }
}

/** Owns LSPosed service/session state, scope requests and hook-generation restart checks. */
internal class ModuleRuntimeController(
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
    val status: StateFlow<ModuleStatus> = mutableStatus.asStateFlow()

    private val mutableUpdating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = mutableUpdating.asStateFlow()

    private val mutableUpdateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = mutableUpdateMessage.asStateFlow()

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

    /**
     * Multiple libxposed Java entries own independent system hooks in this module. A partial hot
     * reload can update only some of them, leaving mixed generations in system_server. Therefore a
     * hook compatibility bump is handled as a restart requirement. Manager-only APK updates keep
     * the same HOOK_COMPAT_VERSION_CODE and continue without a restart.
     */
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
                val targets = withContext(Dispatchers.IO) { active.service.runningTargets }
                if (!current()) return@launch
                val pending = targets.filter {
                    !RuntimeProtocol.hookCompatible(
                        it.state.name,
                        it.loadedVersionCode,
                        BuildConfig.HOOK_COMPAT_VERSION_CODE,
                        BuildConfig.VERSION_CODE.toLong()
                    )
                }
                mutableUpdateMessage.value = if (pending.isEmpty()) {
                    app.getString(R.string.update_nothing_pending)
                } else {
                    pending.joinToString("\n") { target ->
                        app.getString(
                            R.string.update_target_restart_required,
                            target.processName,
                            target.loadedVersionCode,
                            BuildConfig.HOOK_COMPAT_VERSION_CODE
                        )
                    }
                }
                onFinished()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Hook generation check failed", failure)
                if (current()) {
                    mutableUpdateMessage.value = app.getString(
                        R.string.update_check_failed,
                        app.getString(R.string.module_status_read_failed)
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

    private companion object {
        const val TAG = "ListCleaner.ModuleRuntime"
    }
}
