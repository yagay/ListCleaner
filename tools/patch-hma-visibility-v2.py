from pathlib import Path
import re

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

# Imports for version-aware visibility adapters and cached reflection.
if 'import android.os.Build\n' not in s:
    s = s.replace('import android.os.Binder\n', 'import android.os.Binder\nimport android.os.Build\n')
if 'import java.lang.reflect.Field\n' not in s:
    s = s.replace('import java.lang.reflect.Constructor\n', 'import java.lang.reflect.Constructor\nimport java.lang.reflect.Field\n')

# Remove stale APP-layer hot-reload mapping.
s = s.replace('''                    "$HOOK_ID-app" -> Layer.APP\n''', '')
s = s.replace('''                    Layer.APP -> installApplicationClientHooks(it, baseProcess)\n''', '')

# Preserve archived-package hook on hot reload.
s = s.replace('''                    method != null && handle.id == VISIBILITY_HOOK_ID && method.name == "shouldFilterApplication" -> {\n                        handle.replaceHook(systemVisibilityHooker())\n                        installedMethods.add("VISIBILITY#${method.toGenericString()}")\n                    }\n''','''                    method != null && handle.id == VISIBILITY_HOOK_ID && method.name == "shouldFilterApplication" -> {\n                        val adapter = VisibilityMethodAdapter.create(method)\n                        if (adapter != null) {\n                            handle.replaceHook(systemVisibilityHooker(adapter))\n                            installedMethods.add("VISIBILITY#${method.toGenericString()}")\n                        } else handle.unhook()\n                    }\n                    method != null && handle.id == ARCHIVED_VISIBILITY_HOOK_ID && method.name == "getArchivedPackageInternal" -> {\n                        handle.replaceHook(archivedPackageHooker())\n                        installedMethods.add("ARCHIVED_VISIBILITY#${method.toGenericString()}")\n                    }\n''')

# Add hot-path state/caches near installedMethods.
needle = '''    private val installedMethods = ConcurrentHashMap.newKeySet<String>()\n    private val tracedActions = ConcurrentHashMap.newKeySet<String>()\n'''
replacement = '''    private val installedMethods = ConcurrentHashMap.newKeySet<String>()\n    private val tracedActions = ConcurrentHashMap.newKeySet<String>()\n    private data class CallerCacheEntry(val packages: Set<String>, val expiresAt: Long)\n    private val callerPackageCache = ConcurrentHashMap<Int, CallerCacheEntry>()\n    private val packageAccessorCache = ConcurrentHashMap<Class<*>, PackageNameAccessor>()\n    @Volatile private var visibilityFailureCount = 0\n    @Volatile private var visibilityFailOpen = false\n    @Volatile private var appGlobalsPackageManager: Any? = null\n    @Volatile private var appGlobalsGetPackagesForUid: Method? = null\n'''
if needle in s:
    s = s.replace(needle, replacement)

