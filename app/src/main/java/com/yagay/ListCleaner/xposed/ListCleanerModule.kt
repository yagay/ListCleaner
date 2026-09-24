package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.domain.RuntimeProtocol
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.PersistentComponentState
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PriorityConfig
import com.yagay.ListCleaner.domain.DefaultOpenConfig
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.BrowserLinkConfig
import com.yagay.ListCleaner.domain.normalizeBrowserHost
import com.yagay.ListCleaner.domain.VisibilityCompatConfig
import com.yagay.ListCleaner.domain.matchOpenPreset
import com.yagay.ListCleaner.domain.prioritizeApps
import com.yagay.ListCleaner.domain.selectedKinds
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import com.yagay.ListCleaner.domain.ModuleConfig
import com.yagay.ListCleaner.domain.FilterPolicy
import com.yagay.ListCleaner.domain.intentKind
import com.yagay.ListCleaner.domain.webTargetKind
import com.yagay.ListCleaner.domain.ManagerIdentity
import com.yagay.ListCleaner.domain.VisibilityLayout
import com.yagay.ListCleaner.domain.VisibilitySignature
import com.yagay.ListCleaner.domain.VisibilityScope
import kotlinx.serialization.json.Json
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Level 1 filters PackageManager resolver results. Level 2 runs only in system_server and adds
 * caller-aware Android package-visibility filtering for apps selected in the hidden-app list.
 * Third-party apps do not need LSPosed scope. Real package/component state is never changed.
 */
class ListCleanerModule : XposedModule() {
    private data class RuleSnapshot(
        val configured: Set<String>,
        val displayMode: DisplayMode,
        val priorities: PriorityConfig,
        val defaultOpen: DefaultOpenConfig,
        val openTypes: OpenTypeConfig,
        val browserLinks: BrowserLinkConfig,
        val diagnostic: Boolean,
        val managerAppId: Int = -1,
        val digest: String = "",
        val hiddenFromApps: Set<String> = emptySet(),
        val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig()
    ) {
        val selectedKinds: Set<IntentKind> = selectedKinds(configured)
        private val selectedPackages: Map<IntentKind, Set<String>> = configured.mapNotNull { id ->
            val parts = id.split('|', limit = 3)
            val kind = parts.getOrNull(0)?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            val packageName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            kind to packageName
        }.groupBy({ it.first }, { it.second }).mapValues { (_, packages) -> packages.toSet() }
        val allSelectedPackages: Set<String> = visibilityCompat.activePackages()
        fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
        fun hasPackageSelection(kind: IntentKind, packageName: String): Boolean = packageName in selectedPackages[kind].orEmpty()
    }

    private data class ListResult(val values: List<*>, val rebuild: (List<*>) -> Any?)
    private data class MethodAccessor(val method: Method?)
    private data class PackageNameAccessor(val getter: Method?, val field: Field?)
    private data class ParceledListAccessor(val getList: Method, val constructor: Constructor<*>)
    private data class RuntimeTransfer(
        val callerUid: Int,
        val managerAppId: Int,
        val id: String,
        val digest: String,
        val revision: Long,
        val totalChunks: Int,
        val totalChars: Int,
        val startedAt: Long,
        val chunks: Array<String?>,
    )
    private data class ResolverPolicy(
        val include: Boolean,
        val rank: Int,
        val title: String?,
    )

