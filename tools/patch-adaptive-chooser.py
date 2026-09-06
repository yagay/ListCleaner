from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace('import android.content.pm.ApplicationInfo\n', 'import android.content.pm.ApplicationInfo\nimport android.content.ComponentName\n')
s = s.replace('import java.util.concurrent.ConcurrentHashMap\n', 'import java.util.concurrent.ConcurrentHashMap\nimport java.util.concurrent.atomic.AtomicLong\n')

old = '''    private val queryInProgress = ThreadLocal<Boolean>()
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val tracedActions = ConcurrentHashMap.newKeySet<String>()
'''
new = '''    private val queryInProgress = ThreadLocal<Boolean>()
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val tracedActions = ConcurrentHashMap.newKeySet<String>()
    private val adaptiveInstalled = ConcurrentHashMap.newKeySet<String>()
    private val adaptiveGeneration = AtomicLong(0L)
    @Volatile private var adaptiveContext: AdaptiveContext? = null

    private data class AdaptiveContext(
        val generation: Long,
        val kind: IntentKind,
        val selectedPackages: Set<String>,
        val expiresAt: Long,
        val ownerPackage: String,
    )
'''
assert old in s
s = s.replace(old, new)

old = '''        if (layer == Layer.APP) {
            if (extracted.values.size <= 1) {
'''
new = '''        if (layer == Layer.APP) {
            armAdaptiveChooser(kind)
            installAdaptiveChooserHooksFromStack(kind)
            if (extracted.values.size <= 1) {
'''
assert old in s
s = s.replace(old, new)