# Replace current visibility implementation with version-aware, cached, fail-open engine.
start = s.index('    private fun installSystemVisibilityHooks(classLoader: ClassLoader) {')
end = s.index('    private fun installResolverClientHooks(classLoader: ClassLoader) {', start)
new_block = r'''    private sealed interface PackageNameAccessor {
        fun read(value: Any): String?

        data class Getter(private val method: Method) : PackageNameAccessor {
            override fun read(value: Any): String? = runCatching { method.invoke(value) as? String }.getOrNull()
        }

        data class FieldValue(private val field: Field) : PackageNameAccessor {
            override fun read(value: Any): String? = runCatching { field.get(value) as? String }.getOrNull()
        }

        data object None : PackageNameAccessor {
            override fun read(value: Any): String? = null
        }
    }

    private data class VisibilityMethodAdapter(
        val uidIndex: Int,
        val targetIndex: Int,
        val computerIndex: Int?,
        val signature: String,
    ) {
        companion object {
            fun create(method: Method): VisibilityMethodAdapter? {
                val types = method.parameterTypes
                if (method.name != "shouldFilterApplication" || method.returnType != Boolean::class.javaPrimitiveType) return null

                // Android 13+ AOSP: (Computer snapshot, int callingUid, ..., PackageStateInternal target, ...)
                if (types.size >= 4 && types[1] == Int::class.javaPrimitiveType &&
                    types[0].name.contains("Computer")) {
                    return VisibilityMethodAdapter(1, 3, 0, "T_PLUS")
                }
                // Android 11/12 AOSP AppsFilter: callingUid is first, target package state is usually third.
                if (types.size >= 3 && types[0] == Int::class.javaPrimitiveType) {
                    return VisibilityMethodAdapter(0, 2, null, "R_S")
                }
                // OEM/newer fallback: resolve indexes once at installation, never scan args on the hot path.
                val uid = types.indexOfFirst { it == Int::class.javaPrimitiveType }
                val target = types.indices.lastOrNull { i ->
                    i != uid && !types[i].isPrimitive && types[i] != String::class.java &&
                        (types[i].name.contains("PackageState") || types[i].name.contains("PackageSetting"))
                } ?: types.indices.lastOrNull { i -> i != uid && !types[i].isPrimitive }
                val computer = types.indices.firstOrNull { types[it].name.contains("Computer") }
                return if (uid >= 0 && target != null) VisibilityMethodAdapter(uid, target, computer, "OEM_FALLBACK") else null
            }
        }
    }

    private fun installSystemVisibilityHooks(classLoader: ClassLoader) {
        var installed = 0
        SYSTEM_VISIBILITY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                diagnostic("VISIBILITY_CLASS_UNAVAILABLE class=$className error=${it.javaClass.name}")
                return@forEach
            }
            val methods = generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { it.name == "shouldFilterApplication" && it.returnType == Boolean::class.javaPrimitiveType }
                .distinctBy(Method::toGenericString)
                .toList()
            methods.forEach { method ->
                val adapter = VisibilityMethodAdapter.create(method)
                if (adapter == null) {
                    record("VISIBILITY_SIGNATURE_UNSUPPORTED method=${method.toGenericString()}")
                    return@forEach
                }
                val key = "VISIBILITY#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(VISIBILITY_HOOK_ID).intercept(systemVisibilityHooker(adapter))
                    installed++
                    record("VISIBILITY_ADAPTER signature=${adapter.signature} uidIndex=${adapter.uidIndex} targetIndex=${adapter.targetIndex} computerIndex=${adapter.computerIndex} method=${method.toGenericString()}")
                }.onFailure {
                    installedMethods.remove(key)
                    record("VISIBILITY_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                }
            }
        }
        installArchivedPackageHook(classLoader)
        record("VISIBILITY_HOOKS new=$installed callers=${snapshot.hiddenFromApps.size} targets=${snapshot.allSelectedPackages.size} hotPath=memory_snapshot")
    }

    private fun installArchivedPackageHook(classLoader: ClassLoader) {
        val clazz = runCatching { Class.forName("com.android.server.pm.PackageManagerService", false, classLoader) }.getOrNull() ?: return
        val methods = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.name == "getArchivedPackageInternal" && it.parameterTypes.isNotEmpty() }
            .toList()
        methods.forEach { method ->
            val key = "ARCHIVED_VISIBILITY#${method.toGenericString()}"
            if (!installedMethods.add(key)) return@forEach
            runCatching {
                method.isAccessible = true
                hook(method).setId(ARCHIVED_VISIBILITY_HOOK_ID).intercept(archivedPackageHooker())
                record("ARCHIVED_VISIBILITY_HOOK_INSTALLED method=${method.toGenericString()}")
            }.onFailure {
                installedMethods.remove(key)
                record("ARCHIVED_VISIBILITY_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
            }
        }
    }

    private fun systemVisibilityHooker(adapter: VisibilityMethodAdapter) = XposedInterface.Hooker { chain ->
        if (visibilityFailOpen) return@Hooker chain.proceed()
        val current = snapshot // immutable/volatile snapshot: no preference I/O on this hot path.
        if (!visibilityEnabled(current)) return@Hooker chain.proceed()
        try {
            val args = chain.args
            val callingUid = args.getOrNull(adapter.uidIndex) as? Int ?: return@Hooker chain.proceed()
            if (callingUid < Process.FIRST_APPLICATION_UID) return@Hooker chain.proceed()

            val computer = adapter.computerIndex?.let { args.getOrNull(it) }
            val callers = callerPackages(callingUid, computer)
            if (callers.isEmpty() || callers.none(current.hiddenFromApps::contains)) return@Hooker chain.proceed()

            val target = packageNameFromState(args.getOrNull(adapter.targetIndex)) ?: return@Hooker chain.proceed()
            if (!shouldHidePackage(current, callers, target)) return@Hooker chain.proceed()

            visibilityFailureCount = 0
            diagnostic("SYSTEM_VISIBILITY_FILTER uid=$callingUid caller=${callers.sorted()} target=$target adapter=${adapter.signature}")
            true
        } catch (failure: Throwable) {
            onVisibilityFailure("shouldFilterApplication", failure)
            chain.proceed()
        }
    }

    private fun archivedPackageHooker() = XposedInterface.Hooker { chain ->
        if (visibilityFailOpen) return@Hooker chain.proceed()
        val current = snapshot
        if (!visibilityEnabled(current)) return@Hooker chain.proceed()
        try {
            val callingUid = Binder.getCallingUid()
            if (callingUid < Process.FIRST_APPLICATION_UID) return@Hooker chain.proceed()
            val callers = callerPackages(callingUid, null)
            if (callers.none(current.hiddenFromApps::contains)) return@Hooker chain.proceed()
            val target = chain.args.firstOrNull { it is String } as? String ?: return@Hooker chain.proceed()
            if (!shouldHidePackage(current, callers, target)) return@Hooker chain.proceed()
            diagnostic("SYSTEM_VISIBILITY_ARCHIVED_FILTER uid=$callingUid caller=${callers.sorted()} target=$target")
            null
        } catch (failure: Throwable) {
            onVisibilityFailure("getArchivedPackageInternal", failure)
            chain.proceed()
        }
    }

    private fun visibilityEnabled(current: RuleSnapshot): Boolean =
        current.displayMode == DisplayMode.HIDE_SELECTED &&
            current.hiddenFromApps.isNotEmpty() && current.allSelectedPackages.isNotEmpty()

    private fun shouldHidePackage(current: RuleSnapshot, callers: Set<String>, target: String): Boolean {
        if (target !in current.allSelectedPackages) return false
        if (target in PROTECTED_VISIBILITY_PACKAGES || target in callers) return false
        return callers.any(current.hiddenFromApps::contains)
    }

    private fun callerPackages(uid: Int, computer: Any?): Set<String> {
        val now = SystemClock.elapsedRealtime()
        callerPackageCache[uid]?.takeIf { it.expiresAt > now }?.let { return it.packages }
        val packages = if (computer != null) packagesForUidFromComputer(computer, uid) else packagesForUidFromSystem(uid)
        if (packages.isNotEmpty()) callerPackageCache[uid] = CallerCacheEntry(packages, now + CALLER_CACHE_TTL_MS)
        return packages
    }

    private fun packagesForUidFromComputer(computer: Any, uid: Int): Set<String> {
        val method = findGetPackagesForUid(computer.javaClass) ?: return packagesForUidFromSystem(uid)
        val identity = Binder.clearCallingIdentity()
        return try {
            method.isAccessible = true
            stringSet(method.invoke(computer, uid))
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun packagesForUidFromSystem(uid: Int): Set<String> {
        val identity = Binder.clearCallingIdentity()
        return try {
            var pms = appGlobalsPackageManager
            var method = appGlobalsGetPackagesForUid
            if (pms == null || method == null) {
                pms = Class.forName("android.app.AppGlobals").getDeclaredMethod("getPackageManager").invoke(null) ?: return emptySet()
                method = pms.javaClass.methods.firstOrNull { candidate ->
                    candidate.name == "getPackagesForUid" && candidate.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                } ?: pms.javaClass.interfaces.asSequence().flatMap { it.methods.asSequence() }.firstOrNull { candidate ->
                    candidate.name == "getPackagesForUid" && candidate.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                }
                if (method == null) return emptySet()
                method.isAccessible = true
                appGlobalsPackageManager = pms
                appGlobalsGetPackagesForUid = method
            }
            stringSet(method.invoke(pms, uid))
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun stringSet(value: Any?): Set<String> = when (value) {
        is Array<*> -> value.filterIsInstance<String>().toSet()
        is Collection<*> -> value.filterIsInstance<String>().toSet()
        else -> emptySet()
    }

    private fun findGetPackagesForUid(clazz: Class<*>): Method? =
        generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { candidate ->
                candidate.name == "getPackagesForUid" && candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == Int::class.javaPrimitiveType
            }

    private fun packageNameFromState(value: Any?): String? {
        if (value == null) return null
        if (value is String) return value.takeIf(::looksLikePackageName)
        if (value is Number || value is Boolean || value is ClassLoader) return null
        val accessor = packageAccessorCache.computeIfAbsent(value.javaClass, ::buildPackageNameAccessor)
        return accessor.read(value)?.takeIf(::looksLikePackageName)
    }

    private fun buildPackageNameAccessor(clazz: Class<*>): PackageNameAccessor {
        val classes = generateSequence(clazz as Class<*>?) { it.superclass }.toList()
        classes.asSequence().flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { method ->
                method.parameterTypes.isEmpty() && method.returnType == String::class.java &&
                    method.name in setOf("getPackageName", "getName")
            }?.let { method ->
                runCatching { method.isAccessible = true }
                return PackageNameAccessor.Getter(method)
            }
        classes.asSequence().flatMap { it.declaredFields.asSequence() }
            .firstOrNull { field ->
                field.type == String::class.java && field.name in setOf("mName", "name", "packageName", "mPackageName")
            }?.let { field ->
                runCatching { field.isAccessible = true }
                return PackageNameAccessor.FieldValue(field)
            }
        return PackageNameAccessor.None
    }

    private fun onVisibilityFailure(stage: String, failure: Throwable) {
        val failures = ++visibilityFailureCount
        record("VISIBILITY_FAILURE stage=$stage count=$failures error=${failure.javaClass.name}")
        if (failures >= VISIBILITY_FAILURE_LIMIT) {
            visibilityFailOpen = true
            record("VISIBILITY_FAIL_OPEN enabled=true reason=repeated_failures stage=$stage")
        }
    }

    private fun looksLikePackageName(value: String): Boolean =
        value.length in 3..255 && '.' in value && value.none { it.isWhitespace() || it.isISOControl() }

'''
s = s[:start] + new_block + s[end:]

