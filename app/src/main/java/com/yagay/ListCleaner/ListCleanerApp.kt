package com.yagay.ListCleaner

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.yagay.ListCleaner.data.IntentCatalog
import com.yagay.ListCleaner.data.ObservedEntryCache
import com.yagay.ListCleaner.data.PersistentComponentStore
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.data.readLegacyRemoteConfig
import com.yagay.ListCleaner.domain.AuthorityCandidatePolicy
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.OBSERVABLE_ENTRY_KINDS
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.RuntimeProtocol
import com.yagay.ListCleaner.domain.deriveFullySelectedPackages
import com.yagay.ListCleaner.runtime.RuntimeConfigTransport
import com.yagay.ListCleaner.runtime.ServiceSession
import com.yagay.ListCleaner.runtime.ServiceSessionRegistry
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
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
    val componentDiscoveryProtocol: Int = 0,
    val observedAtMillis: Long = System.currentTimeMillis()
)

@OptIn(FlowPreview::class)
class ListCleanerApp : Application(), XposedServiceHelper.OnServiceListener {
    lateinit var rules: RuleRepository; private set
    lateinit var catalog: IntentCatalog; private set

    private val sessionRegistry = ServiceSessionRegistry()
    private val mutableServiceSession = MutableStateFlow<ServiceSession?>(null)
    val serviceSession: StateFlow<ServiceSession?> = mutableServiceSession.asStateFlow()
    /** Compatibility surface for existing UI code. New async work should capture [serviceSession]. */
    private val mutableService = MutableStateFlow<XposedService?>(null)
    val service: StateFlow<XposedService?> = mutableService.asStateFlow()
    private val mutableSyncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = mutableSyncStatus.asStateFlow()
    private val mutableRuntime = MutableStateFlow(RuntimeStatus())
    val runtime: StateFlow<RuntimeStatus> = mutableRuntime.asStateFlow()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val runtimeTransport by lazy { RuntimeConfigTransport(this) }
    private val observedEntryCache by lazy { ObservedEntryCache(this) }
    private var pendingRecovery: ModuleConfig? = null
    private var corruptRecovery = false
    private var acknowledgedSessionGeneration = -1L
    private var acknowledgedRevision = -1L
    private var observedRemotePreferences: SharedPreferences? = null
    private val observedPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!ObservedEntryCache.isRemoteKey(key)) return@OnSharedPreferenceChangeListener
        applicationScope.launch {
            synchronizeObservedEntries()
            catalog.invalidate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mutableSyncStatus.value = getString(R.string.runtime_waiting_connection)
        mutableRuntime.value = RuntimeStatus(message = getString(R.string.runtime_waiting_verify))
        rules = RuleRepository(this)
        catalog = IntentCatalog(this)
        XposedServiceHelper.registerListener(this)
        applicationScope.launch {
            combine(rules.rules, catalog.candidates) { selected, candidates ->
                deriveFullySelectedPackages(AuthorityCandidatePolicy.normalize(candidates), selected)
            }.collect(rules::setVisibilityFullPackages)
        }
        // Service lifecycle changes must synchronize immediately. Rule edits are debounced so a
        // burst of checkbox changes produces one runtime config transfer instead of one per tap.
        applicationScope.launch {
            serviceSession.collect { synchronize() }
        }
        applicationScope.launch {
            rules.revision
                .drop(1)
                .debounce(CONFIG_SYNC_DEBOUNCE_MS)
                .collect { synchronize() }
        }
    }

    override fun onServiceBind(service: XposedService) {
        val session = sessionRegistry.bind(service)
        acknowledgedSessionGeneration = -1L
        acknowledgedRevision = -1L
        observedRemotePreferences?.unregisterOnSharedPreferenceChangeListener(observedPreferenceListener)
        observedRemotePreferences = runCatching {
            service.getRemotePreferences(RuleRepository.REMOTE_PREFS).also {
                it.registerOnSharedPreferenceChangeListener(observedPreferenceListener)
            }
        }.onFailure {
            Log.w(TAG, "Observed preference listener registration failed", it)
        }.getOrNull()
        mutableService.value = service
        mutableServiceSession.value = session
        applicationScope.launch {
            PersistentComponentStore(this@ListCleanerApp).syncRemote()
            synchronizeObservedEntries()
        }
    }

    override fun onServiceDied(service: XposedService) {
        val cleared = sessionRegistry.clear(service) ?: return
        if (serviceSession.value?.generation == cleared.generation) {
            observedRemotePreferences?.unregisterOnSharedPreferenceChangeListener(observedPreferenceListener)
            observedRemotePreferences = null
            acknowledgedSessionGeneration = -1L
            acknowledgedRevision = -1L
            mutableServiceSession.value = null
            mutableService.value = null
            publish(RuntimeStatus(message = getString(R.string.runtime_connection_lost)))
        }
    }

    fun currentSession(): ServiceSession? = sessionRegistry.snapshot()
    fun isCurrent(session: ServiceSession?): Boolean = sessionRegistry.isCurrent(session)

    suspend fun synchronizeObservedEntries(): Int = withContext(Dispatchers.IO) {
        val session = currentSession() ?: return@withContext observedEntryCache.snapshot().size
        runCatching {
            val prefs = session.service.getRemotePreferences(RuleRepository.REMOTE_PREFS)
            syncObservedEntriesFrom(prefs)
        }.onFailure {
            Log.w(TAG, "Observed entry cache synchronization failed", it)
        }.getOrElse { observedEntryCache.snapshot().size }
    }

    private fun syncObservedEntriesFrom(prefs: SharedPreferences): Int {
        val segmented = OBSERVABLE_ENTRY_KINDS.asSequence()
            .filter { kind -> prefs.contains(ObservedEntryCache.remoteKey(kind)) }
            .associateWith { kind -> prefs.getString(ObservedEntryCache.remoteKey(kind), null) }
        return observedEntryCache.synchronizeRemoteEncoded(
            legacyEncoded = prefs.getString(ObservedEntryCache.REMOTE_KEY, null),
            segmentedEncoded = segmented,
        ).size
    }

    private fun publish(status: RuntimeStatus): Boolean {
        mutableRuntime.value = status
        mutableSyncStatus.value = status.message
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
                syncObservedEntriesFrom(prefs)
                if (!rules.hasLocalConfiguration()) {
                    try {
                        val encoded = prefs.getString(RuleRepository.KEY_CONFIG, null)
                        require(encoded == null || encoded.length <= RuleRepository.MAX_BACKUP_CHARS) {
                            getString(R.string.runtime_remote_config_too_large)
                        }
                        val remote = if (encoded != null) {
                            json.decodeFromString(ModuleConfig.serializer(), encoded).validated()
                        } else {
                            readLegacyRemoteConfig(prefs, json)
                        }
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
                val canPauseTargets = rules.displayMode.value == DisplayMode.SHOW_ALL && targets.isNotEmpty() && targets.all {
                    RuntimeProtocol.supportsSafetyPause(
                        it.state.name,
                        it.loadedVersionCode,
                        BuildConfig.HOOK_COMPAT_VERSION_CODE,
                        BuildConfig.VERSION_CODE.toLong()
                    )
                }
                val incompatible = targets.filter {
                    !RuntimeProtocol.hookCompatible(
                        it.state.name,
                        it.loadedVersionCode,
                        BuildConfig.HOOK_COMPAT_VERSION_CODE,
                        BuildConfig.VERSION_CODE.toLong()
                    )
                }
                if (incompatible.isNotEmpty()) {
                    val details = incompatible.joinToString { target ->
                        "${target.processName} ${target.state.name}/v${target.loadedVersionCode}"
                    }
                    val suffix = getString(
                        if (canPauseTargets) R.string.runtime_incompatible_pause_pending
                        else R.string.runtime_incompatible_paused
                    )
                    return@withLock publishFor(
                        session,
                        RuntimeStatus(
                            message = getString(R.string.runtime_incompatible_targets, details, suffix)
                        )
                    )
                }
                check(targets.any { it.processName == "system" }) {
                    getString(R.string.runtime_system_target_missing)
                }

                val revision = rules.revision.value
                val config = rules.remoteSnapshot().copy(
                    rootDisabledComponents = PersistentComponentStore(this@ListCleanerApp).disabledKeys()
                ).validated()
                val encoded = json.encodeToString(ModuleConfig.serializer(), config)
                require(encoded.length <= RuleRepository.MAX_BACKUP_CHARS) {
                    getString(R.string.runtime_config_transfer_too_large)
                }
                val digest = RuntimeProtocol.digest(encoded)
                if (runtime.value.ready && runtime.value.digest == digest &&
                    acknowledgedSessionGeneration == session.generation &&
                    acknowledgedRevision == revision
                ) return@withLock true

                val canPause = config.mode == DisplayMode.SHOW_ALL && targets.isNotEmpty() && targets.all {
                    RuntimeProtocol.supportsSafetyPause(
                        it.state.name,
                        it.loadedVersionCode,
                        BuildConfig.HOOK_COMPAT_VERSION_CODE,
                        BuildConfig.VERSION_CODE.toLong()
                    )
                }
                val remoteEncoded = prefs.getString(RuleRepository.KEY_CONFIG, null)
                if (remoteEncoded != encoded) {
                    check(writeRemoteSnapshot(prefs, encoded)) {
                        getString(
                            if (canPause) R.string.runtime_pause_write_failed
                            else R.string.runtime_remote_write_failed
                        )
                    }
                    if (canPause) {
                        publishFor(session, RuntimeStatus(message = getString(R.string.runtime_pause_submitted)))
                    }
                }
                if (runtime.value.digest != digest) {
                    publishFor(session, RuntimeStatus(message = getString(R.string.runtime_waiting_ack)))
                }

                var ack = runtimeTransport.queryAck(digest)
                if (ack == null) {
                    ack = runtimeTransport.pushConfig(encoded, digest, revision)
                }
                repeat(2) {
                    if (ack == null) {
                        if (!isCurrent(session)) return@withLock false
                        delay(150)
                        ack = runtimeTransport.queryAck(digest)
                    }
                }

                check(isCurrent(session)) { getString(R.string.runtime_connection_changed) }
                val confirmed = ack
                check(confirmed != null && confirmed.digest == digest) { getString(R.string.runtime_ack_missing) }
                check(rules.revision.value == revision) { getString(R.string.runtime_config_changed) }
                val published = publishFor(
                    session,
                    RuntimeStatus(
                        ready = true,
                        message = getString(R.string.runtime_confirmed_hits),
                        digest = digest,
                        queryHits = confirmed.queryHits,
                        visibilityHits = confirmed.visibilityHits,
                        orderingHits = confirmed.orderingHits,
                        componentDiscoveryProtocol = confirmed.componentDiscoveryProtocol
                    )
                )
                if (published) {
                    acknowledgedSessionGeneration = session.generation
                    acknowledgedRevision = revision
                }
                published
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Runtime synchronization failed", failure)
                if (attemptSession != null && !isCurrent(attemptSession)) {
                    false
                } else {
                    acknowledgedSessionGeneration = -1L
                    acknowledgedRevision = -1L
                    publish(RuntimeStatus(message = getString(R.string.runtime_validation_failed)))
                }
            }
        }
    }

    private fun writeRemoteSnapshot(
        prefs: SharedPreferences,
        encoded: String,
    ): Boolean = prefs.edit()
        .putString(RuleRepository.KEY_CONFIG, encoded)
        .commit()

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
            acknowledgedSessionGeneration = -1L
            acknowledgedRevision = -1L
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
        const val CONFIG_SYNC_DEBOUNCE_MS = 300L
    }
}
