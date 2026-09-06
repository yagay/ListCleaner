from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

old_activity = '''    private fun virtualActivityInfoHooker() = XposedInterface.Hooker { chain ->
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
'''
new_activity = '''    private fun virtualActivityInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val component = chain.args.firstOrNull { it is ComponentName } as? ComponentName ?: return@Hooker chain.proceed()
        if (!shouldVirtuallyDisable(context.kind, component, snapshot)) return@Hooker chain.proceed()
        diagnostic("VIRTUAL_ACTIVITY_HIDDEN kind=${context.kind} component=${component.flattenToShortString()} result=NameNotFound")
        throw PackageManager.NameNotFoundException(component.flattenToShortString())
    }
'''
if old_activity not in s:
    raise SystemExit('virtualActivityInfoHooker block not found')
s = s.replace(old_activity, new_activity)

old_application = '''    private fun virtualApplicationInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        val info = original as? ApplicationInfo ?: return@Hooker original
        if (!shouldVirtuallyDisablePackage(context.kind, info.packageName, snapshot)) return@Hooker original
        diagnostic("VIRTUAL_APPLICATION_INFO kind=${context.kind} package=${info.packageName} enabled=false")
        ApplicationInfo(info).apply { enabled = false }
    }
'''
new_application = '''    private fun virtualApplicationInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val packageName = chain.args.firstOrNull { it is String } as? String ?: return@Hooker chain.proceed()
        if (!shouldVirtuallyDisablePackage(context.kind, packageName, snapshot)) return@Hooker chain.proceed()
        diagnostic("VIRTUAL_APPLICATION_HIDDEN kind=${context.kind} package=$packageName result=NameNotFound")
        throw PackageManager.NameNotFoundException(packageName)
    }
'''
if old_application not in s:
    raise SystemExit('virtualApplicationInfoHooker block not found')
s = s.replace(old_application, new_application)

old_package = '''    private fun virtualPackageInfoHooker() = XposedInterface.Hooker { chain ->
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
'''
new_package = '''    private fun virtualPackageInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val context = activeVirtualDisableContext() ?: return@Hooker chain.proceed()
        val packageName = chain.args.firstOrNull { it is String } as? String ?: return@Hooker chain.proceed()
        if (!shouldVirtuallyDisablePackage(context.kind, packageName, snapshot)) return@Hooker chain.proceed()
        diagnostic("VIRTUAL_PACKAGE_HIDDEN kind=${context.kind} package=$packageName result=NameNotFound")
        throw PackageManager.NameNotFoundException(packageName)
    }
'''
if old_package not in s:
    raise SystemExit('virtualPackageInfoHooker block not found')
s = s.replace(old_package, new_package)

# Keep installed-package/application list filtering fail-open: never return an empty synthetic list.
# Direct package/component lookups are stronger: during the short chooser window the target is
# indistinguishable from a package/component that PackageManager cannot see.

p.write_text(s)
