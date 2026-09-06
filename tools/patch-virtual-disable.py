from pathlib import Path

path = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = path.read_text(encoding='utf-8')

# Imports: keep ComponentName for virtual component-state hooks, remove adaptive-only AtomicLong.
s = s.replace('import android.content.pm.ApplicationInfo\nimport android.content.ComponentName\n',
              'import android.content.pm.ApplicationInfo\nimport android.content.pm.PackageManager\nimport android.content.ComponentName\n')
s = s.replace('import java.util.concurrent.ConcurrentHashMap\nimport java.util.concurrent.atomic.AtomicLong\n',
              'import java.util.concurrent.ConcurrentHashMap\n')

# Replace adaptive second-stage state with short-lived virtual-disable session state.
old = '''    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
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
new = '''    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val tracedActions = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var virtualDisableContext: VirtualDisableContext? = null

    private data class VirtualDisableContext(
        val kind: IntentKind,
        val expiresAt: Long,
        val ownerPackage: String,
    )
'''
assert old in s, 'adaptive state block not found'
s = s.replace(old, new)

# Hot reload must accept APP layer handles as compatible, not only resolver handles.
old = '''            check(param.oldHookHandles.any { handle ->
                val method = handle.executable as? Method
                (handle.id == "$HOOK_ID-${if (systemServer) "system" else "resolver"}" &&
                    method != null && isQueryIntentActivitiesMethod(method))
            }) { "No compatible query handle; restart required" }
'''
new = '''            val reloadBaseProcess = processName.substringBefore(':')
            val expectedQueryHookId = when {
                systemServer -> "$HOOK_ID-system"
                reloadBaseProcess == FRAMEWORK_PACKAGE || reloadBaseProcess == INTENT_RESOLVER_PACKAGE -> "$HOOK_ID-resolver"
                reloadBaseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> "$HOOK_ID-app"
            }
            if (expectedQueryHookId != null) {
                check(param.oldHookHandles.any { handle ->
                    val method = handle.executable as? Method
                    handle.id == expectedQueryHookId && method != null && isQueryIntentActivitiesMethod(method)
                }) { "No compatible query handle; restart required" }
            }
'''
assert old in s, 'hot reload compatibility block not found'
s = s.replace(old, new)

# Install virtual component state hooks in every manually scoped third-party app.
old = '''        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->
            if (installHook(method, Layer.APP)) installed++
        }
        record("APP_QUERY_HOOKS package=$packageName new=$installed total=${installedMethods.size}")
    }
'''
new = '''        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->
            if (installHook(method, Layer.APP)) installed++
        }
        record("APP_QUERY_HOOKS package=$packageName new=$installed total=${installedMethods.size}")
        installVirtualComponentHooks(clazz, packageName)
    }

    private fun installVirtualComponentHooks(clazz: Class<*>, packageName: String) {
        var activityHooks = 0
        var stateHooks = 0
        clazz.declaredMethods.filter(::isActivityInfoMethod).forEach { method ->
            val key = "VIRTUAL_ACTIVITY#${method.toGenericString()}"
            if (!installedMethods.add(key)) return@forEach
            runCatching {
                hook(method).setId(VIRTUAL_ACTIVITY_HOOK_ID).intercept(virtualActivityInfoHooker())
                activityHooks++
            }.onFailure {
                installedMethods.remove(key)
                record("VIRTUAL_HOOK_FAILED package=$packageName type=activity_info method=${method.name} error=${it.javaClass.name}")
            }
        }
        clazz.declaredMethods.filter(::isComponentEnabledSettingMethod).forEach { method ->
            val key = "VIRTUAL_STATE#${method.toGenericString()}"
            if (!installedMethods.add(key)) return@forEach
            runCatching {
                hook(method).setId(VIRTUAL_STATE_HOOK_ID).intercept(virtualComponentStateHooker())
                stateHooks++
            }.onFailure {
                installedMethods.remove(key)
                record("VIRTUAL_HOOK_FAILED package=$packageName type=component_state method=${method.name} error=${it.javaClass.name}")
            }
        }
        record("VIRTUAL_HOOKS package=$packageName activityInfo=$activityHooks componentState=$stateHooks")
    }
