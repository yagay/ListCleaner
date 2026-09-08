package com.yagay.ListCleaner

import android.app.Application
import android.content.Intent
import com.yagay.ListCleaner.data.IntentCatalog
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.RuntimeProtocol
import com.yagay.ListCleaner.domain.deriveFullySelectedPackages
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            combine(rules.revision, service) { _, _ -> Unit }.collect { synchronize() }
        }
    }

    override fun onServiceBind(service: XposedService) { this.service.value = service }
    override fun onServiceDied(service: XposedService) {
        if (this.service.value === service) {
            this.service.value = null
            publish(RuntimeStatus(message = getString(R.string.runtime_connection_lost)))
        }
    }

    private fun publish(status: RuntimeStatus): Boolean {
        runtime.value = status
        syncStatus.value = status.message
        return status.ready
    }

    /** Serialized bootstrap/sync/probe, also used before every catalog query batch. */
    suspend fun synchronize(): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val bound = service.value ?: return@withLock publish(
                    RuntimeStatus(message = getString(R.string.runtime_lsposed_disconnected))
                )
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
                        if (remote != null) {
                            corruptRecovery = false
                            pendingRecovery = remote
                            return@withLock publish(
                                RuntimeStatus(
                                    needsDecision = true,
                                    message = getString(R.string.runtime_recovery_available, remote.rules.size)
                                )
                            )
                        }
                        rules.markInitialized()
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        pendingRecovery = null
                        corruptRecovery = true
                        return@withLock publish(
                            RuntimeStatus(
                                needsDecision = true,
                                recoveryCorrupt = true,
                                message = getString(
                                    R.string.runtime_recovery_corrupt,
                                    failure.javaClass.simpleName
                                )
                            )
                        )
                    }
                }
                pendingRecovery = null
                corruptRecovery = false
                check(bound.apiVersion >= 102) { getString(R.string.runtime_framework_api_required) }
                val targets = bound.runningTargets
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
                    publish(RuntimeStatus(message = getString(R.string.runtime_pause_submitted)))
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
                    publish(RuntimeStatus(message = getString(R.string.runtime_waiting_ack)))
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
                check(service.value === bound) { getString(R.string.runtime_connection_changed) }
                check(acknowledged) { getString(R.string.runtime_ack_missing) }
                check(rules.remoteSnapshot() == config) { getString(R.string.runtime_config_changed) }
                publish(
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
                publish(
                    RuntimeStatus(
                        message = failure.message ?: getString(R.string.runtime_validation_failed)
                    )
                )
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
}