# Remove the unused third-party APP/virtual-visibility installation block.
s = re.sub(
    r'\n    private fun installApplicationClientHooks\(classLoader: ClassLoader, packageName: String\) \{.*?\n    private fun installFinalOrderingHooks',
    '\n    private fun installFinalOrderingHooks', s, flags=re.S)

# Remove unused process-level virtual hook implementations.
s = re.sub(
    r'\n    private fun processVisibilityHidden\(packageName: String, current: RuleSnapshot\): Boolean \{.*?\n    private fun isSelectedCandidate',
    '\n    private fun isSelectedCandidate', s, flags=re.S)

# Since APP layer is no longer installed, make matching exact-only and remove APP-specific branch.
s = s.replace('''        return layer == Layer.APP && current.hasPackageSelection(kind, activity.packageName)\n''','''        return false\n''')
s = re.sub(r'\n        if \(layer == Layer\.APP\) \{.*?\n        \}\n        val replacement = transform', '\n        val replacement = transform', s, flags=re.S)
s = s.replace('''                val packageFallback = layer == Layer.APP && !exactSelected && current.hasPackageSelection(kind, activity.packageName)\n                val selected = exactSelected || packageFallback\n''','''                val packageFallback = false\n                val selected = exactSelected\n''')

# Invalidate caller cache on config changes, but do not reintroduce hot-path polling.
s = s.replace('''                snapshot = RuleSnapshot(config.rules.map { it.id }.toSet(), config.mode,\n                    config.priorities, config.diagnostic, config.managerAppId, digest, config.hiddenFromApps)\n                lastEncodedConfig = encoded\n''','''                snapshot = RuleSnapshot(config.rules.map { it.id }.toSet(), config.mode,\n                    config.priorities, config.diagnostic, config.managerAppId, digest, config.hiddenFromApps)\n                callerPackageCache.clear()\n                visibilityFailureCount = 0\n                visibilityFailOpen = false\n                lastEncodedConfig = encoded\n''')