'''
assert old in s, 'application hook install block not found'
s = s.replace(old, new)

# Replace adaptive arming in APP query path with virtual-disable session arming.
s = s.replace('''        if (layer == Layer.APP) {
            armAdaptiveChooser(kind)
            installAdaptiveChooserHooksFromStack(kind)
''', '''        if (layer == Layer.APP) {
            armVirtualDisable(kind)
''')

# Remove the whole adaptive boundary engine and replace it with virtual component-state behavior.
start = s.index('    private fun armAdaptiveChooser(kind: IntentKind) {')
end = s.index('    private fun isSelectedCandidate(', start)
virtual_block = '''    private fun armVirtualDisable(kind: IntentKind) {
        val owner = processName.substringBefore(':')
        if (owner.isBlank() || owner == FRAMEWORK_PACKAGE || owner == INTENT_RESOLVER_PACKAGE || owner == SYSTEM_SCOPE_PACKAGE) return
        val current = snapshot
        if (current.displayMode == DisplayMode.SHOW_ALL || !current.hasSelection(kind)) {
            virtualDisableContext = null
            return
        }
        virtualDisableContext = VirtualDisableContext(
            kind = kind,
            expiresAt = SystemClock.elapsedRealtime() + VIRTUAL_DISABLE_WINDOW_MS,
            ownerPackage = owner,
        )
        diagnostic("VIRTUAL_DISABLE_ARM kind=$kind owner=$owner windowMs=$VIRTUAL_DISABLE_WINDOW_MS mode=${current.displayMode}")
    }

    private fun activeVirtualDisableContext(): VirtualDisableContext? {
        val context = virtualDisableContext ?: return null
        if (processName.substringBefore(':') != context.ownerPackage || SystemClock.elapsedRealtime() > context.expiresAt) {
            virtualDisableContext = null
            return null
        }
        return context
    }

    private fun virtualSelected(kind: IntentKind, component: ComponentName, current: RuleSnapshot): Boolean {
        if (component.packageName == processName.substringBefore(':')) return false
        val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
            component.packageName, component.className, null
        )
        if ("${kind.name}|${component.packageName}|$canonicalClass" in current.configured) return true
        return current.hasPackageSelection(kind, component.packageName)
    }

    private fun virtualSelected(kind: IntentKind, activity: ActivityInfo, current: RuleSnapshot): Boolean {
        if (activity.packageName == processName.substringBefore(':')) return false
        val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(
            activity.packageName, activity.name, activity.targetActivity
        )
        if ("${kind.name}|${activity.packageName}|$canonicalClass" in current.configured) return true
        return current.hasPackageSelection(kind, activity.packageName)
    }

    private fun shouldVirtuallyDisable(kind: IntentKind, component: ComponentName, current: RuleSnapshot): Boolean {
        if (current.displayMode == DisplayMode.SHOW_ALL || !current.hasSelection(kind)) return false
        val selected = virtualSelected(kind, component, current)
        return !current.displayMode.includes(selected, true)
    }

    private fun shouldVirtuallyDisable(kind: IntentKind, activity: ActivityInfo, current: RuleSnapshot): Boolean {
        if (current.displayMode == DisplayMode.SHOW_ALL || !current.hasSelection(kind)) return false
        val selected = virtualSelected(kind, activity, current)
        return !current.displayMode.includes(selected, true)
    }

    private fun virtualActivityInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val component = chain.args.firstOrNull { it is ComponentName } as? ComponentName ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        val info = original as? ActivityInfo ?: return@Hooker original
        val current = snapshot
        if (!shouldVirtuallyDisable(context.kind, info, current)) return@Hooker original
        diagnostic("VIRTUAL_ACTIVITY_INFO kind=${context.kind} component=${component.flattenToShortString()} enabled=false")
        ActivityInfo(info).apply { enabled = false }
    }

    private fun virtualComponentStateHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val component = chain.args.firstOrNull { it is ComponentName } as? ComponentName ?: return@Hooker chain.proceed()
        val current = snapshot
        if (!shouldVirtuallyDisable(context.kind, component, current)) return@Hooker chain.proceed()
        diagnostic("VIRTUAL_COMPONENT_STATE kind=${context.kind} component=${component.flattenToShortString()} state=DISABLED")
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