insert_before = '''    private fun isSelectedCandidate(kind: IntentKind, activity: ActivityInfo, current: RuleSnapshot, layer: Layer): Boolean {
'''
block = r'''    private fun armAdaptiveChooser(kind: IntentKind) {
        val baseProcess = processName.substringBefore(':')
        if (baseProcess.isBlank() || baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE || baseProcess == SYSTEM_SCOPE_PACKAGE) return
        val selected = snapshot.configured.asSequence().mapNotNull { id ->
            val parts = id.split('|', limit = 3)
            if (parts.getOrNull(0) != kind.name) return@mapNotNull null
            parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        }.toSet()
        if (selected.isEmpty()) return
        val generation = adaptiveGeneration.incrementAndGet()
        adaptiveContext = AdaptiveContext(
            generation = generation,
            kind = kind,
            selectedPackages = selected,
            expiresAt = SystemClock.elapsedRealtime() + ADAPTIVE_WINDOW_MS,
            ownerPackage = baseProcess,
        )
        diagnostic("ADAPTIVE_ARM kind=$kind generation=$generation packages=${selected.size} owner=$baseProcess")
    }

    private fun installAdaptiveChooserHooksFromStack(kind: IntentKind) {
        val owner = processName.substringBefore(':')
        if (owner.isBlank() || owner == FRAMEWORK_PACKAGE || owner == INTENT_RESOLVER_PACKAGE || owner == SYSTEM_SCOPE_PACKAGE) return
        Throwable().stackTrace.asSequence()
            .filter { frame -> frame.className.startsWith(owner) && !frame.className.startsWith("${BuildConfig.APPLICATION_ID}.") }
            .distinctBy { it.className to it.methodName }
            .take(ADAPTIVE_STACK_LIMIT)
            .forEach { frame ->
                val clazz = runCatching { Class.forName(frame.className, false, Thread.currentThread().contextClassLoader) }.getOrNull()
                    ?: return@forEach
                clazz.declaredMethods.asSequence()
                    .filter { method -> method.name == frame.methodName && isAdaptiveListBoundary(method) }
                    .take(ADAPTIVE_METHODS_PER_FRAME)
                    .forEach { method -> installAdaptiveBoundaryHook(method, owner, kind) }
            }
    }

    private fun isAdaptiveListBoundary(method: Method): Boolean {
        if (method.isSynthetic || method.isBridge) return false
        if (List::class.java.isAssignableFrom(method.returnType) || Collection::class.java.isAssignableFrom(method.returnType)) return true
        return method.parameterTypes.any { type -> List::class.java.isAssignableFrom(type) || Collection::class.java.isAssignableFrom(type) }
    }

    private fun installAdaptiveBoundaryHook(method: Method, owner: String, kind: IntentKind) {
        val key = "ADAPTIVE#${method.toGenericString()}"
        if (!adaptiveInstalled.add(key)) return
        runCatching {
            hook(method).setId("adaptive-chooser").intercept(adaptiveBoundaryHooker(method))
            record("ADAPTIVE_HOOK_INSTALLED owner=$owner kind=$kind method=${method.toGenericString()}")
        }.onFailure {
            adaptiveInstalled.remove(key)
            diagnostic("ADAPTIVE_HOOK_FAILED method=${method.name} error=${it.javaClass.name}")
        }
    }

    private fun adaptiveBoundaryHooker(method: Method) = XposedInterface.Hooker { chain ->
        val context = adaptiveContext
        if (context == null || SystemClock.elapsedRealtime() > context.expiresAt || processName.substringBefore(':') != context.ownerPackage) {
            return@Hooker chain.proceed()
        }
        val args = chain.args.toTypedArray()
        var argsChanged = false
        args.forEachIndexed { index, arg ->
            val filtered = adaptiveFilterCollection(arg, context, "arg$index:${method.name}")
            if (filtered != null && filtered !== arg) {
                args[index] = filtered
                argsChanged = true
            }
        }
        val result = if (argsChanged) chain.proceed(args) else chain.proceed()
        adaptiveFilterCollection(result, context, "return:${method.name}") ?: result
    }

    private fun adaptiveFilterCollection(value: Any?, context: AdaptiveContext, stage: String): Any? {
        val values: List<*> = when (value) {
            is List<*> -> value
            is Collection<*> -> value.toList()
            else -> return null
        }
        if (values.size <= 1) return value
        var recognized = 0
        var removed = 0
        val filtered = values.filter { item ->
            val packageName = adaptivePackageName(item)
            if (packageName != null) recognized++
            val hide = packageName != null && packageName in context.selectedPackages
            if (hide) removed++
            !hide
        }
        if (recognized == 0 || removed == 0) {
            diagnostic("ADAPTIVE_NO_CHANGE stage=$stage kind=${context.kind} size=${values.size} recognized=$recognized generation=${context.generation}")
            return value
        }
        if (filtered.isEmpty() && context.kind != IntentKind.PROCESS_TEXT) {
            diagnostic("ADAPTIVE_RESTORE_ALL stage=$stage kind=${context.kind} before=${values.size} generation=${context.generation}")
            return value
        }
        diagnostic("ADAPTIVE_FILTER stage=$stage kind=${context.kind} ${values.size}->${filtered.size} recognized=$recognized generation=${context.generation}")
        return when (value) {
            is ArrayList<*> -> ArrayList(filtered)
            is List<*> -> filtered
            is Collection<*> -> filtered
            else -> value
        }
    }

    private fun adaptivePackageName(value: Any?, depth: Int = 0): String? {
        if (value == null || depth > ADAPTIVE_EXTRACT_DEPTH) return null
        when (value) {
            is ResolveInfo -> return value.activityInfo?.packageName
            is ActivityInfo -> return value.packageName
            is ApplicationInfo -> return value.packageName
            is ComponentName -> return value.packageName
            is Intent -> return value.component?.packageName ?: value.`package`
        }
        val clazz = value.javaClass
        if (clazz.name.startsWith("java.") || clazz.name.startsWith("kotlin.") || clazz.name.startsWith("androidx.compose.")) return null
        ADAPTIVE_FIELD_NAMES.forEach { name ->
            val nested = runCatching {
                var current: Class<*>? = clazz
                while (current != null) {
                    val c = current
                    val field = runCatching { c.getDeclaredField(name) }.getOrNull()
                    if (field != null) {
                        field.isAccessible = true
                        return@runCatching field.get(value)
                    }
                    current = c.superclass
                }
                null
            }.getOrNull()
            if (nested is String && looksLikePackageName(nested)) return nested
            adaptivePackageName(nested, depth + 1)?.let { return it }
        }
        ADAPTIVE_GETTER_NAMES.forEach { name ->
            val nested = runCatching {
                clazz.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(value)
            }.getOrNull()
            if (nested is String && looksLikePackageName(nested)) return nested
            adaptivePackageName(nested, depth + 1)?.let { return it }
        }
        return null
    }

    private fun looksLikePackageName(value: String): Boolean =
        value.length in 3..200 && '.' in value && value.none { it.isWhitespace() || it == '/' }

'''
assert insert_before in s
s = s.replace(insert_before, block + insert_before)

old = '''        const val PER_USER_RANGE = 100_000
        val SYSTEM_QUERY_CLASSES = listOf(
'''
new = '''        const val PER_USER_RANGE = 100_000
        const val ADAPTIVE_WINDOW_MS = 3_000L
        const val ADAPTIVE_STACK_LIMIT = 12
        const val ADAPTIVE_METHODS_PER_FRAME = 4
        const val ADAPTIVE_EXTRACT_DEPTH = 2
        val ADAPTIVE_FIELD_NAMES = listOf("packageName", "package", "pkg", "resolveInfo", "activityInfo", "applicationInfo", "componentName", "component", "intent", "appInfo")
        val ADAPTIVE_GETTER_NAMES = listOf("getPackageName", "getPackage", "getPkg", "getResolveInfo", "getActivityInfo", "getApplicationInfo", "getComponentName", "getComponent", "getIntent", "getAppInfo")
        val SYSTEM_QUERY_CLASSES = listOf(
'''
assert old in s
s = s.replace(old, new)

p.write_text(s)
