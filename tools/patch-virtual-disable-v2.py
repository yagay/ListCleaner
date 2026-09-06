from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace('import android.content.pm.ApplicationInfo\nimport android.content.pm.PackageManager\n', 'import android.content.pm.ApplicationInfo\nimport android.content.pm.PackageInfo\nimport android.content.pm.PackageManager\n')

old = '''    private fun installVirtualComponentHooks(clazz: Class<*>, packageName: String) {
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
new = '''    private fun installVirtualComponentHooks(clazz: Class<*>, packageName: String) {
        var activityHooks = 0
        var stateHooks = 0
        var applicationHooks = 0
        var packageHooks = 0
        var installedApplicationHooks = 0
        var installedPackageHooks = 0
        var resolveHooks = 0
        var optionHooks = 0

        fun install(method: Method, keyPrefix: String, id: String, type: String, hooker: XposedInterface.Hooker): Boolean {
            val key = "$keyPrefix#${method.toGenericString()}"
            if (!installedMethods.add(key)) return false
            return runCatching {
                hook(method).setId(id).intercept(hooker)
                true
            }.getOrElse {
                installedMethods.remove(key)
                record("VIRTUAL_HOOK_FAILED package=$packageName type=$type method=${method.name} error=${it.javaClass.name}")
                false
            }
        }

        clazz.declaredMethods.filter(::isActivityInfoMethod).forEach { method ->
            if (install(method, "VIRTUAL_ACTIVITY", VIRTUAL_ACTIVITY_HOOK_ID, "activity_info", virtualActivityInfoHooker())) activityHooks++
        }
        clazz.declaredMethods.filter(::isComponentEnabledSettingMethod).forEach { method ->
            if (install(method, "VIRTUAL_STATE", VIRTUAL_STATE_HOOK_ID, "component_state", virtualComponentStateHooker())) stateHooks++
        }
        clazz.declaredMethods.filter(::isApplicationInfoMethod).forEach { method ->
            if (install(method, "VIRTUAL_APPLICATION", VIRTUAL_APPLICATION_HOOK_ID, "application_info", virtualApplicationInfoHooker())) applicationHooks++
        }
        clazz.declaredMethods.filter(::isPackageInfoMethod).forEach { method ->
            if (install(method, "VIRTUAL_PACKAGE", VIRTUAL_PACKAGE_HOOK_ID, "package_info", virtualPackageInfoHooker())) packageHooks++
        }
        clazz.declaredMethods.filter(::isInstalledApplicationsMethod).forEach { method ->
            if (install(method, "VIRTUAL_INSTALLED_APPS", VIRTUAL_INSTALLED_APPS_HOOK_ID, "installed_applications", virtualInstalledApplicationsHooker())) installedApplicationHooks++
        }
        clazz.declaredMethods.filter(::isInstalledPackagesMethod).forEach { method ->
            if (install(method, "VIRTUAL_INSTALLED_PACKAGES", VIRTUAL_INSTALLED_PACKAGES_HOOK_ID, "installed_packages", virtualInstalledPackagesHooker())) installedPackageHooks++
        }
        clazz.declaredMethods.filter(::isResolveActivityMethod).forEach { method ->
            if (install(method, "VIRTUAL_RESOLVE", VIRTUAL_RESOLVE_HOOK_ID, "resolve_activity", virtualResolveActivityHooker())) resolveHooks++
        }
        clazz.declaredMethods.filter(::isQueryIntentActivityOptionsMethod).forEach { method ->
            if (install(method, "VIRTUAL_OPTIONS", VIRTUAL_OPTIONS_HOOK_ID, "query_intent_activity_options", virtualQueryIntentActivityOptionsHooker())) optionHooks++
        }
        record("VIRTUAL_HOOKS package=$packageName activityInfo=$activityHooks componentState=$stateHooks applicationInfo=$applicationHooks packageInfo=$packageHooks installedApps=$installedApplicationHooks installedPackages=$installedPackageHooks resolveActivity=$resolveHooks queryOptions=$optionHooks")
    }