# Remove obsolete method classifiers for process virtual hooks.
s = re.sub(r'\n    private fun isActivityInfoMethod\(method: Method\): Boolean =.*?\n    private enum class Layer', '\n    private enum class Layer', s, flags=re.S)

# Layer APP is no longer used.
s = s.replace('private enum class Layer { SYSTEM, RESOLVER, APP }', 'private enum class Layer { SYSTEM, RESOLVER }')

# Remove obsolete imports only used by deleted process-level hooks.
s = s.replace('import android.content.pm.PackageInfo\n', '')
s = s.replace('import android.content.pm.PackageManager\n', '')
s = s.replace('import android.content.ComponentName\n', '')

# Constants: remove process virtual IDs and add system visibility protections.
s = re.sub(r'\n        const val VIRTUAL_ACTIVITY_HOOK_ID = .*?\n        const val VIRTUAL_OPTIONS_HOOK_ID = .*?\n', '\n', s, flags=re.S)
s = s.replace('''        const val VISIBILITY_HOOK_ID = "ic-system-package-visibility"\n        const val MANAGER_PACKAGE = "com.yagay.ListCleaner"\n''','''        const val VISIBILITY_HOOK_ID = "ic-system-package-visibility"\n        const val ARCHIVED_VISIBILITY_HOOK_ID = "ic-system-archived-package-visibility"\n        const val MANAGER_PACKAGE = "com.yagay.ListCleaner"\n        const val CALLER_CACHE_TTL_MS = 60_000L\n        const val VISIBILITY_FAILURE_LIMIT = 3\n        val PROTECTED_VISIBILITY_PACKAGES = setOf(\n            "android", MANAGER_PACKAGE, "com.android.systemui", "com.android.settings",\n            "com.android.permissioncontroller", "com.google.android.permissioncontroller"\n        )\n''')
# Android 11/12 compatibility class first, then T+ implementation.
s = s.replace('''        val SYSTEM_VISIBILITY_CLASSES = listOf(\n            "com.android.server.pm.AppsFilterImpl"\n        )\n''','''        val SYSTEM_VISIBILITY_CLASSES = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(\n            "com.android.server.pm.AppsFilterImpl"\n        ) else listOf(\n            "com.android.server.pm.AppsFilter"\n        )\n''')

# Sanity guards: ensure obsolete runtime surface is gone and new architecture exists.
assert 'installVirtualComponentHooks' not in s
assert 'PROCESS_VISIBILITY_' not in s
assert 'systemVisibilityHooker(adapter' in s
assert 'ARCHIVED_VISIBILITY_HOOK_ID' in s
assert 'pollPreferences()\n        val current = snapshot\n        if (!visibilityEnabled' not in s
assert 'Layer.APP' not in s

p.write_text(s)
