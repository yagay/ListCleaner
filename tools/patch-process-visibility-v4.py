from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace(''' * Level 1 filters PackageManager resolver results. Level 2 gives manually scoped third-party\n * apps a short-lived virtual component-disabled view while their chooser is being built.\n * Real package/component state is never changed; every failure is fail-open.\n''',''' * Level 1 filters PackageManager resolver results. Level 2 gives manually scoped third-party\n * apps a process-wide virtual package visibility view so preloaded/cached app lists cannot\n * reintroduce hidden targets. Real package/component state is never changed; failures fail-open.\n''')

old = '''        fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds\n        fun hasPackageSelection(kind: IntentKind, packageName: String): Boolean = packageName in selectedPackages[kind].orEmpty()\n'''
new = '''        val allSelectedPackages: Set<String> = selectedPackages.values.flatten().toSet()\n        fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds\n        fun hasPackageSelection(kind: IntentKind, packageName: String): Boolean = packageName in selectedPackages[kind].orEmpty()\n'''
if old not in s: raise SystemExit('RuleSnapshot anchor missing')
s = s.replace(old, new)

old = '''    @Volatile private var virtualDisableContext: VirtualDisableContext? = null\n\n    private data class VirtualDisableContext(\n        val kind: IntentKind,\n        val expiresAt: Long,\n        val ownerPackage: String,\n    )\n\n'''
if old not in s: raise SystemExit('context block missing')
s = s.replace(old, '')

s = s.replace('''        if (layer == Layer.APP) {\n            armVirtualDisable(kind)\n''','''        if (layer == Layer.APP) {\n''')

start = s.index('    private fun armVirtualDisable(kind: IntentKind) {')
end = s.index('    private fun isSelectedCandidate(', start)
replacement = r'''    private fun processVisibilityHidden(packageName: String, current: RuleSnapshot): Boolean {
        val owner = processName.substringBefore(':')
        if (packageName.isBlank() || packageName == owner || current.displayMode == DisplayMode.SHOW_ALL) return false
        val selected = packageName in current.allSelectedPackages
        return current.displayMode.includes(selected, current.allSelectedPackages.isNotEmpty()).not()
    }

    private fun virtualActivityInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val component = chain.args.firstOrNull { it is ComponentName } as? ComponentName ?: return@Hooker chain.proceed()
        if (!processVisibilityHidden(component.packageName, snapshot)) return@Hooker chain.proceed()
        diagnostic("PROCESS_VISIBILITY_ACTIVITY_HIDDEN component=${component.flattenToShortString()} result=NameNotFound")
        throw PackageManager.NameNotFoundException(component.flattenToShortString())
    }

    private fun virtualComponentStateHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val component = chain.args.firstOrNull { it is ComponentName } as? ComponentName ?: return@Hooker chain.proceed()
        if (!processVisibilityHidden(component.packageName, snapshot)) return@Hooker chain.proceed()
        diagnostic("PROCESS_VISIBILITY_COMPONENT_STATE component=${component.flattenToShortString()} state=DISABLED")
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun virtualApplicationInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val packageName = chain.args.firstOrNull { it is String } as? String ?: return@Hooker chain.proceed()
        if (!processVisibilityHidden(packageName, snapshot)) return@Hooker chain.proceed()
        diagnostic("PROCESS_VISIBILITY_APPLICATION_HIDDEN package=$packageName result=NameNotFound")
        throw PackageManager.NameNotFoundException(packageName)
    }

    private fun virtualPackageInfoHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val packageName = chain.args.firstOrNull { it is String } as? String ?: return@Hooker chain.proceed()
        if (!processVisibilityHidden(packageName, snapshot)) return@Hooker chain.proceed()
        diagnostic("PROCESS_VISIBILITY_PACKAGE_HIDDEN package=$packageName result=NameNotFound")
        throw PackageManager.NameNotFoundException(packageName)
    }

    private fun virtualInstalledApplicationsHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        val filtered = result.values.filter { value ->
            val info = value as? ApplicationInfo ?: return@filter true
            !processVisibilityHidden(info.packageName, snapshot)
        }
        if (filtered.size == result.values.size || filtered.isEmpty()) return@Hooker original
        diagnostic("PROCESS_VISIBILITY_INSTALLED_APPLICATIONS ${result.values.size}->${filtered.size}")
        runCatching { result.rebuild(filtered) }.getOrDefault(original)
    }

    private fun virtualInstalledPackagesHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        val filtered = result.values.filter { value ->
            val info = value as? PackageInfo ?: return@filter true
            !processVisibilityHidden(info.packageName, snapshot)
        }
        if (filtered.size == result.values.size || filtered.isEmpty()) return@Hooker original
        diagnostic("PROCESS_VISIBILITY_INSTALLED_PACKAGES ${result.values.size}->${filtered.size}")
        runCatching { result.rebuild(filtered) }.getOrDefault(original)
    }

    private fun virtualResolveActivityHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val original = chain.proceed()
        val info = original as? ResolveInfo ?: return@Hooker original
        val activity = info.activityInfo ?: return@Hooker original
        if (!processVisibilityHidden(activity.packageName, snapshot)) return@Hooker original
        diagnostic("PROCESS_VISIBILITY_RESOLVE_ACTIVITY component=${activity.packageName}/${activity.name} result=null")
        null
    }

    private fun virtualQueryIntentActivityOptionsHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val original = chain.proceed()
        val result = extractListResult(original) ?: return@Hooker original
        val filtered = result.values.filter { value ->
            val info = value as? ResolveInfo ?: return@filter true
            val activity = info.activityInfo ?: return@filter true
            !processVisibilityHidden(activity.packageName, snapshot)
        }
        if (filtered.size == result.values.size || filtered.isEmpty()) return@Hooker original
        diagnostic("PROCESS_VISIBILITY_QUERY_OPTIONS ${result.values.size}->${filtered.size}")
        runCatching { result.rebuild(filtered) }.getOrDefault(original)
    }

'''
s = s[:start] + replacement + s[end:]

s = s.replace('''        const val VIRTUAL_DISABLE_WINDOW_MS = 8_000L\n''', '')

# Log capability at install time so diagnostics prove process-wide mode is active before chooser usage.
s = s.replace('''        record("VIRTUAL_HOOKS package=$packageName activityInfo=$activityHooks componentState=$stateHooks applicationInfo=$applicationHooks packageInfo=$packageHooks installedApps=$installedApplicationHooks installedPackages=$installedPackageHooks resolveActivity=$resolveHooks queryOptions=$optionHooks")\n''','''        record("VIRTUAL_HOOKS package=$packageName mode=process_wide activityInfo=$activityHooks componentState=$stateHooks applicationInfo=$applicationHooks packageInfo=$packageHooks installedApps=$installedApplicationHooks installedPackages=$installedPackageHooks resolveActivity=$resolveHooks queryOptions=$optionHooks")\n''')

for token in ['VirtualDisableContext', 'virtualDisableContext', 'armVirtualDisable', 'activeVirtualDisableContext', 'VIRTUAL_DISABLE_WINDOW_MS', 'shouldVirtuallyDisablePackage', 'shouldVirtuallyDisable(', 'virtualSelected(']:
    if token in s:
        raise SystemExit(f'obsolete token remains: {token}')

p.write_text(s)
