package com.yagay.ListCleaner

import android.app.Application
import android.content.Intent
import android.util.Log
import com.yagay.ListCleaner.data.IntentCatalog
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.RuntimeProtocol
import com.yagay.ListCleaner.domain.deriveFullySelectedPackages
import com.yagay.ListCleaner.runtime.ServiceSession
import com.yagay.ListCleaner.runtime.ServiceSessionRegistry
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class RuntimeStatus(
    val ready: Boolean = false,
    val needsDecision: Boolean = false,
    val message: String = "",
    val digest: String = "",
    val recoveryCorrupt: Boolean = false,
    val queryHits: Long = 0,
    val visibilityHits: Long = 0,
    val orderingHits: Long = 0,
    val observedAtMillis: Long = System.currentTimeMillis()
)

class ListCleanerApp : Application(), XposedServiceHelper.OnServiceListener {
    lateinit var rules: RuleRepository; private set
    lateinit var catalog: IntentCatalog; private set

    private val sessionRegistry = ServiceSessionRegistry()
    val serviceSession = MutableStateFlow<ServiceSession?>(null)
    /** Compatibility surface for existing UI code. New async work should capture [serviceSession]. */
    val service = MutableStateFlow<XposedService?>(null)
    val syncStatus = MutableStateFlow("")
    val runtime = MutableStateFlow(RuntimeStatus())

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var pendingRecovery: ModuleConfig? = null
    private var corruptRecovery = false

    override fun onCreate() {
        super.onCreate()
        syncStatus.value = getString(R.string.runtime_waiting_connection)
        runtime.value = RuntimeStatus(message = getString(R.string.runtime_waiting_verify))
        rules = RuleRepository(this)
        catalog = IntentCatalog(this)
        XposedServiceHelper.registerListener(this)
        applicationScope.launch {
            combine(rules.rules, catalog.candidates) { selected, candidates ->
                deriveFullySelectedPackages(candidates, selected)
            }.collect(rules::setVisibilityFullPackages)
        }
        applicationScope.launch {
            combine(rules.revision, serviceSession) { _, _ -> Unit }.collect { synchronize() }
        }
    }

    override fun onServiceBind(service: XposedService) {
        val session = sessionRegistry.bind(service)
        this.service.value = service
        serviceSession.value = session
    }

    override fun onServiceDied(service: XposedService) {
        val cleared = sessionRegistry.clear(service) ?: return
        if (serviceSession.value?.generation == cleared.generation) {
            serviceSession.value = null
            this.service.value = null
            publish(RuntimeStatus(message = getString(R.string.runtime_connection_lost)))
        }
    }

    fun currentSession(): ServiceSession? = sessionRegistry.snapshot()

    fun isCurrent(session: ServiceSession?): Boolean = sessionRegistry.isCurrent(session)

    private fun publish(status: RuntimeStatus): Boolean {
        runtime.value = status
        syncStatus.value = status.message
        return status.ready
    }

    private fun publishFor(session: ServiceSession, status: RuntimeStatus): Boolean =
        if (isCurrent(session)) publish(status) else false