'''
s = s[:start] + virtual_block + s[end:]

# Add method recognizers next to the existing query recognizer.
needle = '''    private fun isQueryIntentActivitiesMethod(method: Method): Boolean =
        method.name in setOf("queryIntentActivities", "queryIntentActivitiesAsUser") &&
            method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
            (List::class.java.isAssignableFrom(method.returnType) ||
                method.returnType.name == "android.content.pm.ParceledListSlice")

'''
replacement = needle + '''    private fun isActivityInfoMethod(method: Method): Boolean =
        method.name in setOf("getActivityInfo", "getActivityInfoAsUser") &&
            method.parameterTypes.any { ComponentName::class.java.isAssignableFrom(it) } &&
            ActivityInfo::class.java.isAssignableFrom(method.returnType)

    private fun isComponentEnabledSettingMethod(method: Method): Boolean =
        method.name == "getComponentEnabledSetting" &&
            method.parameterTypes.any { ComponentName::class.java.isAssignableFrom(it) } &&
            method.returnType == Int::class.javaPrimitiveType

'''
assert needle in s, 'query method recognizer not found'
s = s.replace(needle, replacement)

# Remove adaptive constants and add virtual-disable IDs/window.
old = '''        const val PER_USER_RANGE = 100_000
        const val ADAPTIVE_WINDOW_MS = 3_000L
        const val ADAPTIVE_STACK_LIMIT = 12
        const val ADAPTIVE_METHODS_PER_FRAME = 4
        const val ADAPTIVE_EXTRACT_DEPTH = 2
        val ADAPTIVE_FIELD_NAMES = listOf("packageName", "package", "pkg", "resolveInfo", "activityInfo", "applicationInfo", "componentName", "component", "intent", "appInfo")
        val ADAPTIVE_GETTER_NAMES = listOf("getPackageName", "getPackage", "getPkg", "getResolveInfo", "getActivityInfo", "getApplicationInfo", "getComponentName", "getComponent", "getIntent", "getAppInfo")
'''
new = '''        const val PER_USER_RANGE = 100_000
        const val VIRTUAL_ACTIVITY_HOOK_ID = "ic-virtual-activity-info"
        const val VIRTUAL_STATE_HOOK_ID = "ic-virtual-component-state"
        const val VIRTUAL_DISABLE_WINDOW_MS = 8_000L
'''
assert old in s, 'adaptive constants block not found'
s = s.replace(old, new)

# Update module comment to describe the two retained mechanisms.
s = s.replace('''/**
 * Two layers: system_server filters every caller; Resolver processes provide a fallback and
 * UI-only ordering. Every failure is fail-open and returns Android's original result.
 */''', '''/**
 * Level 1 filters PackageManager resolver results. Level 2 gives manually scoped third-party
 * apps a short-lived virtual component-disabled view while their chooser is being built.
 * Real package/component state is never changed; every failure is fail-open.
 */''')

# Guard against accidentally leaving adaptive implementation behind.
for token in ('AdaptiveContext', 'ADAPTIVE_', 'adaptiveChooser', 'adaptiveFilterCollection', 'adaptivePackageName'):
    assert token not in s, f'leftover adaptive token: {token}'

path.write_text(s, encoding='utf-8')

# Remove one-shot/obsolete files that were committed during earlier patch experiments.
for stale in [
    Path('app/adaptive-chooser-build-marker.txt'),
    Path('tools/patch-app-layer.py'),
    Path('.github/workflows/apply-strict-app-layer.yml'),
]:
    if stale.exists():
        stale.unlink()