'''
if old not in s:
    raise SystemExit('installVirtualComponentHooks block not found')
s = s.replace(old, new)

anchor = '''    private fun virtualComponentStateHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val component = chain.args.firstOrNull { it is ComponentName } as? ComponentName ?: return@Hooker chain.proceed()
        val current = snapshot
        if (!shouldVirtuallyDisable(context.kind, component, current)) return@Hooker chain.proceed()
        diagnostic("VIRTUAL_COMPONENT_STATE kind=${context.kind} component=${component.flattenToShortString()} state=DISABLED")
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
'''
insert = anchor + '''
    private fun shouldVirtuallyDisablePackage(kind: IntentKind, packageName: String, current: RuleSnapshot): Boolean {
        if (packageName == processName.substringBefore(':')) return false
        if (current.displayMode == DisplayMode.SHOW_ALL || !current.hasSelection(kind)) return false
        val selected = current.hasPackageSelection(kind, packageName)
        return !current.displayMode.includes(selected, true)
    }

    private fun virtualApplicationInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        val info = original as? ApplicationInfo ?: return@Hooker original
        if (!shouldVirtuallyDisablePackage(context.kind, info.packageName, snapshot)) return@Hooker original
        diagnostic("VIRTUAL_APPLICATION_INFO kind=${context.kind} package=${info.packageName} enabled=false")
        ApplicationInfo(info).apply { enabled = false }
    }

    private fun virtualPackageInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        val info = original as? PackageInfo ?: return@Hooker original
        if (!shouldVirtuallyDisablePackage(context.kind, info.packageName, snapshot)) return@Hooker original
        diagnostic("VIRTUAL_PACKAGE_INFO kind=${context.kind} package=${info.packageName} enabled=false")
        PackageInfo(info).apply {
            applicationInfo = applicationInfo?.let { ApplicationInfo(it).apply { enabled = false } }
            activities = activities?.map { ActivityInfo(it).apply { enabled = false } }?.toTypedArray()
        }
    }

    private fun virtualInstalledApplicationsHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        val filtered = result.values.filter { value ->
            val info = value as? ApplicationInfo ?: return@filter true
            !shouldVirtuallyDisablePackage(context.kind, info.packageName, snapshot)
        }
        if (filtered.size == result.values.size || filtered.isEmpty()) return@Hooker original
        diagnostic("VIRTUAL_INSTALLED_APPLICATIONS kind=${context.kind} ${result.values.size}->${filtered.size}")
        runCatching { result.rebuild(filtered) }.getOrDefault(original)
    }

    private fun virtualInstalledPackagesHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        val filtered = result.values.filter { value ->
            val info = value as? PackageInfo ?: return@filter true
            !shouldVirtuallyDisablePackage(context.kind, info.packageName, snapshot)
        }
        if (filtered.size == result.values.size || filtered.isEmpty()) return@Hooker original
        diagnostic("VIRTUAL_INSTALLED_PACKAGES kind=${context.kind} ${result.values.size}->${filtered.size}")
        runCatching { result.rebuild(filtered) }.getOrDefault(original)
    }

    private fun virtualResolveActivityHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val intent = chain.args.firstOrNull { it is Intent } as? Intent
        val kind = intent?.let { (it.selector ?: it).intentKind(null) }
        if (kind != null && intent?.component == null && intent?.`package` == null) armVirtualDisable(kind)
        val original = chain.proceed()
        val info = original as? ResolveInfo ?: return@Hooker original
        val context = activeVirtualDisableContext() ?: return@Hooker original
        val activity = info.activityInfo ?: return@Hooker original
        if (!shouldVirtuallyDisable(context.kind, activity, snapshot)) return@Hooker original
        diagnostic("VIRTUAL_RESOLVE_ACTIVITY kind=${context.kind} component=${activity.packageName}/${activity.name} result=null")
        null
    }

    private fun virtualQueryIntentActivityOptionsHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val intent = chain.args.firstOrNull { it is Intent } as? Intent
        val kind = intent?.let { (it.selector ?: it).intentKind(null) }
        if (kind != null && intent?.component == null && intent?.`package` == null) armVirtualDisable(kind)
        val original = chain.proceed()
        val context = activeVirtualDisableContext() ?: return@Hooker original
        val result = extractListResult(original) ?: return@Hooker original
        val filtered = result.values.filter { value ->
            val info = value as? ResolveInfo ?: return@filter true
            val activity = info.activityInfo ?: return@filter true
            !shouldVirtuallyDisable(context.kind, activity, snapshot)
        }
        if (filtered.size == result.values.size || FilterPolicy.restoreEmpty(context.kind.name, result.values.size, filtered.size)) return@Hooker original
        diagnostic("VIRTUAL_QUERY_OPTIONS kind=${context.kind} ${result.values.size}->${filtered.size}")
        runCatching { result.rebuild(filtered) }.getOrDefault(original)
    }
