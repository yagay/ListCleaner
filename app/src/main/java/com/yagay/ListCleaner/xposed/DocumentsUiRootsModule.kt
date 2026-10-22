package com.yagay.ListCleaner.xposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.PickerRuntimeRoot
import com.yagay.ListCleaner.domain.PickerRuntimeSnapshot
import com.yagay.ListCleaner.domain.PickerRuntimeSnapshotCodec
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Filters only third-party RootInfo displayed by DocumentsUI, leaving providers and files intact. */
class DocumentsUiRootsModule : XposedModule() {
    private data class RootEntry(val rule: ComponentRule, val title: String)
    @Volatile private var processName = ""
    private val installed = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var sidebarLoaderInstalled = false
    private val seen = ConcurrentHashMap<String, String>()
    private val providers = ConcurrentHashMap<String, ProviderInfo>()
    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "ListCleaner-DocsUI").apply { isDaemon = true }
    }
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val policy by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(preferences, setOf(IntentKind.DOCUMENT_ROOT), true, ::record)
    }
    private val persistence by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }
    override fun onHotReloading(param: HotReloadingParam): Boolean = false
    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName !in UI_PACKAGES) return
        var count = 0
        // RootsLoader is the AOSP navigation drawer's loader, rather than a
        // cache query that can also run on behalf of Recents or other callers.
        // When available it is the preferred source for a picker snapshot.
        LOADER_CLASSES.forEach { className ->
            val klass = runCatching { Class.forName(className, false, param.classLoader) }
                .getOrNull() ?: return@forEach
            ReflectionAccess.hierarchyMethods(klass)
                .filter { it.name == "loadInBackground" && !Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 0 &&
                    java.util.Collection::class.java.isAssignableFrom(it.returnType) }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installed.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(rootHooker(method.name, fromSidebar = true))
                        sidebarLoaderInstalled = true
                        count++
                        record("SIDEBAR_HOOK_INSTALLED method=" + key)
                    }.onFailure {
                        installed.remove(key)
                        record("SIDEBAR_HOOK_FAILED method=" + key + " error=" + it.javaClass.name)
                    }
                }
        }
        CACHE_CLASSES.forEach { className ->
            val klass = runCatching { Class.forName(className, false, param.classLoader) }.getOrNull()
            if (klass == null) {
                record("CACHE_CLASS_MISSING name=$className")
                return@forEach
            }
            ReflectionAccess.hierarchyMethods(klass)
                .filter {
                    it.name in ROOT_METHODS && !Modifier.isStatic(it.modifiers) &&
                        java.util.Collection::class.java.isAssignableFrom(it.returnType)
                }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installed.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(rootHooker(method.name))
                        count++
                        record("HOOK_INSTALLED method=" + key)
                    }.onFailure {
                        installed.remove(key)
                        record("HOOK_FAILED method=" + key + " error=" + it.javaClass.name)
                    }
                }
        }
        if (count > 0) {
            policy.start()
            record("HOOKS_READY package=" + param.packageName + " total=" + installed.size)
        } else {
            record("HOOK_FAILED reason=roots_cache_methods_unavailable package=" + param.packageName)
            // The Google/OEM fork may rename or change the signature of its cache.
            // Enumerate method names and parameter/return types without touching its data.
            CACHE_CLASSES.forEach { className ->
                val klass = runCatching {
                    Class.forName(className, false, param.classLoader)
                }.getOrNull() ?: return@forEach
                ReflectionAccess.hierarchyMethods(klass)
                    .asSequence()
                    .filter { method ->
                        method.name.contains("root", ignoreCase = true) ||
                            method.name.contains("provider", ignoreCase = true)
                    }
                    .take(60)
                    .forEach { method ->
                        record("ROOT_API_CANDIDATE method=" + method.toGenericString())
                    }
            }
        }
    }

    private fun rootHooker(methodName: String, fromSidebar: Boolean = false) =
        XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val roots = original as? Collection<*> ?: return@Hooker original
        val ctx = appContext() ?: return@Hooker original
        val isMatching = methodName == "getMatchingRootsBlocking"
        val shouldCapture = fromSidebar || (isMatching && !sidebarLoaderInstalled)
        val request = if (fromSidebar) {
            detectRequestAction(listOf(ReflectionAccess.readField(chain.thisObject, "mState", "state")))
        } else {
            detectRequestAction(chain.args)
        }
        if (roots.size > PickerRuntimeSnapshotCodec.MAX_ROOTS) {
            record("ROOTS_LARGE count=" + roots.size +
                " captureBudget=" + PickerRuntimeSnapshotCodec.MAX_ROOTS)
        }
        if (roots.isEmpty()) {
            if (shouldCapture) snapshotMatchingRoots(
                roots, ctx, request, if (fromSidebar) "ROOTS_LOADER" else "PROVIDERS_CACHE"
            )
            return@Hooker original
        }
        runCatching {
            val available = IdentityHashMap<Any, RootEntry>()
            val observations = ArrayList<ObservedEntryRecord>()
            val processUserId = AndroidUid.userId(Process.myUid())
            roots.forEach { item ->
                val root = item ?: return@forEach
                // The cache may contain work-profile roots in this process.
                // Never apply the current user's rule to a different user's root.
                val rootUserId = rootUserId(root)
                if (rootUserId != null && rootUserId != processUserId) return@forEach
                val entry = findEntry(root, ctx) ?: return@forEach
                available[root] = entry
                if (seen.put(entry.rule.id, entry.title) != entry.title) {
                    observations += ObservedEntryRecord(
                        IntentKind.DOCUMENT_ROOT.name, entry.rule.packageName,
                        entry.rule.className, entry.title, observedAt = System.currentTimeMillis(),
                    )
                }
            }
            if (observations.isNotEmpty()) worker.execute {
                if (persistence.merge(observations)) {
                    record("AUTHORITY_OBSERVED kind=DOCUMENT_ROOT count=" + observations.size)
                }
            }
            val settings = policy.snapshot(processUserId)
            val chosen = settings.selected(IntentKind.DOCUMENT_ROOT)
            val priorities = settings.priorities(IntentKind.DOCUMENT_ROOT)
            record("QUERY kind=DOCUMENT_ROOT count=" + roots.size + " identified=" + available.size)
            // Inventory and authority-specific cache lookups are not sidebar presentation
            // queries. Never mutate those shared caches: they may feed other callers.
            if (!(isMatching || fromSidebar) ||
                settings.displayMode == DisplayMode.SHOW_ALL ||
                (chosen.isEmpty() && priorities.isEmpty())) return@runCatching original

            val filtered = roots.filter { item ->
                val entry = item?.let { available[it] }
                // Recents, internal storage, downloads, system roots and unknown OEM types stay visible.
                entry == null || settings.displayMode.includes(entry.rule.id in chosen, chosen.isNotEmpty())
            }
            val ranks = priorities.withIndex().associate { it.value to it.index }
            val ordered = if (ranks.isEmpty()) filtered else filtered.withIndex()
                .sortedWith(compareBy(
                    { it: IndexedValue<Any?> ->
                        val pkg = it.value?.let { root -> available[root]?.rule?.packageName }
                        ranks[pkg] ?: Int.MAX_VALUE
                    },
                    { it: IndexedValue<Any?> -> it.index }
                )).map { it.value }
            // A zero-root match is meaningful: do not restore third-party
            // providers just because all candidates were filtered.
            if (ordered.size == roots.size && ranks.isEmpty()) return@runCatching original
            record("FILTER kind=DOCUMENT_ROOT before=" + roots.size + " after=" + ordered.size)
            ArrayList(ordered)
        }.onFailure { record("HOOK_FAILED stage=filter error=" + it.javaClass.name) }
            .getOrDefault(original)
            .also { returned ->
                // Only the matching-roots method is a filtered, caller-sensitive root query.
                // The other cache methods may expose entire provider inventories or subsets.
                if (shouldCapture) {
                    (returned as? Collection<*>)?.let { returnedRoots ->
                        snapshotMatchingRoots(
                            returnedRoots, ctx, request,
                            if (fromSidebar) "ROOTS_LOADER" else "PROVIDERS_CACHE"
                        )
                    }
                }
            }
    }

    private data class RequestAction(
        val kind: String,
        val value: Int?,
        val stateType: String,
        val candidates: String,
    )

    /** Use the actual State enum constants from the installed DocumentsUI, not
     * guessed AOSP/OEM integer IDs. Keep UNKNOWN as diagnostic, never live proof.
     */
    private fun detectRequestAction(args: List<Any?>): RequestAction {
        val names = mapOf(
            "ACTION_OPEN" to "OPEN_DOCUMENT",
            "ACTION_CREATE" to "CREATE_DOCUMENT",
            "ACTION_GET_CONTENT" to "GET_CONTENT",
            "ACTION_OPEN_TREE" to "DOCUMENT_ROOT",
        )
        args.forEach { possibleState ->
            val state = possibleState ?: return@forEach
            val code = ReflectionAccess.readField(state, "action", "mAction") as? Number
                ?: return@forEach
            val available = linkedMapOf<String, Int>()
            names.forEach { (name, _) ->
                var owner: Class<*>? = state.javaClass
                while (owner != null && name !in available) {
                    val clazz = owner ?: break
                    runCatching {
                        clazz.getDeclaredField(name).takeIf {
                            Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType
                        }?.let { field ->
                            field.isAccessible = true
                            available[name] = field.getInt(null)
                        }
                    }
                    owner = clazz.superclass
                }
            }
            val found = available.entries.firstOrNull { it.value == code.toInt() }
            return RequestAction(
                found?.let { names.getValue(it.key) } ?: "UNKNOWN",
                code.toInt(),
                state.javaClass.name.take(160),
                available.entries.joinToString(",") { it.key + "=" + it.value }.take(240),
            )
        }
        return RequestAction("UNKNOWN", null, "", "")
    }

    /** Reads the profile attached to an AOSP RootInfo, if the installed build exposes it. */
    private fun rootUserId(root: Any): Int? {
        val value = ReflectionAccess.readField(root, "userId", "mUserId") ?: return null
        val number = when (value) {
            is Number -> value
            else -> (ReflectionAccess.invokeFirstNoArg(value, listOf("getIdentifier", "getId"))
                ?: ReflectionAccess.readField(value, "mUserId", "userId", "mId", "id")) as? Number
        }
        return number?.toInt()?.takeIf { it >= 0 }
    }

    private fun snapshotMatchingRoots(
        roots: Collection<*>, context: Context, request: RequestAction, source: String,
    ) {
        // Never persist raw document paths, root IDs or URI grants.
        val entries = roots.asSequence().take(PickerRuntimeSnapshotCodec.MAX_ROOTS)
            .mapNotNull { root ->
                root ?: return@mapNotNull null
                val authority = ReflectionAccess.readField(root, "authority", "mAuthority")
                    ?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val rootId = ReflectionAccess.readField(root, "rootId", "mRootId")
                    ?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val provider = providers[authority] ?: runCatching {
                    context.packageManager.resolveContentProvider(authority, 0)
                }.getOrNull()?.also { providers[authority] = it }
                val system = provider?.applicationInfo?.flags
                    ?.and(ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM
                val label = ReflectionAccess.readField(root, "title", "mTitle")
                    ?.toString()?.trim()?.take(160)?.takeIf(String::isNotBlank)
                    ?: authority.take(160)
                PickerRuntimeRoot(
                    packageName = provider?.packageName ?: authority,
                    label = label,
                    syntheticId = SyntheticEntryKeys.documentRootClass(authority, rootId),
                    systemOwned = system,
                    userId = rootUserId(root),
                )
            }.distinctBy { it.userId to it.syntheticId }.toList()
        val snapshot = PickerRuntimeSnapshot(
            capturedAt = System.currentTimeMillis(),
            userId = AndroidUid.userId(Process.myUid()),
            roots = entries,
            totalRoots = roots.size,
            truncated = roots.size > entries.size,
            requestKind = request.kind,
            actionCode = request.value,
            stateClassName = request.stateType,
            knownActionConstants = request.candidates,
            captureSource = source,
        )
        val encoded = PickerRuntimeSnapshotCodec.encode(snapshot)
        if (encoded.isEmpty() || encoded.length > PickerRuntimeSnapshotCodec.MAX_CHARS) return
        worker.execute {
            runCatching {
                preferences.edit().apply {
                    putString(PickerRuntimeSnapshotCodec.REMOTE_KEY, encoded)
                    if (request.kind != "UNKNOWN") {
                        putString(PickerRuntimeSnapshotCodec.keyForRequest(request.kind), encoded)
                    }
                }.commit()
            }.onSuccess { saved ->
                if (saved) record(
                    "PICKER_MATCHING_ROOTS kind=" + request.kind +
                        " captured=" + entries.size +
                        " source=" + source +
                        " action=" + (request.value?.toString() ?: "missing") +
                        " state=" + request.stateType +
                        " constants=" + request.candidates
                )
            }.onFailure {
                record("PICKER_SNAPSHOT_FAILED error=" + it.javaClass.name)
            }
        }
    }

    private fun findEntry(root: Any, context: Context): RootEntry? {
        val authority = (ReflectionAccess.readField(root, "authority", "mAuthority") as? String)
            ?.takeIf(String::isNotBlank) ?: return null
        val rootId = (ReflectionAccess.readField(root, "rootId", "mRootId") as? String)
            ?.takeIf(String::isNotBlank) ?: return null
        val provider = providers[authority] ?: runCatching {
            context.packageManager.resolveContentProvider(authority, 0)
        }.getOrNull()?.also { providers[authority] = it } ?: return null
        if (provider.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0) return null
        val rule = ComponentRule(
            IntentKind.DOCUMENT_ROOT, provider.packageName,
            SyntheticEntryKeys.documentRootClass(authority, rootId)
        )
        if (!rule.isValid()) return null
        val title = ReflectionAccess.readField(root, "title", "mTitle")?.toString()
            ?.trim()?.take(ObservedEntryRecord.MAX_LABEL_CHARS)
            ?.takeIf(String::isNotBlank) ?: rootId.take(ObservedEntryRecord.MAX_LABEL_CHARS)
        return RootEntry(rule, title)
    }

    private fun appContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()

    private fun record(message: String) {
        val text = "pid=" + Process.myPid() + " process=" + processName + " " + message
        runCatching { Log.i(TAG, text) }
        runCatching { log(Log.INFO, TAG, text) }
    }

    private companion object {
        const val TAG = "ListCleaner.DocumentsUI"
        const val HOOK_ID = "lc-documentsui-roots"
        const val MAX_ROOTS = 512
        val UI_PACKAGES = setOf("com.android.documentsui", "com.google.android.documentsui")
        val LOADER_CLASSES = listOf(
            "com.android.documentsui.roots.RootsLoader",
            "com.android.documentsui.RootsLoader",
        )
        val CACHE_CLASSES = listOf(
            // AOSP versions differ: older branches call this RootsCache; newer
            // DocumentsUI code uses ProvidersCache for root discovery.
            "com.android.documentsui.roots.ProvidersCache",
            "com.android.documentsui.ProvidersCache",
            "com.android.documentsui.base.ProvidersCache",
            "com.android.documentsui.roots.RootsCache",
            "com.android.documentsui.RootsCache",
            "com.android.documentsui.base.RootsCache",
        )
        val ROOT_METHODS = setOf(
            "getRootsBlocking", "getMatchingRootsBlocking", "getRootsForAuthorityBlocking"
        )
    }
}