    @Volatile
    private var snapshot = RuleSnapshot(
        emptySet(), DisplayMode.HIDE_SELECTED, PriorityConfig(), DefaultOpenConfig(),
        OpenTypeConfig(), BrowserLinkConfig(), false
    )
    private var lastEncodedConfig: String? = null
    @Volatile private var runtimeTransportActive = false
    @Volatile private var appliedRuntimeRevision = -1L
    private var incomingTransfer: RuntimeTransfer? = null
    @Volatile private var listenerRegistered = false
    private var processName = ""
    private var systemServer = false
    private var diagnosticWindow = 0L
    private var diagnosticCount = 0
    private var suppressedCount = 0L
    private var detailCount = 0
    private var suppressedDetails = 0L
    @Volatile private var nextPreferencePoll = 0L
    private val queryInProgress = ThreadLocal<Boolean>()
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val tracedActions = ConcurrentHashMap.newKeySet<String>()
    private val queryHits = AtomicLong()
    private val visibilityHits = AtomicLong()
    private val orderingHits = AtomicLong()
    private val packagesForUidCache = ConcurrentHashMap<Class<*>, MethodAccessor>()
    private val packageNameAccessorCache = ConcurrentHashMap<Class<*>, PackageNameAccessor>()
    private val callerPackageFieldsCache = ConcurrentHashMap<Class<*>, List<Field>>()
    private val parceledListAccessorCache = ConcurrentHashMap<Class<*>, ParceledListAccessor>()
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG ||
            (key in RuleRepository.SYNCED_KEYS && !preferences.contains(RuleRepository.KEY_CONFIG))) {
            refreshRulesSafely("preference changed")
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        systemServer = param.isSystemServer
        record("MODULE_LOADED version=${BuildConfig.VERSION_CODE} systemServer=$systemServer frameworkLog=direct-api")
    }

    @Synchronized override fun onHotReloading(param: HotReloadingParam): Boolean {
        if (listenerRegistered) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            listenerRegistered = false
        }
        record("HOT_RELOAD_RETIRING version=${BuildConfig.VERSION_CODE}")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        processName = param.processName
        systemServer = param.isSystemServer
        val loaders = linkedSetOf<ClassLoader>()
        try {
            val reloadBaseProcess = processName.substringBefore(':')
            val expectedQueryHookId = when {
                systemServer -> "$HOOK_ID-system"
                reloadBaseProcess == FRAMEWORK_PACKAGE || reloadBaseProcess == INTENT_RESOLVER_PACKAGE -> "$HOOK_ID-resolver"
                reloadBaseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> null
            }
            if (expectedQueryHookId != null) {
                check(param.oldHookHandles.any { handle ->
                    val method = handle.executable as? Method
                    handle.id == expectedQueryHookId && method != null && isQueryIntentActivitiesMethod(method)
                }) { "No compatible query handle; restart required" }
            }
            initializePreferences()
            param.oldHookHandles.forEach { handle ->
                val method = handle.executable as? Method
                method?.declaringClass?.classLoader?.let(loaders::add)
                val layer = when (handle.id) {
                    "$HOOK_ID-system" -> Layer.SYSTEM
                    "$HOOK_ID-resolver" -> Layer.RESOLVER
                    else -> null
                }
                when {
                    method != null && layer != null && isQueryIntentActivitiesMethod(method) -> {
                        handle.replaceHook(queryHooker(layer))
                        installedMethods.add("${method.declaringClass.name}#${method.toGenericString()}@$layer")
                    }
                    method != null && handle.id == VISIBILITY_HOOK_ID && method.name == "shouldFilterApplication" -> {
                        val adapter = visibilityAdapter(method)
                        if (adapter != null) {
                            handle.replaceHook(systemVisibilityHooker(adapter))
                            installedMethods.add("VISIBILITY#${method.toGenericString()}")
                        } else {
                            record("VISIBILITY_RELOAD_SKIP method=${method.toGenericString()} reason=unsupported_signature")
                            handle.unhook()
                        }
                    }
                    method != null && handle.id == "ic-final-order" -> {
                        handle.replaceHook(orderHooker())
                        installedMethods.add("ORDER#${method.toGenericString()}")
                    }
                    method != null && handle.id == "ic-alpha-order" -> {
                        handle.replaceHook(alphabeticalOrderHooker())
                        installedMethods.add("ALPHA#${method.toGenericString()}")
                    }
                    else -> handle.unhook()
                }
            }
            if (!systemServer) runCatching {
                val application = Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").invoke(null) as? Context
                application?.classLoader?.let(loaders::add)
            }.onFailure { record("ORDER_LOADER_UNAVAILABLE error=${it.javaClass.name}") }
            val baseProcess = processName.substringBefore(':')
            val expectedLayer = when {
                systemServer -> Layer.SYSTEM
                baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE -> Layer.RESOLVER
                baseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> null
            }
            loaders.forEach {
                when (expectedLayer) {
                    Layer.SYSTEM -> installSystemServerQueryHooks(it)
                    Layer.RESOLVER -> installResolverClientHooks(it)
                    null -> record("HOT_RELOAD_SKIP package=$baseProcess reason=system_scope_pseudo_process")
                }
            }
            if (expectedLayer != null) {
                check(installedMethods.any { it.endsWith("@$expectedLayer") }) {
                    "No query hooks after reload; restart required"
                }
            }
            record("HOT_RELOAD_READY version=${BuildConfig.VERSION_CODE} hooks=${installedMethods.size}")
            if (!systemServer && !processName.startsWith(SYSTEM_UI_PACKAGE)) recordOrderingCapability()
        } catch (failure: Throwable) {
            record("HOT_RELOAD_FAILED version=${BuildConfig.VERSION_CODE} error=${failure.javaClass.name}")
            throw failure
        }
    }

    @Synchronized private fun pollPreferences() {
        // Probe v2 owns the live snapshot once a verified runtime config has been applied.
        // Avoid SharedPreferences reads and config hashing on PackageManager/Resolver hot paths.
        if (runtimeTransportActive) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextPreferencePoll) return
        nextPreferencePoll = now + if (listenerRegistered) 10_000 else 2_000
        refreshRulesSafely("query poll")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        systemServer = true
        record("SYSTEM_SERVER_STARTING loader=${param.classLoader.javaClass.name}")
        initializePreferences()
        installSystemServerQueryHooks(param.classLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        record("PACKAGE_READY package=${param.packageName} loader=${param.classLoader.javaClass.name}")
        if (systemServer) {
            if (!listenerRegistered) initializePreferences()
            return
        }
        initializePreferences()
        if (param.packageName == FRAMEWORK_PACKAGE || param.packageName == INTENT_RESOLVER_PACKAGE) {
            installResolverClientHooks(param.classLoader)
        } else if (param.packageName == SYSTEM_SCOPE_PACKAGE) {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=system_scope_pseudo_process")
        } else {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=third_party_scope_not_required")
        }
    }

    private fun installSystemServerQueryHooks(classLoader: ClassLoader) {
        var installed = 0
        SYSTEM_QUERY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                record("CLASS_UNAVAILABLE class=$className error=${it.javaClass.name}")
                return@forEach
            }
            runCatching {
                generateSequence(clazz as Class<*>?) { it.superclass }
                    .flatMap { it.declaredMethods.asSequence() }
                    .filter(::isQueryIntentActivitiesMethod)
                    .distinctBy(Method::toGenericString)
                    .toList()
            }.getOrElse {
                record("METHOD_DISCOVERY_FAILED class=$className error=${it.javaClass.name}")
                emptyList()
            }.forEach { method ->
                if (installHook(method, Layer.SYSTEM)) installed++
            }
        }
        record("SYSTEM_HOOKS new=$installed total=${installedMethods.size}")
        installSystemVisibilityHooks(classLoader)
    }

    private fun installSystemVisibilityHooks(classLoader: ClassLoader) {
        var installed = 0
        SYSTEM_VISIBILITY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                diagnostic("VISIBILITY_CLASS_UNAVAILABLE class=$className error=${it.javaClass.name}")
                return@forEach
            }
            val methods = runCatching {
                generateSequence(clazz as Class<*>?) { it.superclass }
                    .flatMap { it.declaredMethods.asSequence() }
                    .filter { method ->
                        method.name == "shouldFilterApplication" &&
                            method.returnType == Boolean::class.javaPrimitiveType
                    }.distinctBy(Method::toGenericString).toList()
            }.getOrElse {
                record("VISIBILITY_DISCOVERY_FAILED class=$className error=${it.javaClass.name}")
                emptyList()
            }
            methods.forEach { method ->
                val adapter = visibilityAdapter(method)
                if (adapter == null) {
                    record("VISIBILITY_HOOK_SKIP method=${method.toGenericString()} reason=unsupported_signature")
                    return@forEach
                }
                val key = "VISIBILITY#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(VISIBILITY_HOOK_ID).intercept(systemVisibilityHooker(adapter))
                    installed++
                    record("VISIBILITY_HOOK_INSTALLED method=${method.toGenericString()} uidIndex=${adapter.uidIndex} callerIndex=${adapter.callerSettingIndex} targetIndex=${adapter.targetIndex}")
                }.onFailure {
                    installedMethods.remove(key)
                    record("VISIBILITY_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                }
            }
        }
        record("VISIBILITY_HOOKS new=$installed callers=${snapshot.hiddenFromApps.size} scopes=${snapshot.visibilityCompat.scopes.map { it.name }.sorted()} targets=${snapshot.allSelectedPackages.size}")
    }

    private fun visibilityAdapter(method: Method): VisibilityLayout? =
        VisibilitySignature.parse(method.parameterTypes.map { it.name })

    private fun systemVisibilityHooker(adapter: VisibilityLayout) = XposedInterface.Hooker { chain ->
        pollPreferences()
        val current = snapshot
        if (current.displayMode != DisplayMode.HIDE_SELECTED ||
            current.hiddenFromApps.isEmpty() || current.allSelectedPackages.isEmpty()) {
            return@Hooker chain.proceed()
        }

        val args = chain.args
        val callingUid = args.getOrNull(adapter.uidIndex) as? Int ?: return@Hooker chain.proceed()
        if (callingUid < 10_000) return@Hooker chain.proceed()

        val computer = args.firstOrNull { value -> value != null && hasGetPackagesForUid(value.javaClass) }
        val callers = if (computer != null) packagesForUid(computer, callingUid)
            else packageNamesFromCallerSetting(adapter.callerSettingIndex?.let(args::getOrNull))
        if (callers.isEmpty() || callers.none { it in current.hiddenFromApps }) return@Hooker chain.proceed()

        val target = packageNameFromState(args.getOrNull(adapter.targetIndex)) ?: return@Hooker chain.proceed()
        if (target in callers || target == MANAGER_PACKAGE || target !in current.allSelectedPackages) {
            return@Hooker chain.proceed()
        }

        visibilityHits.incrementAndGet()
        diagnostic("SYSTEM_VISIBILITY_FILTER uid=$callingUid caller=${callers.sorted()} target=$target scopes=${current.visibilityCompat.scopes.map { it.name }.sorted()}")
        true
    }

    private fun packageNamesFromCallerSetting(value: Any?): Set<String> {
        if (value == null) return emptySet()
        packageNameFromState(value)?.let { return setOf(it) }
        val result = linkedSetOf<String>()
        callerPackageFields(value.javaClass).forEach { field ->
            runCatching {
                when (val nested = field.get(value)) {
                    is Array<*> -> nested.asSequence()
                    is Collection<*> -> nested.asSequence()
                    is Map<*, *> -> nested.values.asSequence()
                    else -> emptySequence()
                }.mapNotNull(::packageNameFromState).forEach(result::add)
            }
        }
        return result
    }

    private fun callerPackageFields(clazz: Class<*>): List<Field> = callerPackageFieldsCache.computeIfAbsent(clazz) {
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { field ->
                val name = field.name.lowercase()
                "package" in name || "packages" in name
            }
            .take(12)
            .onEach { it.isAccessible = true }
            .toList()
    }

    private fun packagesForUidMethod(clazz: Class<*>): Method? = packagesForUidCache.computeIfAbsent(clazz) {
        MethodAccessor(
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .firstOrNull { candidate ->
                    candidate.name == "getPackagesForUid" && candidate.parameterTypes.size == 1 &&
                        candidate.parameterTypes[0] == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }
        )
    }.method

    private fun hasGetPackagesForUid(clazz: Class<*>): Boolean = packagesForUidMethod(clazz) != null

    private fun packagesForUid(computer: Any, uid: Int): Set<String> {
        val method = packagesForUidMethod(computer.javaClass) ?: return emptySet()
        val identity = Binder.clearCallingIdentity()
        return try {
            when (val result = method.invoke(computer, uid)) {
                is Array<*> -> result.filterIsInstance<String>().toSet()
                is Collection<*> -> result.filterIsInstance<String>().toSet()
                else -> emptySet()
            }
        } catch (failure: Throwable) {
            diagnostic("SYSTEM_VISIBILITY_CALLER_FAILED uid=$uid error=${failure.javaClass.name}")
            emptySet()
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun verifiedManagerAppIdForProbe(
        chain: XposedInterface.Chain,
        callerUid: Int,
    ): Int? {
        if (callerUid < 0) return null
        val candidates = buildList {
            chain.thisObject?.let(::add)
            chain.args.filterNotNull().forEach(::add)
        }
        val owner = candidates.firstOrNull { value ->
            hasGetPackagesForUid(value.javaClass) &&
                MANAGER_PACKAGE in packagesForUid(value, callerUid)
        }
        if (owner == null) {
            record("CONFIG_PROBE_IDENTITY uid=$callerUid verified=false")
            return null
        }
        val appId = callerUid % PER_USER_RANGE
        return appId.takeIf(ManagerIdentity::valid)?.also {
            if (tracedActions.add("config_identity:$callerUid")) {
                record("CONFIG_PROBE_IDENTITY uid=$callerUid appId=$it verified=true")
            }
        }
    }

    @Synchronized
    private fun beginRuntimeTransfer(
        intent: Intent,
        callerUid: Int,
        managerAppId: Int,
    ): Boolean {
        val transferId = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val digest = intent.getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
        val revision = intent.getLongExtra(RuntimeProtocol.EXTRA_REVISION, -1L)
        val totalChunks = intent.getIntExtra(RuntimeProtocol.EXTRA_TOTAL_CHUNKS, -1)
        val totalChars = intent.getIntExtra(RuntimeProtocol.EXTRA_TOTAL_CHARS, -1)
        if (
            !RuntimeProtocol.validTransferId(transferId) ||
            !RuntimeProtocol.validDigest(digest) ||
            revision < 0L ||
            totalChunks !in 1..RuntimeProtocol.MAX_CONFIG_CHUNKS ||
            totalChars !in 1..RuleRepository.MAX_BACKUP_CHARS
        ) {
            record(
                "CONFIG_PUSH_REJECT stage=begin uid=$callerUid transfer=${transferId ?: "none"} " +
                    "chunks=$totalChunks chars=$totalChars revision=$revision"
            )
            return false
        }

        incomingTransfer = RuntimeTransfer(
            callerUid = callerUid,
            managerAppId = managerAppId,
            id = requireNotNull(transferId),
            digest = requireNotNull(digest),
            revision = revision,
            totalChunks = totalChunks,
            totalChars = totalChars,
            startedAt = SystemClock.elapsedRealtime(),
            chunks = arrayOfNulls(totalChunks),
        )
        record(
            "CONFIG_PUSH_BEGIN uid=$callerUid transfer=$transferId chunks=$totalChunks " +
                "chars=$totalChars revision=$revision digest=$digest"
        )
        return true
    }

    @Synchronized
    private fun appendRuntimeChunk(
        intent: Intent,
        callerUid: Int,
    ): Boolean {
        val transfer = incomingTransfer ?: return false
        if (
            transfer.callerUid != callerUid ||
            SystemClock.elapsedRealtime() - transfer.startedAt > CONFIG_TRANSFER_TIMEOUT_MS
        ) {
            incomingTransfer = null
            record("CONFIG_PUSH_REJECT stage=chunk uid=$callerUid reason=owner_or_timeout")
            return false
        }
        val transferId = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val index = intent.getIntExtra(RuntimeProtocol.EXTRA_CHUNK_INDEX, -1)
        val chunk = intent.getStringExtra(RuntimeProtocol.EXTRA_CONFIG_CHUNK)
        if (
            transferId != transfer.id ||
            index !in transfer.chunks.indices ||
            chunk == null ||
            chunk.length > RuntimeProtocol.CONFIG_CHUNK_CHARS
        ) {
            record(
                "CONFIG_PUSH_REJECT stage=chunk uid=$callerUid transfer=${transferId ?: "none"} index=$index"
            )
            return false
        }
        transfer.chunks[index] = chunk
        return true
    }

    @Synchronized
    private fun commitRuntimeTransfer(
        intent: Intent,
        callerUid: Int,
        managerAppId: Int,
    ): Boolean {
        val transfer = incomingTransfer ?: return false
        incomingTransfer = null

        val transferId = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val expectedDigest = intent.getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
        val revision = intent.getLongExtra(RuntimeProtocol.EXTRA_REVISION, -1L)
        if (
            transfer.callerUid != callerUid ||
            transfer.managerAppId != managerAppId ||
            transferId != transfer.id ||
            expectedDigest != transfer.digest ||
            revision != transfer.revision ||
            SystemClock.elapsedRealtime() - transfer.startedAt > CONFIG_TRANSFER_TIMEOUT_MS ||
            transfer.chunks.any { it == null }
        ) {
            record(
                "CONFIG_PUSH_REJECT stage=commit uid=$callerUid transfer=${transferId ?: "none"} " +
                    "revision=$revision reason=metadata_or_chunks"
            )
            return false
        }

        val encoded = buildString(transfer.totalChars) {
            transfer.chunks.forEach { append(requireNotNull(it)) }
        }
        if (encoded.length != transfer.totalChars) {
            record(
                "CONFIG_PUSH_REJECT stage=commit uid=$callerUid transfer=${transfer.id} " +
                    "reason=length expected=${transfer.totalChars} actual=${encoded.length}"
            )
            return false
        }
        val actualDigest = RuntimeProtocol.digest(encoded)
        if (actualDigest != transfer.digest) {
            record(
                "CONFIG_PUSH_REJECT stage=commit uid=$callerUid transfer=${transfer.id} " +
                    "reason=digest expected=${transfer.digest} actual=$actualDigest"
            )
            return false
        }

        val applied = runCatching {
            applyAtomicConfig(
                encoded = encoded,
                reason = "runtime push",
                source = "probe-v2",
                expectedDigest = actualDigest,
                expectedManagerAppId = managerAppId,
            )
        }.onFailure {
            record(
                "CONFIG_PUSH_REJECT stage=commit uid=$callerUid transfer=${transfer.id} " +
                    "reason=${it.javaClass.name}"
            )
        }.getOrDefault(false)

        if (applied) {
            runtimeTransportActive = true
            appliedRuntimeRevision = transfer.revision
            record(
                "CONFIG_PUSH_APPLIED uid=$callerUid transfer=${transfer.id} revision=${transfer.revision} " +
                    "digest=$actualDigest rules=${snapshot.configured.size}"
            )
        }
        return applied
    }

    private fun packageNameAccessor(clazz: Class<*>): PackageNameAccessor = packageNameAccessorCache.computeIfAbsent(clazz) {
        val classes = generateSequence(clazz as Class<*>?) { it.superclass }.toList()
        val getter = classes.asSequence().flatMap { current -> current.declaredMethods.asSequence() }
            .firstOrNull { method ->
                method.parameterTypes.isEmpty() && method.returnType == String::class.java &&
                    method.name in setOf("getPackageName", "getName")
            }?.apply { isAccessible = true }
        val field = classes.asSequence().flatMap { current -> current.declaredFields.asSequence() }
            .firstOrNull { it.type == String::class.java && it.name in setOf("mName", "name", "packageName", "mPackageName") }
            ?.apply { isAccessible = true }
        PackageNameAccessor(getter, field)
    }

    private fun packageNameFromState(value: Any?): String? {
        if (value == null || value is Number || value is Boolean || value is ClassLoader) return null
        if (value is String) return value.takeIf(::looksLikePackageName)
        val accessor = packageNameAccessor(value.javaClass)
        runCatching {
            (accessor.getter?.invoke(value) as? String)?.takeIf(::looksLikePackageName)
        }.getOrNull()?.let { return it }
        return runCatching {
            (accessor.field?.get(value) as? String)?.takeIf(::looksLikePackageName)
        }.getOrNull()
    }

    private fun looksLikePackageName(value: String): Boolean =
        value.length in 3..255 && '.' in value && value.none { it.isWhitespace() || it.isISOControl() }

    private fun installResolverClientHooks(classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName("android.app.ApplicationPackageManager", false, classLoader)
        }.getOrElse {
            record("RESOLVER_CLASS_UNAVAILABLE error=${it.javaClass.name}")
            return
        }
        var installed = 0
        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->
            if (installHook(method, Layer.RESOLVER)) installed++
        }
        record("RESOLVER_HOOKS new=$installed total=${installedMethods.size}")
        installFinalOrderingHooks(classLoader)
    }

    private fun installFinalOrderingHooks(loader: ClassLoader) {
        listOf("com.android.internal.app.ResolverListAdapter", "com.android.internal.app.ChooserListAdapter",
            "com.android.intentresolver.ResolverListAdapter", "com.android.intentresolver.ChooserListAdapter").forEach { name ->
            val clazz = runCatching { Class.forName(name, false, loader) }.getOrElse {
                record("ORDER_CLASS_UNAVAILABLE class=$name")
                return@forEach
            }
            runCatching {
                val methods = clazz.declaredMethods.filter { method ->
                    method.name == "processSortedList" && method.parameterTypes.size == 2 &&
                        method.parameterTypes[0] == List::class.java &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                if (methods.isEmpty()) record("ORDER_METHOD_UNAVAILABLE class=$name signature=processSortedList_List_boolean")
                methods.forEach { method ->
                    val key = "ORDER#${method.toGenericString()}"
                    if (installedMethods.add(key)) {
                        try {
                            hook(method).setId("ic-final-order").intercept(orderHooker())
                            record("ORDER_HOOK_INSTALLED method=${method.toGenericString()}")
                        } catch (failure: Throwable) {
                            installedMethods.remove(key)
                            record("ORDER_HOOK_FAILED error=${failure.javaClass.name}")
                        }
                    }
                }
            }.onFailure { record("ORDER_DISCOVERY_FAILED class=$name error=${it.javaClass.name}") }
        }
        runCatching {
            val method = Class.forName("android.widget.BaseAdapter", false, loader)
                .getDeclaredMethod("notifyDataSetChanged")
            val key = "ALPHA#${method.toGenericString()}"
            if (installedMethods.add(key)) {
                try {
                    hook(method).setId("ic-alpha-order").intercept(alphabeticalOrderHooker())
                    record("ORDER_HOOK_INSTALLED stage=alpha method=${method.toGenericString()}")
                } catch (failure: Throwable) {
                    installedMethods.remove(key)
                    throw failure
                }
            }
        }.onFailure { record("ORDER_HOOK_FAILED stage=alpha error=${it.javaClass.name}") }
        recordOrderingCapability()
    }

    private fun recordOrderingCapability() = record("ORDER_CAPABILITY ranked=${installedMethods.any { it.startsWith("ORDER#") }} alphaBoundary=${installedMethods.any { it.startsWith("ALPHA#") }} hits=${orderingHits.get()}")

    private fun adapterKind(receiver: Any): IntentKind? {
        val intent = OrderingAccess.targetIntent(receiver) as? Intent ?: return null
        val effective = intent.selector ?: intent
        val context = runCatching { OrderingAccess.field(receiver, "mContext") as? Context }.getOrNull()
        val mime = effective.type ?: runCatching {
            context?.let { effective.resolveTypeIfNeeded(it.contentResolver) }
        }.getOrNull()
        return effective.intentKind(mime)
    }

    private fun adapterOpenPreset(receiver: Any, kind: IntentKind): OpenPreset? {
        if (kind != IntentKind.OPEN) return null
        val intent = OrderingAccess.targetIntent(receiver) as? Intent ?: return null
        val effective = intent.selector ?: intent
        val context = runCatching { OrderingAccess.field(receiver, "mContext") as? Context }.getOrNull()
        val mime = effective.type ?: runCatching { context?.let { effective.resolveTypeIfNeeded(it.contentResolver) } }.getOrNull()
        val data = effective.data
        return matchOpenPreset(kind, mime, data?.scheme, data?.lastPathSegment ?: data?.path)
    }

    private fun effectivePriorities(
        kind: IntentKind,
        preset: OpenPreset?,
        browserHost: String?,
        current: RuleSnapshot
    ): List<String> {
        val typed = if (kind == IntentKind.OPEN && preset != null) current.openTypes.priorities[preset].orEmpty() else emptyList()
        if (typed.isNotEmpty()) return typed
        val hostPriority = if (kind == IntentKind.DEEP_LINK && browserHost != null) {
            current.browserLinks.priority(browserHost)
        } else emptyList()
        return if (hostPriority.isNotEmpty()) hostPriority else current.priorities.apps[kind].orEmpty()
    }

    private fun candidateKind(baseKind: IntentKind, info: ResolveInfo): IntentKind =
        if (baseKind == IntentKind.BROWSER) info.webTargetKind() else baseKind

    private fun hasEffectivePriorities(
        kind: IntentKind,
        preset: OpenPreset?,
        browserHost: String?,
        current: RuleSnapshot
    ): Boolean {
        if (kind != IntentKind.BROWSER) return effectivePriorities(kind, preset, browserHost, current).isNotEmpty()
        return current.priorities.apps[IntentKind.BROWSER].orEmpty().isNotEmpty() ||
            effectivePriorities(IntentKind.DEEP_LINK, null, browserHost, current).isNotEmpty()
    }

    private fun adapterBrowserHost(receiver: Any, kind: IntentKind): String? {
        if (kind != IntentKind.BROWSER) return null
        val intent = OrderingAccess.targetIntent(receiver) as? Intent ?: return null
        val effective = intent.selector ?: intent
        return normalizeBrowserHost(effective.data?.host)
    }

    private fun resolveInfoForOrder(item: Any?, stage: String): ResolveInfo {
        requireNotNull(item)
        return if (item is ResolveInfo) item else if (stage == "alpha") {
            OrderingAccess.call(item, "getResolveInfo") as ResolveInfo
        } else {
            item.javaClass.getMethod("getResolveInfoAt", Int::class.javaPrimitiveType)
                .invoke(item, 0) as ResolveInfo
        }
    }

    private fun resolverMetadataDigest(info: ResolveInfo): String? =
        info.activityInfo?.metaData
            ?.getString(RuntimeProtocol.META_POLICY_DIGEST)
            ?.takeIf(RuntimeProtocol::validDigest)

    private fun hasResolverPolicyMetadata(items: List<*>, stage: String): Boolean =
        items.asSequence().mapNotNull { item ->
            runCatching { resolveInfoForOrder(item, stage) }.getOrNull()
        }.any { resolverMetadataDigest(it) != null }

    private fun orderItemsFromMetadata(
        items: List<*>,
        kind: IntentKind,
        stage: String,
        fixedPackages: Set<String> = emptySet(),
    ): List<*> {
        if (items.size < 2) return items
        val infos = items.map { resolveInfoForOrder(it, stage) }
        val positions = items.indices.toMutableList()

        fun reorderSubset(targetKind: IntentKind) {
            val movable = items.indices.filter { index ->
                candidateKind(kind, infos[index]) == targetKind &&
                    infos[index].activityInfo.packageName !in fixedPackages
            }
            if (movable.size < 2) return
            movable.groupBy { index ->
                requireNotNull(infos[index].activityInfo.applicationInfo).uid / PER_USER_RANGE
            }.values.forEach { profilePositions ->
                val sorted = profilePositions.sortedBy { index ->
                    val meta = infos[index].activityInfo.metaData
                    if (meta?.containsKey(RuntimeProtocol.META_PRIORITY_RANK) == true) {
                        meta.getInt(RuntimeProtocol.META_PRIORITY_RANK, Int.MAX_VALUE)
                    } else {
                        Int.MAX_VALUE
                    }
                }
                profilePositions.forEachIndexed { orderIndex, position ->
                    positions[position] = sorted[orderIndex]
                }
            }
        }

        if (kind == IntentKind.BROWSER) {
            reorderSubset(IntentKind.BROWSER)
            reorderSubset(IntentKind.DEEP_LINK)
        } else {
            reorderSubset(kind)
        }

        val changed = positions != items.indices.toList()
        val digest = infos.asSequence().mapNotNull(::resolverMetadataDigest).firstOrNull()
        diagnostic(
            "ORDER_RESULT stage=$stage kind=$kind count=${items.size} changed=$changed " +
                "source=system_metadata digest=${digest ?: "none"}"
        )
        return if (changed) positions.map { items[it] } else items
    }

    private fun orderItems(
        items: List<*>,
        kind: IntentKind,
        current: RuleSnapshot,
        stage: String,
        fixedPackages: Set<String> = emptySet(),
        preset: OpenPreset? = null,
        browserHost: String? = null
    ): List<*> {
        if (items.size < 2) return items
        val infos = items.map { resolveInfoForOrder(it, stage) }
        val positions = items.indices.toMutableList()

        fun reorderSubset(targetKind: IntentKind, priorities: List<String>) {
            if (priorities.isEmpty()) return
            val movable = items.indices.filter { index ->
                candidateKind(kind, infos[index]) == targetKind &&
                    infos[index].activityInfo.packageName !in fixedPackages
            }
            if (movable.size < 2) return
            val sorted = prioritizeApps(
                movable,
                priorities,
                { index -> requireNotNull(infos[index].activityInfo).packageName },
                { index -> requireNotNull(infos[index].activityInfo.applicationInfo).uid / PER_USER_RANGE }
            )
            movable.forEachIndexed { orderIndex, position -> positions[position] = sorted[orderIndex] }
        }

        if (kind == IntentKind.BROWSER) {
            reorderSubset(IntentKind.BROWSER, current.priorities.apps[IntentKind.BROWSER].orEmpty())
            reorderSubset(
                IntentKind.DEEP_LINK,
                effectivePriorities(IntentKind.DEEP_LINK, null, browserHost, current)
            )
        } else {
            reorderSubset(kind, effectivePriorities(kind, preset, browserHost, current))
        }

        val changed = positions != items.indices.toList()
        diagnostic(
            "ORDER_RESULT stage=" + stage + " kind=" + kind + " count=" + items.size +
                " changed=" + changed + " digest=" + current.digest
        )
        return if (changed) positions.map { items[it] } else items
    }

    private fun orderHooker() = XposedInterface.Hooker { chain ->
        val replacement = runCatching {
            val receiver = chain.thisObject ?: return@runCatching null
            val kind = adapterKind(receiver) ?: run {
                diagnostic("ORDER_SKIP reason=unclassified_intent")
                return@runCatching null
            }
            val items = chain.args[0] as? List<*> ?: return@runCatching null

            val ordered = if (hasResolverPolicyMetadata(items, "ranked")) {
                orderItemsFromMetadata(items, kind, "ranked")
            } else {
                pollPreferences()
                val current = snapshot
                if (current.displayMode == DisplayMode.SHOW_ALL) {
                    diagnostic("ORDER_SKIP reason=show_all")
                    return@runCatching null
                }
                val preset = adapterOpenPreset(receiver, kind)
                val browserHost = adapterBrowserHost(receiver, kind)
                if (!hasEffectivePriorities(kind, preset, browserHost, current)) {
                    diagnostic("ORDER_SKIP kind=$kind reason=no_priorities")
                    return@runCatching null
                }
                orderItems(
                    items,
                    kind,
                    current,
                    "ranked",
                    preset = preset,
                    browserHost = browserHost,
                )
            }
            if (ordered === items) null else chain.args.toTypedArray().also { it[0] = ordered }
        }.getOrElse {
            diagnostic("ORDER_FAILED error=${it.javaClass.name}")
            null
        }
        val result = if (replacement == null) chain.proceed() else chain.proceed(replacement)
        if (replacement != null) {
            orderingHits.incrementAndGet()
            diagnostic("ORDER_DELIVERED stage=ranked")
        }
        result
    }

    private fun alphabeticalOrderHooker() = XposedInterface.Hooker { chain ->
        val receiver = chain.thisObject
        if (receiver != null && OrderingAccess.isChooser(receiver)) runCatching {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                diagnostic("ORDER_SKIP stage=alpha reason=not_main_thread")
                return@runCatching
            }
            val kind = adapterKind(receiver) ?: return@runCatching
            val items = OrderingAccess.field(receiver, "mSortedList")
            if (items == null || items.javaClass != java.util.ArrayList::class.java) {
                diagnostic("ORDER_SKIP stage=alpha reason=unsupported_backing_list")
                return@runCatching
            }
            @Suppress("UNCHECKED_CAST")
            val list = items as java.util.ArrayList<Any?>
            val callerTargets = OrderingAccess.field(receiver, "mCallerTargets") as List<*>
            val fixed = callerTargets.map { target ->
                val info = OrderingAccess.call(requireNotNull(target), "getResolveInfo") as ResolveInfo
                info.activityInfo.packageName
            }.toSet()

            val ordered = if (hasResolverPolicyMetadata(list, "alpha")) {
                orderItemsFromMetadata(list, kind, "alpha", fixed)
            } else {
                pollPreferences()
                if (snapshot.displayMode == DisplayMode.SHOW_ALL) {
                    diagnostic("ORDER_SKIP stage=alpha reason=show_all")
                    return@runCatching
                }
                val preset = adapterOpenPreset(receiver, kind)
                val browserHost = adapterBrowserHost(receiver, kind)
                orderItems(list, kind, snapshot, "alpha", fixed, preset, browserHost)
            }
            if (ordered !== list) {
                ordered.forEachIndexed { index, item -> list[index] = item }
                orderingHits.incrementAndGet()
                diagnostic("ORDER_DELIVERED stage=alpha kind=$kind")
            }
        }.onFailure { diagnostic("ORDER_FAILED stage=alpha error=${it.javaClass.name}") }
        chain.proceed()
    }

    private fun installHook(method: Method, layer: Layer): Boolean {
        val key = "${method.declaringClass.name}#${method.toGenericString()}@$layer"
        if (!installedMethods.add(key)) return false
        return runCatching {
            method.isAccessible = true
            hook(method).setId("$HOOK_ID-${layer.name.lowercase()}").intercept(queryHooker(layer))
            record("HOOK_INSTALLED layer=$layer method=${method.toGenericString()}")
            true
        }.getOrElse {
            installedMethods.remove(key)
            record("HOOK_FAILED layer=$layer method=${method.toGenericString()} error=${it.javaClass.name}")
            Log.w(TAG, "Unable to hook ${method.declaringClass.name}.${method.name}", it)
            false
        }
    }

    private fun queryHooker(layer: Layer) = XposedInterface.Hooker { chain ->
        if (queryInProgress.get() == true) return@Hooker chain.proceed()
        val callerUid = if (layer == Layer.SYSTEM) Binder.getCallingUid() else -1
        queryInProgress.set(true)
        try {
            if (layer == Layer.SYSTEM) pollPreferences()
            val original = if (layer == Layer.RESOLVER) {
                val intentIndex = chain.args.indexOfFirst { it is Intent }
                if (intentIndex >= 0) {
                    val replacement = chain.args.toTypedArray()
                    replacement[intentIndex] = Intent(chain.args[intentIndex] as Intent).apply {
                        putExtra(RuntimeProtocol.EXTRA_RESOLVER_REQUEST, true)
                    }
                    chain.proceed(replacement)
                } else {
                    chain.proceed()
                }
            } else {
                chain.proceed()
            }
            try {
                processQuery(chain, original, layer, callerUid)
            } catch (failure: Throwable) {
                diagnostic("FILTER_FAILED layer=$layer error=${failure.javaClass.name}; keeping original")
                original
            }
        } finally {
            queryInProgress.remove()
        }
    }

    private fun resolveQueryMime(
        chain: XposedInterface.Chain,
        intent: Intent,
        layer: Layer,
        systemResolvedType: String?
    ): String? {
        intent.type?.let { return it }
        if (layer == Layer.SYSTEM) return systemResolvedType
        val data = intent.data ?: return null
        if (data.scheme != "content") return null
        val receiver = chain.thisObject ?: return null
        val context = runCatching { OrderingAccess.field(receiver, "mContext") as? Context }.getOrNull() ?: return null
        return runCatching { intent.resolveTypeIfNeeded(context.contentResolver) }
            .onFailure { diagnostic("MIME_RESOLVE_FAILED layer=$layer error=${it.javaClass.name}") }
            .getOrNull()
    }

    private fun handleRuntimeProbe(
        chain: XposedInterface.Chain,
        original: Any?,
        callerUid: Int,
        intent: Intent,
    ): Any? {
        val protocol = intent.getIntExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, 1)
        val operation = intent.getStringExtra(RuntimeProtocol.EXTRA_OPERATION)
        val expectedDigest = intent
            .getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
            ?.takeIf(RuntimeProtocol::validDigest)

        val verifiedManagerAppId = verifiedManagerAppIdForProbe(chain, callerUid)
            ?: return original

        if (operation == RuntimeProtocol.OP_CHUNK) {
            if (protocol != RuntimeProtocol.VERSION) {
                record("CONFIG_PUSH_REJECT stage=chunk uid=$callerUid reason=protocol protocol=$protocol")
                return original
            }
            appendRuntimeChunk(intent, callerUid)
            return original
        }

        when (operation) {
            RuntimeProtocol.OP_BEGIN -> {
                if (protocol == RuntimeProtocol.VERSION) {
                    beginRuntimeTransfer(intent, callerUid, verifiedManagerAppId)
                } else {
                    record("CONFIG_PUSH_REJECT stage=begin uid=$callerUid reason=protocol protocol=$protocol")
                }
                return original
            }
            RuntimeProtocol.OP_COMMIT -> {
                if (protocol != RuntimeProtocol.VERSION ||
                    !commitRuntimeTransfer(intent, callerUid, verifiedManagerAppId)
                ) {
                    return original
                }
            }
            null -> Unit
            else -> {
                record("CONFIG_PUSH_REJECT stage=operation uid=$callerUid operation=$operation")
                return original
            }
        }

        val applied = snapshot
        val digestMatches = expectedDigest == null || applied.digest == expectedDigest
        if (
            ManagerIdentity.matches(callerUid, applied.managerAppId) &&
            applied.digest.isNotEmpty() &&
            digestMatches
        ) {
            val result = extractListResult(original) ?: return original
            val ack = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = RuntimeProtocol.PACKAGE
                    name = RuntimeProtocol.COMPONENT
                    applicationInfo = ApplicationInfo().apply {
                        packageName = RuntimeProtocol.PACKAGE
                        uid = callerUid
                    }
                }
                nonLocalizedLabel =
                    "${BuildConfig.HOOK_COMPAT_VERSION_CODE}:${applied.digest}:" +
                        "${queryHits.get()}:${visibilityHits.get()}:${orderingHits.get()}:" +
                        "$COMPONENT_DISCOVERY_PROTOCOL:${RuntimeProtocol.VERSION}:$appliedRuntimeRevision"
            }
            record(
                "CONFIG_ACK moduleVersion=${BuildConfig.VERSION_CODE} " +
                    "hookCompat=${BuildConfig.HOOK_COMPAT_VERSION_CODE} digest=${applied.digest} " +
                    "queryHits=${queryHits.get()} visibilityHits=${visibilityHits.get()} " +
                    "orderHits=${orderingHits.get()} callerUid=$callerUid " +
                    "runtimeProtocol=${RuntimeProtocol.VERSION} revision=$appliedRuntimeRevision"
            )
            return result.rebuild(listOf(ack))
        }

        record(
            "CONFIG_ACK_SKIP callerUid=$callerUid managerAppId=${applied.managerAppId} " +
                "expected=${expectedDigest ?: "none"} actual=${applied.digest.ifEmpty { "none" }} " +
                "identityMatch=${ManagerIdentity.matches(callerUid, applied.managerAppId)}"
        )
        return original
    }

    private fun annotateResolverPolicy(
        kind: IntentKind,
        values: List<*>,
        mimeType: String?,
        scheme: String?,
        fileNameOrPath: String?,
        browserHost: String?,
    ): List<*>? {
        if (values.isEmpty()) return null
        val current = snapshot
        if (current.digest.isEmpty()) return null

        val preset = matchOpenPreset(kind, mimeType, scheme, fileNameOrPath)
        val typedIds = if (kind == IntentKind.OPEN && preset != null) {
            current.openTypes.rules[preset].orEmpty()
        } else {
            emptySet()
        }
        val normalizedHost = if (kind == IntentKind.BROWSER) {
            normalizeBrowserHost(browserHost)
        } else {
            null
        }
        val deepLinkIds = normalizedHost?.let {
            current.browserLinks.rules[it].orEmpty()
        }.orEmpty()

        val policies = values.map { value ->
            val info = value as? ResolveInfo
                ?: return@map ResolverPolicy(true, Int.MAX_VALUE, null)
            val activity = info.activityInfo
                ?: return@map ResolverPolicy(true, Int.MAX_VALUE, null)
            if (current.displayMode == DisplayMode.SHOW_ALL) {
                return@map ResolverPolicy(true, Int.MAX_VALUE, null)
            }

            val effectiveKind = candidateKind(kind, info)
            val scopedTypedIds = if (effectiveKind == IntentKind.OPEN) typedIds else emptySet()
            val scopedDeepIds = if (effectiveKind == IntentKind.DEEP_LINK) deepLinkIds else emptySet()
            val hasSelection = current.hasSelection(effectiveKind) ||
                scopedTypedIds.isNotEmpty() ||
                scopedDeepIds.isNotEmpty()
            val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
                activity.packageName,
                activity.name,
                activity.targetActivity,
            )
            val candidateId = "${effectiveKind.name}|${activity.packageName}|$canonicalClass"
            val selected = candidateId in current.configured ||
                candidateId in scopedTypedIds ||
                candidateId in scopedDeepIds
            val include = current.displayMode.includes(selected, hasSelection)

            val priorities = effectivePriorities(
                effectiveKind,
                if (effectiveKind == IntentKind.OPEN) preset else null,
                if (effectiveKind == IntentKind.DEEP_LINK) normalizedHost else null,
                current,
            )
            val priorityIndex = priorities.indexOf(activity.packageName)
            val title = current.priorities.titles[candidateId]
            ResolverPolicy(
                include = include,
                rank = if (priorityIndex >= 0) priorityIndex else Int.MAX_VALUE,
                title = title,
            )
        }

        val resolveCount = values.count { it is ResolveInfo && it.activityInfo != null }
        if (resolveCount == 0) return null
        val includedCount = policies.count { it.include }
        val restoreAll = FilterPolicy.restoreEmpty(kind.name, values.size, includedCount)

        val annotated = values.mapIndexed { index, value ->
            val info = value as? ResolveInfo ?: return@mapIndexed value
            val activity = info.activityInfo ?: return@mapIndexed value
            val policy = if (restoreAll) {
                ResolverPolicy(true, Int.MAX_VALUE, null)
            } else {
                policies[index]
            }
            ResolveInfo(info).apply {
                activityInfo = ActivityInfo(activity).apply {
                    metaData = Bundle(activity.metaData ?: Bundle()).apply {
                        putString(RuntimeProtocol.META_POLICY_DIGEST, current.digest)
                        putBoolean(RuntimeProtocol.META_INCLUDE, policy.include)
                        if (policy.rank == Int.MAX_VALUE) {
                            remove(RuntimeProtocol.META_PRIORITY_RANK)
                        } else {
                            putInt(RuntimeProtocol.META_PRIORITY_RANK, policy.rank)
                        }
                    }
                }
                policy.title?.let { nonLocalizedLabel = it }
            }
        }

        diagnostic(
            "RESOLVER_POLICY_METADATA kind=$kind count=${values.size} included=$includedCount " +
                "restoreAll=$restoreAll digest=${current.digest}"
        )
        return annotated
    }

    private fun processQuery(
        chain: XposedInterface.Chain,
        original: Any?,
        layer: Layer,
        callerUid: Int,
    ): Any? {
        val outerIntent = chain.args.firstOrNull { it is Intent } as? Intent
        val intent = outerIntent?.selector ?: outerIntent
        if (
            layer == Layer.SYSTEM &&
            outerIntent?.action == RuntimeProtocol.ACTION &&
            outerIntent.`package` == RuntimeProtocol.PACKAGE &&
            outerIntent.selector == null
        ) {
            return handleRuntimeProbe(chain, original, callerUid, outerIntent)
        }

        val moduleAppId = snapshot.managerAppId
        if (layer == Layer.SYSTEM && !ManagerIdentity.valid(moduleAppId)) {
            diagnostic("FILTER_PAUSED reason=manager_identity_unknown open_module_app_to_sync")
            return original
        }
        if (ManagerIdentity.matches(callerUid, moduleAppId)) {
            if (tracedActions.add("manager_bypass")) {
                record("MANAGER_QUERY_BYPASS uid=$callerUid")
            }
            return original
        }

        val intentIndex = chain.args.indexOfFirst { it is Intent }
        val systemResolvedType =
            if (layer == Layer.SYSTEM) chain.args.getOrNull(intentIndex + 1) as? String else null
        val resolvedType = intent?.let {
            resolveQueryMime(chain, it, layer, systemResolvedType)
        }
        val kind = intent?.intentKind(resolvedType)
        val explicit = intent?.component != null || intent?.`package` != null ||
            outerIntent?.component != null || outerIntent?.`package` != null
        val privilegedSystem =
            layer == Layer.SYSTEM && !FilterPolicy.ordinaryAppCaller(callerUid)
        val resolverSystemRequest =
            privilegedSystem &&
                outerIntent?.getBooleanExtra(RuntimeProtocol.EXTRA_RESOLVER_REQUEST, false) == true

        if (intent != null && kind != null) {
            if (!privilegedSystem) queryHits.incrementAndGet()
            val traceKey = "$layer|$kind|$callerUid"
            if (
                !explicit &&
                (callerUid >= 10_000 || layer == Layer.RESOLVER) &&
                snapshot.diagnostic &&
                tracedActions.size < 128 &&
                tracedActions.add(traceKey)
            ) {
                diagnostic(
                    "FIRST_QUERY_STACK layer=$layer kind=$kind callerUid=$callerUid action=${intent.action} " +
                        Throwable().stackTrace.take(14).joinToString(" <- ") {
                            "${it.className}.${it.methodName}"
                        }
                )
            }
            diagnostic(
                "QUERY layer=$layer kind=$kind action=${intent.action} mime=$resolvedType " +
                    "scheme=${intent.data?.scheme} " +
                    "ext=${safeExtension(intent.data?.lastPathSegment ?: intent.data?.path)} " +
                    "component=${intent.component?.flattenToShortString()} package=${intent.`package`} " +
                    "callerUid=$callerUid result=${original?.javaClass?.name} " +
                    "rules=${snapshot.configured.size} mode=${snapshot.displayMode}"
            )
        }
        if (intent == null || explicit) {
            if (kind != null) diagnostic("SKIP explicit_component_or_package")
            return original
        }
        if (kind == null) {
            if (intent.action == Intent.ACTION_VIEW) {
                diagnostic("SKIP_UNCLASSIFIED scheme=${intent.data?.scheme} mime=$resolvedType")
            }
            return original
        }

        val extracted = extractListResult(original) ?: run {
            diagnostic("skip $layer ${intent.action}: unsupported result ${original?.javaClass?.name}")
            return original
        }
        val data = intent.data

        if (privilegedSystem) {
            if (!resolverSystemRequest) {
                diagnostic("FILTER_SKIP reason=privileged_caller uid=$callerUid")
                return original
            }
            val annotated = annotateResolverPolicy(
                kind = kind,
                values = extracted.values,
                mimeType = resolvedType,
                scheme = data?.scheme,
                fileNameOrPath = data?.lastPathSegment ?: data?.path,
                browserHost = data?.host,
            ) ?: return original
            return runCatching { extracted.rebuild(annotated) }.getOrElse {
                diagnostic("RESOLVER_POLICY_REBUILD_FAILED error=${it.javaClass.name}")
                original
            }
        }

        if (layer == Layer.RESOLVER && hasResolverPolicyMetadata(extracted.values, "query")) {
            val filtered = extracted.values.filter { value ->
                val info = value as? ResolveInfo ?: return@filter true
                val meta = info.activityInfo?.metaData ?: return@filter true
                if (!RuntimeProtocol.validDigest(meta.getString(RuntimeProtocol.META_POLICY_DIGEST))) {
                    return@filter true
                }
                meta.getBoolean(RuntimeProtocol.META_INCLUDE, true)
            }
            val ordered = if (kind == IntentKind.PROCESS_TEXT) {
                orderItemsFromMetadata(filtered, kind, "query")
            } else {
                filtered
            }
            if (filtered.size != extracted.values.size || ordered !== filtered) {
                diagnostic(
                    "RESOLVER_POLICY_APPLIED kind=$kind before=${extracted.values.size} " +
                        "after=${ordered.size}"
                )
                return runCatching { extracted.rebuild(ordered) }.getOrElse {
                    diagnostic("RESOLVER_POLICY_REBUILD_FAILED error=${it.javaClass.name}")
                    original
                }
            }
            return original
        }

        val replacement = transform(
            kind,
            extracted.values,
            layer,
            callerUid,
            resolvedType,
            data?.scheme,
            data?.lastPathSegment ?: data?.path,
            data?.host,
        ) ?: return original
        return runCatching { extracted.rebuild(replacement) }.getOrElse {
            Log.e(TAG, "Failed to rebuild ${original?.javaClass?.name}; keeping original", it)
            original
        }
    }

    private fun safeExtension(fileNameOrPath: String?): String? {
        val clean = fileNameOrPath?.substringBefore('?')?.substringBefore('#')?.substringAfterLast('/') ?: return null
        val extension = clean.substringAfterLast('.', "").lowercase()
        return extension.takeIf { it.length in 1..16 && it.all { ch -> ch.isLetterOrDigit() } }
    }

    private fun isSelectedCandidate(kind: IntentKind, activity: ActivityInfo, current: RuleSnapshot, layer: Layer): Boolean {
        val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
            activity.packageName, activity.name, activity.targetActivity
        )
        if ("${kind.name}|${activity.packageName}|$canonicalClass" in current.configured) return true
        return false
    }

    private fun transform(
        kind: IntentKind,
        values: List<*>,
        layer: Layer,
        callerUid: Int,
        mimeType: String?,
        scheme: String?,
        fileNameOrPath: String?,
        browserHost: String?
    ): List<*>? {
        if (values.isEmpty()) {
            diagnostic("SKIP $layer $kind empty_input")
            return null
        }
        val current = snapshot
        if (current.displayMode == DisplayMode.SHOW_ALL) {
            diagnostic("NO_CHANGE $layer $kind size=${values.size} reason=show_all")
            return null
        }

        var changed = false
        var anySelection = false
        val preset = matchOpenPreset(kind, mimeType, scheme, fileNameOrPath)
        val typedIds = if (kind == IntentKind.OPEN && preset != null) {
            current.openTypes.rules[preset].orEmpty()
        } else emptySet()
        val normalizedHost = if (kind == IntentKind.BROWSER) normalizeBrowserHost(browserHost) else null
        val deepLinkIds = normalizedHost?.let { current.browserLinks.rules[it].orEmpty() }.orEmpty()

        val filtered = values.filter { value ->
            val info = value as? ResolveInfo ?: return@filter true
            val activity = info.activityInfo ?: return@filter true
            val effectiveKind = candidateKind(kind, info)
            if (FilterPolicy.sameCaller(callerUid, activity.applicationInfo?.uid ?: -1)) {
                diagnostic(
                    "KEEP_SAME_APP $layer $effectiveKind ${activity.packageName}/${activity.name} callerUid=$callerUid",
                    detail = true
                )
                return@filter true
            }

            val scopedTypedIds = if (effectiveKind == IntentKind.OPEN) typedIds else emptySet()
            val scopedDeepIds = if (effectiveKind == IntentKind.DEEP_LINK) deepLinkIds else emptySet()
            val hasSelection = current.hasSelection(effectiveKind) ||
                scopedTypedIds.isNotEmpty() || scopedDeepIds.isNotEmpty()
            anySelection = anySelection || hasSelection
            if (!hasSelection && current.displayMode != DisplayMode.SHOW_SELECTED) return@filter true

            val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
                activity.packageName, activity.name, activity.targetActivity
            )
            val candidateId = "${effectiveKind.name}|${activity.packageName}|$canonicalClass"
            val selected = candidateId in current.configured ||
                candidateId in scopedTypedIds || candidateId in scopedDeepIds
            diagnostic(
                "CANDIDATE $layer $effectiveKind preset=$preset host=$normalizedHost " +
                    "${activity.packageName}/${activity.name} target=${activity.targetActivity} " +
                    "canonical=$canonicalClass selected=$selected",
                detail = true
            )
            current.displayMode.includes(selected, hasSelection).also {
                if (!it) changed = true
            }
        }

        if (FilterPolicy.restoreEmpty(kind.name, values.size, filtered.size)) {
            diagnostic("RESTORE_ALL $layer $kind before=${values.size} filtered=0")
            Log.w(TAG, "Refusing to empty $kind resolver; keeping Android result")
            return null
        }

        val ordered = if (kind == IntentKind.PROCESS_TEXT) runCatching {
            orderItems(filtered, kind, current, "text_query", preset = preset, browserHost = normalizedHost)
        }.getOrElse {
            diagnostic("ORDER_FAILED stage=text_query error=${it.javaClass.name}")
            filtered
        } else filtered
        if (ordered !== filtered) changed = true

        val (titled, titleCount) = runCatching {
            applyCustomTitles(kind, ordered, current)
        }.getOrElse { failure ->
            diagnostic("TITLE_FAILED kind=$kind error=${failure.javaClass.name}")
            ordered to 0
        }
        if (titleCount > 0) changed = true
        if (!changed) {
            diagnostic("NO_CHANGE $layer $kind size=${values.size} hasSelection=$anySelection preset=$preset")
            return null
        }
        diagnostic("$layer $kind preset=$preset: ${values.size} -> ${titled.size}")
        return titled
    }

    private fun applyCustomTitles(
        kind: IntentKind,
        values: List<*>,
        current: RuleSnapshot
    ): Pair<List<*>, Int> {
        if (current.priorities.titles.isEmpty()) return values to 0
        var replaced = 0
        val result = values.map { value ->
            val info = value as? ResolveInfo ?: return@map value
            val activity = info.activityInfo ?: return@map value
            val effectiveKind = candidateKind(kind, info)
            val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
                activity.packageName, activity.name, activity.targetActivity
            )
            val key = "${effectiveKind.name}|${activity.packageName}|$canonicalClass"
            val title = current.priorities.titles[key] ?: return@map value
            replaced++
            ResolveInfo(info).apply { nonLocalizedLabel = title }
        }
        if (replaced > 0) diagnostic("TITLES kind=$kind replaced=$replaced")
        return if (replaced > 0) result to replaced else values to 0
    }

    private fun extractListResult(original: Any?): ListResult? = when {
        original is List<*> -> ListResult(original) { it }
        original == null -> null
        original.javaClass.name.endsWith("ParceledListSlice") -> extractParceledListSlice(original)
        else -> null
    }

    private fun extractParceledListSlice(original: Any): ListResult? {
        val accessor = parceledListAccessorCache.computeIfAbsent(original.javaClass) { clazz ->
            val getList = clazz.getMethod("getList").apply { isAccessible = true }
            val constructor = clazz.getDeclaredConstructor(List::class.java).apply { isAccessible = true }
            ParceledListAccessor(getList, constructor)
        }
        val values = runCatching { accessor.getList.invoke(original) as? List<*> }.getOrNull() ?: return null
        return ListResult(values) { accessor.constructor.newInstance(it) }
    }

    @Synchronized private fun initializePreferences() {
        if (listenerRegistered) return
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
            listenerRegistered = true
        }.onFailure { Log.e(TAG, "Preference listener registration failed", it) }
        refreshRulesSafely("init")
    }

    private fun applyAtomicConfig(
        encoded: String,
        reason: String,
        source: String,
        expectedDigest: String?,
        expectedManagerAppId: Int? = null,
    ): Boolean {
        require(encoded.length <= RuleRepository.MAX_BACKUP_CHARS) {
            "Config too large"
        }
        val digest = RuntimeProtocol.digest(encoded)
        if (!expectedDigest.isNullOrBlank() && digest != expectedDigest) {
            record(
                "REMOTE_CONFIG_STALE reason=$reason source=$source " +
                    "expected=$expectedDigest actual=$digest length=${encoded.length}"
            )
            return false
        }
        if (lastEncodedConfig == encoded && snapshot.digest == digest) {
            if (
                expectedManagerAppId != null &&
                snapshot.managerAppId != expectedManagerAppId
            ) {
                record(
                    "CONFIG_IDENTITY_REJECT source=$source expectedAppId=$expectedManagerAppId " +
                        "actualAppId=${snapshot.managerAppId}"
                )
                return false
            }
            return true
        }

        val config = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(
            ModuleConfig.serializer(),
            encoded,
        ).validated()

        if (
            expectedManagerAppId != null &&
            config.managerAppId != expectedManagerAppId
        ) {
            record(
                "CONFIG_IDENTITY_REJECT source=$source expectedAppId=$expectedManagerAppId " +
                    "actualAppId=${config.managerAppId}"
            )
            return false
        }

        snapshot = RuleSnapshot(
            configured = config.rules.map { it.id }.toSet(),
            displayMode = config.mode,
            priorities = config.priorities,
            defaultOpen = config.defaultOpen,
            openTypes = config.openTypes,
            browserLinks = config.browserLinks,
            diagnostic = config.diagnostic,
            managerAppId = config.managerAppId,
            digest = digest,
            hiddenFromApps = config.hiddenFromApps,
            visibilityCompat = config.visibilityCompat,
        )
        lastEncodedConfig = encoded
        RuntimeComponentPolicy.publish(
            managerAppId = config.managerAppId,
            protectedComponents = config.rootDisabledComponents ?: PersistentComponentState.sanitize(
                preferences.getStringSet(PersistentComponentState.REMOTE_KEY, emptySet()).orEmpty()
            ),
            digest = digest,
        )

        record("MANAGER_IDENTITY appId=${config.managerAppId} source=$source")
        record(
            "RULES_READ reason=$reason source=$source count=${snapshot.configured.size} " +
                "mode=${config.mode} diagnostic=${config.diagnostic} atomic=true " +
                "priorities=${config.priorities.apps.mapValues { it.value.size }} " +
                "typedRules=${config.openTypes.rules.mapValues { it.value.size }} " +
                "typedPriorities=${config.openTypes.priorities.mapValues { it.value.size }} " +
                "browserHosts=${config.browserLinks.hosts.size} " +
                "browserRules=${config.browserLinks.rules.mapValues { it.value.size }} " +
                "rootProtected=${config.rootDisabledComponents?.size ?: -1} " +
                "titles=${config.priorities.titles.size} hiddenFromApps=${config.hiddenFromApps.size} " +
                "visibilityScopes=${config.visibilityCompat.scopes.map { it.name }.sorted()} " +
                "visibilityTargets=${snapshot.allSelectedPackages.size} digest=$digest"
        )
        return true
    }

    private fun applyLegacyConfig(
        sourcePreferences: SharedPreferences,
        reason: String,
        source: String,
    ): Boolean {
        val hasMirror =
            sourcePreferences.contains(
                RuleRepository.KEY_DISPLAY_MODE
            ) ||
                sourcePreferences.contains(
                    RuleRepository.KEY_RULES
                ) ||
                sourcePreferences.contains(
                    RuleRepository.KEY_PRIORITIES
                )
        if (!hasMirror) return false

        val rules =
            sourcePreferences.getStringSet(
                RuleRepository.KEY_RULES,
                emptySet()
            ).orEmpty().toSet()
        val mode = DisplayMode.fromStored(
            sourcePreferences.getString(
                RuleRepository.KEY_DISPLAY_MODE,
                null
            ),
            sourcePreferences.getBoolean(
                RuleRepository.KEY_BLACKLIST,
                true
            )
        )
        val json = Json {
            ignoreUnknownKeys = true
        }
        val priorities = runCatching {
            json.decodeFromString(
                PriorityConfig.serializer(),
                sourcePreferences.getString(
                    RuleRepository.KEY_PRIORITIES,
                    null
                ) ?: "{}"
            ).validated()
        }.getOrDefault(PriorityConfig())
        val openTypes = runCatching {
            json.decodeFromString(
                OpenTypeConfig.serializer(),
                sourcePreferences.getString(
                    RuleRepository.KEY_OPEN_TYPES,
                    null
                ) ?: "{}"
            ).validated()
        }.getOrDefault(OpenTypeConfig())
        val browserLinks = runCatching {
            json.decodeFromString(
                BrowserLinkConfig.serializer(),
                sourcePreferences.getString(
                    RuleRepository.KEY_BROWSER_LINKS,
                    null
                ) ?: "{}"
            ).validated()
        }.getOrDefault(BrowserLinkConfig())
        val hiddenFromApps =
            sourcePreferences.getStringSet(
                RuleRepository.KEY_HIDDEN_FROM_APPS,
                emptySet()
            ).orEmpty().toSet()
        val visibilityScopes =
            sourcePreferences.getStringSet(
                RuleRepository.KEY_VISIBILITY_SCOPES,
                emptySet()
            ).orEmpty().mapNotNull { name ->
                runCatching {
                    VisibilityScope.valueOf(name)
                }.getOrNull()
            }.toSet()

        snapshot = RuleSnapshot(
            configured = rules,
            displayMode = mode,
            priorities = priorities,
            defaultOpen = DefaultOpenConfig(),
            openTypes = openTypes,
            browserLinks = browserLinks,
            diagnostic =
                sourcePreferences.getBoolean(
                    RuleRepository.KEY_DIAGNOSTIC,
                    false
                ),
            managerAppId = -1,
            digest = "",
            hiddenFromApps = hiddenFromApps,
            visibilityCompat =
                VisibilityCompatConfig(
                    scopes = visibilityScopes
                ).validated(),
        )
        lastEncodedConfig = null
        record(
            "RULES_READ reason=$reason source=$source count=${rules.size} " +
                "mode=$mode diagnostic=${snapshot.diagnostic} atomic=false legacy=true " +
                "managerAppId=-1 digest=none " +
                "typedRules=${openTypes.rules.mapValues { it.value.size }} " +
                "browserHosts=${browserLinks.hosts.size} hiddenFromApps=${hiddenFromApps.size}"
        )
        return true
    }

    @Synchronized
    private fun refreshRulesSafely(reason: String) {
        runCatching {
            val cached = preferences
            val cachedEncoded = cached.getString(RuleRepository.KEY_CONFIG, null)
            val cachedDigest = cachedEncoded?.let(RuntimeProtocol::digest)

            // Once Probe v2 has applied a verified config, RemotePreferences is only persistent
            // storage. A stale framework cache must never roll the running snapshot backwards.
            if (
                runtimeTransportActive &&
                cachedDigest != snapshot.digest
            ) {
                record(
                    "REMOTE_CONFIG_IGNORED reason=$reason runtimeAuthoritative=true " +
                        "current=${snapshot.digest.ifEmpty { "none" }} " +
                        "remote=${cachedDigest ?: "none"}"
                )
                return@runCatching
            }

            if (
                cachedEncoded != null &&
                applyAtomicConfig(
                    encoded = cachedEncoded,
                    reason = reason,
                    source = "remote-preferences",
                    expectedDigest = null,
                )
            ) {
                return@runCatching
            }

            if (runtimeTransportActive) return@runCatching

            applyLegacyConfig(
                sourcePreferences = cached,
                reason = reason,
                source = "legacy",
            )
        }.onFailure {
            record(
                "RULES_READ_FAILED reason=$reason error=${it.javaClass.name} " +
                    "message=${it.message?.take(160) ?: "none"}"
            )
            Log.e(
                TAG,
                "Rules refresh failed; keeping previous snapshot",
                it,
            )
        }
    }

    @Synchronized private fun diagnostic(message: String, detail: Boolean = false) {
        if (!snapshot.diagnostic) return
        val now = SystemClock.elapsedRealtime()
        if (now - diagnosticWindow >= 5_000) {
            if (suppressedCount > 0 || suppressedDetails > 0)
                record("RATE_LIMIT criticalSuppressed=$suppressedCount detailSuppressed=$suppressedDetails")
            diagnosticWindow = now
            diagnosticCount = 0
            suppressedCount = 0
            detailCount = 0
            suppressedDetails = 0
        }
        if (detail) {
            if (detailCount++ >= 40) { suppressedDetails++; return }
        } else if (diagnosticCount++ >= 200) { suppressedCount++; return }
        record(message)
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName uptimeMs=${SystemClock.elapsedRealtime()} $message"
        runCatching { Log.i(DIAGNOSTIC_TAG, line) }
        runCatching { log(Log.INFO, DIAGNOSTIC_TAG, line) }
    }

    private fun isQueryIntentActivitiesMethod(method: Method): Boolean =
        method.name in setOf("queryIntentActivities", "queryIntentActivitiesAsUser", "queryIntentActivitiesInternal") &&
            method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
            (List::class.java.isAssignableFrom(method.returnType) ||
                method.returnType.name == "android.content.pm.ParceledListSlice")

    private enum class Layer { SYSTEM, RESOLVER }

    private companion object {
        const val TAG = "ListCleaner"
        const val DIAGNOSTIC_TAG = "ListCleaner.Diagnostic"
        const val HOOK_ID = "ic-query-filter"
        const val FRAMEWORK_PACKAGE = "android"
        const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"
        const val SYSTEM_SCOPE_PACKAGE = "system"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val PER_USER_RANGE = 100_000
        const val COMPONENT_DISCOVERY_PROTOCOL = 2
        const val CONFIG_TRANSFER_TIMEOUT_MS = 10_000L
        const val VISIBILITY_HOOK_ID = "ic-system-package-visibility"
        const val MANAGER_PACKAGE = "com.yagay.ListCleaner"
        val SYSTEM_VISIBILITY_CLASSES = listOf(
            "com.android.server.pm.AppsFilterImpl",
            "com.android.server.pm.AppsFilter"
        )
        val SYSTEM_QUERY_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.ComputerLocked"
        )
    }
}
