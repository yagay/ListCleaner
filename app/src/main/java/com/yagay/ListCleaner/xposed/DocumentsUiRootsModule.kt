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
    @Volatile private var finalSidebarInstalled = false
    private val seen = ConcurrentHashMap<String, String>()
    private val providers = ConcurrentHashMap<String, ProviderInfo>()
    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "ListCleaner-DocsUI").apply { isDaemon = true }
    }
    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val policy by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(preferences, setOf(IntentKind.DOCUMENT_ROOT, IntentKind.DOCUMENT_PROVIDER,
            IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT), true, ::record)
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
        var count = installFinalSidebar(param.classLoader)
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

    private fun installFinalSidebar(loader: ClassLoader): Int {
        val clazz = runCatching {
            Class.forName("com.android.documentsui.sidebar.RootsFragment", false, loader)
        }.getOrNull() ?: return 0
        var count = 0
        ReflectionAccess.hierarchyMethods(clazz).filter {
            it.name == "sortLoadResult" && List::class.java.isAssignableFrom(it.returnType)
        }.forEach { method ->
            val key = method.toGenericString()
            if (!installed.add(key)) return@forEach
            runCatching {
                hook(method).setId("lc-documentsui-sidebar").intercept(XposedInterface.Hooker { chain ->
                    val original = chain.proceed()
                    val items = original as? List<*> ?: return@Hooker original
                    val context = chain.args.filterIsInstance<Context>().firstOrNull()
                        ?: return@Hooker original
                    val request = detectRequestAction(chain.args)
                    val kind = runCatching { IntentKind.valueOf(request.kind) }.getOrNull()
                    val kept = items.filter { item ->
                        val info = sidebarApp(item) ?: return@filter true
                        val user = sidebarUser(item) ?: return@filter true
                        val settings = policy.snapshot(user)
                        val selected = kind?.let(settings::selected).orEmpty()
                        selected.isEmpty() || settings.displayMode.includes(
                            ComponentRule(kind ?: return@filter true, info.packageName, info.name).id in selected, true,
                        )
                    }.toMutableList()
                    // Preserve system rows, separators and profile slots. Promote only recognized rows.
                    kept.indices.filter { sidebarPackage(kept[it], context) != null }
                        .groupBy { sidebarUser(kept[it]) }.forEach { (user, positions) ->
                            if (user == null) return@forEach
                            val settings = policy.snapshot(user)
                            val ranks = (settings.priorities(IntentKind.DOCUMENT_ROOT) +
                                kind?.let(settings::priorities).orEmpty()).distinct().withIndex()
                                .associate { it.value to it.index }
                            val ordered = positions.map { kept[it] }.sortedBy {
                                ranks[sidebarPackage(it, context)] ?: Int.MAX_VALUE
                            }
                            positions.forEachIndexed { index, position -> kept[position] = ordered[index] }
                        }
                    // AOSP also exposes the application rows separately on some layouts.
                    val applicationRows = ReflectionAccess.readField(chain.thisObject, "mApplicationItemList")
                    if (applicationRows is MutableList<*>) {
                        @Suppress("UNCHECKED_CAST")
                        runCatching {
                            val removed = items.toSet() - kept.toSet()
                            (applicationRows as MutableList<Any?>).removeAll(removed)
                        }
                    }
                    snapshotMatchingRoots(kept.mapNotNull(::sidebarRoot), context, request, "SIDEBAR_ROOTS")
                    if (kept == items) original else ArrayList(kept)
                })
                finalSidebarInstalled = true
                count++
                record("FINAL_SIDEBAR_HOOK_INSTALLED method=$key")
            }.onFailure {
                installed.remove(key)
                record("FINAL_SIDEBAR_HOOK_FAILED error=${it.javaClass.name}")
            }
        }
        return count
    }

    private fun sidebarRoot(item: Any?): Any? =
        ReflectionAccess.readField(item, "root", "mRoot")
            ?: ReflectionAccess.readField(ReflectionAccess.readField(item, "rootItem", "mRootItem"), "root", "mRoot")

    private fun sidebarApp(item: Any?): android.content.pm.ActivityInfo? =
        ((ReflectionAccess.readField(item, "info", "mInfo")
            ?: ReflectionAccess.readField(ReflectionAccess.readField(item, "appItem", "mAppItem"), "info", "mInfo"))
            as? android.content.pm.ResolveInfo)?.activityInfo

    private fun sidebarUser(item: Any?): Int? =
        item?.let(::rootUserId) ?: sidebarRoot(item)?.let(::rootUserId)
            ?: sidebarApp(item)?.applicationInfo?.uid?.takeIf { it >= 0 }?.div(100000)

    private fun sidebarPackage(item: Any?, context: Context): String? =
        sidebarRoot(item)?.let { findEntry(it, context)?.rule?.packageName }
            ?: sidebarApp(item)?.takeIf { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 }?.packageName

    private fun rootHooker(methodName: String, fromSidebar: Boolean = false) =
        XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val roots = original as? Collection<*> ?: return@Hooker original
        val ctx = appContext() ?: return@Hooker original
        val isMatching = methodName == "getMatchingRootsBlocking"
        val shouldCapture = !finalSidebarInstalled && (fromSidebar || (isMatching && !sidebarLoaderInstalled))
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
                val ownerUserId = rootUserId ?: processUserId
                val entry = findEntry(root, ctx) ?: return@forEach
                available[root] = entry
                if (seen.put("$ownerUserId|${entry.rule.id}", entry.title) != entry.title) {
                    observations += ObservedEntryRecord(
                        IntentKind.DOCUMENT_ROOT.name, entry.rule.packageName,
                        entry.rule.className, entry.title, observedAt = System.currentTimeMillis(), userId = ownerUserId,
                    )
                }
            }
            if (observations.isNotEmpty()) worker.execute {
                if (persistence.merge(observations)) {
                    record("AUTHORITY_OBSERVED kind=DOCUMENT_ROOT count=" + observations.size)
                }
            }
            // Shared inventories stay intact; only presentation queries apply rules.
            if (!(isMatching || fromSidebar)) return@runCatching original
            val filtered = roots.filter { item ->
                val entry = item?.let { available[it] } ?: return@filter true
                val user = rootUserId(item) ?: processUserId
                val settings = policy.snapshot(user)
                val selected = settings.selected(IntentKind.DOCUMENT_ROOT)
                selected.isEmpty() || settings.displayMode.includes(entry.rule.id in selected, true)
            }.toMutableList()
            filtered.indices.filter { filtered[it]?.let(available::containsKey) == true }
                .groupBy { filtered[it]?.let(::rootUserId) ?: processUserId }.forEach { (user, positions) ->
                    val ranks = policy.snapshot(user).priorities(IntentKind.DOCUMENT_ROOT)
                        .withIndex().associate { it.value to it.index }
                    val promoted = positions.map { filtered[it] }.sortedBy {
                        ranks[available[it]?.rule?.packageName] ?: Int.MAX_VALUE
                    }
                    positions.forEachIndexed { index, position -> filtered[position] = promoted[index] }
                }
            val ordered = filtered
            // A zero-root match is meaningful: do not restore third-party
            // providers just because all candidates were filtered.
            if (ordered == roots.toList()) return@runCatching original
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
        val mimeTypes: List<String> = emptyList(),
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
                (ReflectionAccess.readField(state, "acceptMimes") as? Array<*>)?.filterIsInstance<String>().orEmpty(),
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
            mimeTypes = request.mimeTypes,
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