'''
if anchor not in s:
    raise SystemExit('virtualComponentStateHooker block not found')
s = s.replace(anchor, insert)

oldpred = '''    private fun isComponentEnabledSettingMethod(method: Method): Boolean =
        method.name == "getComponentEnabledSetting" &&
            method.parameterTypes.any { ComponentName::class.java.isAssignableFrom(it) } &&
            method.returnType == Int::class.javaPrimitiveType
'''
newpred = oldpred + '''
    private fun isApplicationInfoMethod(method: Method): Boolean =
        method.name in setOf("getApplicationInfo", "getApplicationInfoAsUser") &&
            method.parameterTypes.any { it == String::class.java } &&
            ApplicationInfo::class.java.isAssignableFrom(method.returnType)

    private fun isPackageInfoMethod(method: Method): Boolean =
        method.name in setOf("getPackageInfo", "getPackageInfoAsUser") &&
            method.parameterTypes.any { it == String::class.java } &&
            PackageInfo::class.java.isAssignableFrom(method.returnType)

    private fun isInstalledApplicationsMethod(method: Method): Boolean =
        method.name in setOf("getInstalledApplications", "getInstalledApplicationsAsUser") &&
            (List::class.java.isAssignableFrom(method.returnType) || method.returnType.name == "android.content.pm.ParceledListSlice")

    private fun isInstalledPackagesMethod(method: Method): Boolean =
        method.name in setOf("getInstalledPackages", "getInstalledPackagesAsUser") &&
            (List::class.java.isAssignableFrom(method.returnType) || method.returnType.name == "android.content.pm.ParceledListSlice")

    private fun isResolveActivityMethod(method: Method): Boolean =
        method.name in setOf("resolveActivity", "resolveActivityAsUser") &&
            method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
            ResolveInfo::class.java.isAssignableFrom(method.returnType)

    private fun isQueryIntentActivityOptionsMethod(method: Method): Boolean =
        method.name == "queryIntentActivityOptions" &&
            method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
            (List::class.java.isAssignableFrom(method.returnType) || method.returnType.name == "android.content.pm.ParceledListSlice")
'''
if oldpred not in s:
    raise SystemExit('predicate anchor not found')
s = s.replace(oldpred, newpred)

oldconst = '''        const val VIRTUAL_ACTIVITY_HOOK_ID = "ic-virtual-activity-info"
        const val VIRTUAL_STATE_HOOK_ID = "ic-virtual-component-state"
        const val VIRTUAL_DISABLE_WINDOW_MS = 8_000L
'''
newconst = '''        const val VIRTUAL_ACTIVITY_HOOK_ID = "ic-virtual-activity-info"
        const val VIRTUAL_STATE_HOOK_ID = "ic-virtual-component-state"
        const val VIRTUAL_APPLICATION_HOOK_ID = "ic-virtual-application-info"
        const val VIRTUAL_PACKAGE_HOOK_ID = "ic-virtual-package-info"
        const val VIRTUAL_INSTALLED_APPS_HOOK_ID = "ic-virtual-installed-applications"
        const val VIRTUAL_INSTALLED_PACKAGES_HOOK_ID = "ic-virtual-installed-packages"
        const val VIRTUAL_RESOLVE_HOOK_ID = "ic-virtual-resolve-activity"
        const val VIRTUAL_OPTIONS_HOOK_ID = "ic-virtual-query-options"
        const val VIRTUAL_DISABLE_WINDOW_MS = 8_000L
'''
if oldconst not in s:
    raise SystemExit('constant anchor not found')
s = s.replace(oldconst, newconst)

p.write_text(s)
