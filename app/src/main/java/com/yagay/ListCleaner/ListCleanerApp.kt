package com.yagay.ListCleaner

import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.Log
import com.yagay.ListCleaner.data.IntentCatalog
import com.yagay.ListCleaner.data.PersistentComponentStore
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
import java.util.UUID

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
    private var acknowledgedSessionGeneration = -1L
    private var acknowledgedRevision = -1L

    private data class RuntimeAck(
        val digest: String,
        val queryHits: Long,
        val visibilityHits: Long,
        val orderingHits: Long,
        val componentDiscoveryProtocol: Int,
        val runtimeProtocol: Int,
        val revision: Long,
    )

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
        acknowledgedSessionGeneration = -1L
        acknowledgedRevision = -1L
        this.service.value = service
        serviceSession.value = session
        applicationScope.launch {
            // Keep the framework-owned discovery/guard policy synchronized after every service
            // reconnect. Root disable remains authoritative; this only refreshes the secondary
            // LSPosed protection snapshot.
            PersistentComponentStore(this@ListCleanerApp).syncRemote()
        }
    }

    override fun onServiceDied(service: XposedService) {
        val cleared = sessionRegistry.clear(service) ?: return
        if (serviceSession.value?.generation == cleared.generation) {
            acknowledgedSessionGeneration = -1L
            acknowledgedRevision = -1L
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
                            message = getString(
                                R.string.runtime_incompatible_targets,
                                details,
                                suffix
                            )
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
                    acknowledgedRevision == revision) {
                    return@withLock true
                }
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
                        publishFor(
                            session,
                            RuntimeStatus(message = getString(R.string.runtime_pause_submitted))
                        )
                    }
                }
                if (runtime.value.digest != digest) {
                    publishFor(
                        session,
                        RuntimeStatus(message = getString(R.string.runtime_waiting_ack))
                    )
                }

                // Official LSPosed normally observes the persisted RemotePreferences update
                // immediately. Probe once first so that path stays cheap. If the running hook
                // still has a stale snapshot (for example Vector), send the actual config through
                // the UID-authenticated Runtime Probe v2 transport.
                var ack = queryRuntimeAck(digest)
                if (ack == null) {
                    ack = pushRuntimeConfig(
                        encoded = encoded,
                        digest = digest,
                        revision = revision,
                    )
                }
                repeat(2) {
                    if (ack == null) {
                        if (!isCurrent(session)) return@withLock false
                        delay(150)
                        ack = queryRuntimeAck(digest)
                    }
                }

                check(isCurrent(session)) { getString(R.string.runtime_connection_changed) }
                val confirmed = ack
                check(confirmed != null) { getString(R.string.runtime_ack_missing) }
                check(confirmed.digest == digest) { getString(R.string.runtime_ack_missing) }
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

    @Suppress("DEPRECATION")
    private fun runtimeQuery(intent: Intent): List<ResolveInfo> =
        packageManager.queryIntentActivities(intent, 0)

    private fun parseRuntimeAck(
        results: List<ResolveInfo>,
        expectedDigest: String,
    ): RuntimeAck? {
        val prefix = "${BuildConfig.HOOK_COMPAT_VERSION_CODE}:$expectedDigest"
        val info = results.firstOrNull { candidate ->
            candidate.activityInfo?.packageName == packageName &&
                candidate.activityInfo?.name == RuntimeProtocol.COMPONENT &&
                (candidate.nonLocalizedLabel?.toString() == prefix ||
                    candidate.nonLocalizedLabel?.toString()?.startsWith("$prefix:") == true)
        } ?: return null
        val parts = info.nonLocalizedLabel?.toString().orEmpty().split(':')
        return RuntimeAck(
            digest = expectedDigest,
            queryHits = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            visibilityHits = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            orderingHits = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            componentDiscoveryProtocol = parts.getOrNull(5)?.toIntOrNull() ?: 0,
            runtimeProtocol = parts.getOrNull(6)?.toIntOrNull() ?: 1,
            revision = parts.getOrNull(7)?.toLongOrNull() ?: -1L,
        )
    }

    private fun queryRuntimeAck(expectedDigest: String): RuntimeAck? =
        parseRuntimeAck(
            runtimeQuery(
                Intent(RuntimeProtocol.ACTION)
                    .setPackage(packageName)
                    .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                    .putExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST, expectedDigest)
            ),
            expectedDigest,
        )

    private fun pushRuntimeConfig(
        encoded: String,
        digest: String,
        revision: Long,
    ): RuntimeAck? {
        val chunks = encoded.chunked(RuntimeProtocol.CONFIG_CHUNK_CHARS)
        require(chunks.isNotEmpty() && chunks.size <= RuntimeProtocol.MAX_CONFIG_CHUNKS) {
            getString(R.string.runtime_config_transfer_too_large)
        }
        val transferId = UUID.randomUUID().toString()

        runtimeQuery(
            Intent(RuntimeProtocol.ACTION)
                .setPackage(packageName)
                .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                .putExtra(RuntimeProtocol.EXTRA_OPERATION, RuntimeProtocol.OP_BEGIN)
                .putExtra(RuntimeProtocol.EXTRA_TRANSFER_ID, transferId)
                .putExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST, digest)
                .putExtra(RuntimeProtocol.EXTRA_REVISION, revision)
                .putExtra(RuntimeProtocol.EXTRA_TOTAL_CHUNKS, chunks.size)
                .putExtra(RuntimeProtocol.EXTRA_TOTAL_CHARS, encoded.length)
        )

        chunks.forEachIndexed { index, chunk ->
            runtimeQuery(
                Intent(RuntimeProtocol.ACTION)
                    .setPackage(packageName)
                    .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                    .putExtra(RuntimeProtocol.EXTRA_OPERATION, RuntimeProtocol.OP_CHUNK)
                    .putExtra(RuntimeProtocol.EXTRA_TRANSFER_ID, transferId)
                    .putExtra(RuntimeProtocol.EXTRA_CHUNK_INDEX, index)
                    .putExtra(RuntimeProtocol.EXTRA_CONFIG_CHUNK, chunk)
            )
        }

        return parseRuntimeAck(
            runtimeQuery(
                Intent(RuntimeProtocol.ACTION)
                    .setPackage(packageName)
                    .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                    .putExtra(RuntimeProtocol.EXTRA_OPERATION, RuntimeProtocol.OP_COMMIT)
                    .putExtra(RuntimeProtocol.EXTRA_TRANSFER_ID, transferId)
                    .putExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST, digest)
                    .putExtra(RuntimeProtocol.EXTRA_REVISION, revision)
            ),
            digest,
        )
    }

    private fun writeRemoteSnapshot(
        prefs: android.content.SharedPreferences,
        encoded: String,
    ): Boolean =
        // Persistent/cold-start copy only. Runtime correctness is provided by Probe v2.
        prefs.edit()
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
    }
}