    /** Serialized bootstrap/sync/probe, also used before every catalog query batch. */
    suspend fun synchronize(): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            var attemptSession: ServiceSession? = null
            try {
                val session = currentSession() ?: return@withLock publish(
                    RuntimeStatus(message = getString(R.string.runtime_lsposed_disconnected))
                )
                attemptSession = session
                val bound = session.service
                val prefs = bound.getRemotePreferences(RuleRepository.REMOTE_PREFS)
                if (!rules.hasLocalConfiguration()) {
                    try {
                        val encoded = prefs.getString(RuleRepository.KEY_CONFIG, null)
                        require(encoded == null || encoded.length <= RuleRepository.MAX_BACKUP_CHARS) {
                            getString(R.string.runtime_remote_config_too_large)
                        }
                        val remote = if (encoded != null) {
                            json.decodeFromString(ModuleConfig.serializer(), encoded).validated()
                        } else if (prefs.contains(RuleRepository.KEY_RULES)) {
                            ModuleConfig(
                                prefs.getStringSet(RuleRepository.KEY_RULES, emptySet()).orEmpty()
                                    .map {
                                        requireNotNull(com.yagay.ListCleaner.domain.ComponentRule.fromId(it)) {
                                            getString(R.string.runtime_legacy_rules_corrupt)
                                        }
                                    }.toSet(),
                                DisplayMode.fromStored(
                                    prefs.getString(RuleRepository.KEY_DISPLAY_MODE, null),
                                    prefs.getBoolean(RuleRepository.KEY_BLACKLIST, true)
                                ),
                                json.decodeFromString(
                                    PriorityConfig.serializer(),
                                    prefs.getString(RuleRepository.KEY_PRIORITIES, null) ?: "{}"
                                ),
                                prefs.getBoolean(RuleRepository.KEY_DIAGNOSTIC, false)
                            ).validated()
                        } else null
                        if (!isCurrent(session)) return@withLock false
                        if (remote != null) {
                            corruptRecovery = false
                            pendingRecovery = remote
                            return@withLock publishFor(
                                session,
                                RuntimeStatus(
                                    needsDecision = true,
                                    message = getString(R.string.runtime_recovery_available, remote.rules.size)
                                )
                            )
                        }
                        rules.markInitialized()
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        Log.e(TAG, "Remote configuration recovery validation failed", failure)
                        if (!isCurrent(session)) return@withLock false
                        pendingRecovery = null
                        corruptRecovery = true
                        return@withLock publishFor(
                            session,
                            RuntimeStatus(
                                needsDecision = true,
                                recoveryCorrupt = true,
                                message = getString(R.string.runtime_recovery_corrupt)
                            )
                        )
                    }
                }
                pendingRecovery = null
                corruptRecovery = false
                check(bound.apiVersion >= 102) { getString(R.string.runtime_framework_api_required) }
                val targets = bound.runningTargets
                check(isCurrent(session)) { getString(R.string.runtime_connection_changed) }
                val config = rules.remoteSnapshot()
                val encoded = json.encodeToString(ModuleConfig.serializer(), config)
                require(encoded.length <= RuleRepository.MAX_BACKUP_CHARS) {
                    getString(R.string.runtime_config_transfer_too_large)
                }
                val digest = RuntimeProtocol.digest(encoded)
                val canPause = config.mode == DisplayMode.SHOW_ALL && targets.isNotEmpty() && targets.all {
                    RuntimeProtocol.supportsSafetyPause(it.state.name, it.loadedVersionCode)
                }
                if (canPause && prefs.getString(RuleRepository.KEY_CONFIG, null) != encoded) {
                    check(prefs.edit().putString(RuleRepository.KEY_CONFIG, encoded).commit()) {
                        getString(R.string.runtime_pause_write_failed)
                    }
                    publishFor(session, RuntimeStatus(message = getString(R.string.runtime_pause_submitted)))
                }
                val incompatible = targets.filter {
                    !RuntimeProtocol.current(
                        it.state.name,
                        it.loadedVersionCode,
                        BuildConfig.VERSION_CODE.toLong()
                    )
                }
                check(incompatible.isEmpty()) {
                    val details = incompatible.joinToString { target ->
                        "${target.processName} ${target.state.name}/v${target.loadedVersionCode}"
                    }
                    val suffix = getString(
                        if (canPause) R.string.runtime_incompatible_pause_pending
                        else R.string.runtime_incompatible_paused
                    )
                    getString(R.string.runtime_incompatible_targets, details, suffix)
                }
                check(targets.any { it.processName == "system" }) {
                    getString(R.string.runtime_system_target_missing)
                }
                if (runtime.value.digest != digest) {
                    publishFor(session, RuntimeStatus(message = getString(R.string.runtime_waiting_ack)))
                }
                if (prefs.getString(RuleRepository.KEY_CONFIG, null) != encoded) {
                    check(prefs.edit().putString(RuleRepository.KEY_CONFIG, encoded).commit()) {
                        getString(R.string.runtime_remote_write_failed)
                    }
                }

                var acknowledged = false
                var queryHits = 0L
                var visibilityHits = 0L
                var orderingHits = 0L
                repeat(4) {
                    if (!isCurrent(session)) return@withLock false
                    if (!acknowledged) {
                        @Suppress("DEPRECATION")
                        val results = packageManager.queryIntentActivities(
                            Intent(RuntimeProtocol.ACTION).setPackage(packageName),
                            0
                        )
                        val prefix = "${BuildConfig.VERSION_CODE}:$digest"
                        results.firstOrNull { info ->
                            info.activityInfo?.packageName == packageName &&
                                info.activityInfo?.name == RuntimeProtocol.COMPONENT &&
                                (info.nonLocalizedLabel?.toString() == prefix ||
                                    info.nonLocalizedLabel?.toString()?.startsWith("$prefix:") == true)
                        }?.let { info ->
                            acknowledged = true
                            val parts = info.nonLocalizedLabel?.toString().orEmpty().split(':')
                            if (parts.size >= 5) {
                                queryHits = parts[2].toLongOrNull() ?: 0L
                                visibilityHits = parts[3].toLongOrNull() ?: 0L
                                orderingHits = parts[4].toLongOrNull() ?: 0L
                            }
                        }
                        if (!acknowledged) delay(150)
                    }
                }
                check(isCurrent(session)) { getString(R.string.runtime_connection_changed) }
                check(acknowledged) { getString(R.string.runtime_ack_missing) }
                check(rules.remoteSnapshot() == config) { getString(R.string.runtime_config_changed) }
                publishFor(
                    session,
                    RuntimeStatus(
                        ready = true,
                        message = getString(R.string.runtime_confirmed_hits),
                        digest = digest,
                        queryHits = queryHits,
                        visibilityHits = visibilityHits,
                        orderingHits = orderingHits
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Runtime synchronization failed", failure)
                if (attemptSession != null && !isCurrent(attemptSession)) {
                    false
                } else {
                    publish(RuntimeStatus(message = getString(R.string.runtime_validation_failed)))
                }
            }
        }
    }

    suspend fun resolveRecovery(restore: Boolean) {
        syncMutex.withLock {
            if (rules.hasLocalConfiguration()) return@withLock
            val remote = pendingRecovery
            if (restore && remote == null) return@withLock
            if (!restore && remote == null && !corruptRecovery) return@withLock
            rules.restoreRemote(
                if (restore) requireNotNull(remote)
                else ModuleConfig(emptySet(), DisplayMode.SHOW_ALL, PriorityConfig(), false)
            )
            pendingRecovery = null
            corruptRecovery = false
            publish(
                RuntimeStatus(
                    message = getString(
                        if (restore) R.string.runtime_recovered_waiting
                        else R.string.runtime_reset_waiting
                    )
                )
            )
        }
        synchronize()
    }

    private companion object {
        const val TAG = "ListCleaner.App"
    }
}
