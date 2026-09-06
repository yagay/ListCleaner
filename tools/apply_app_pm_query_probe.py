from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

old = '''        } else if (param.packageName in snapshot.hiddenFromApps) {
            installAppStartProbe(param.classLoader, param.packageName)
        } else {
'''
new = '''        } else if (param.packageName in snapshot.hiddenFromApps) {
            installAppStartProbe(param.classLoader, param.packageName)
            installAppPmQueryProbe(param.classLoader, param.packageName)
        } else {
'''
if old not in s:
    raise SystemExit('source-app package branch not found')
s = s.replace(old, new, 1)

anchor = '    private fun installAppStartProbe(classLoader: ClassLoader, packageName: String) {'
idx = s.find(anchor)
if idx == -1:
    raise SystemExit('installAppStartProbe anchor not found')

probe = r'''    private fun installAppPmQueryProbe(classLoader: ClassLoader, packageName: String) {
        val className = "android.content.pm.IPackageManager$Stub$Proxy"
        val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
            record("APP_PM_QUERY_PROBE_CLASS_UNAVAILABLE package=$packageName class=$className error=${it.javaClass.name}")
            return
        }
        var installed = 0
        val methods = generateSequence(clazz as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter(::isQueryIntentActivitiesMethod)
            .distinctBy(Method::toGenericString)
            .toList()
        methods.forEach { method ->
            val key = "APP_PM_QUERY_PROBE#${method.toGenericString()}"
            if (!installedMethods.add(key)) return@forEach
            runCatching {
                method.isAccessible = true
                hook(method).setId(APP_PM_QUERY_PROBE_HOOK_ID).intercept(appPmQueryProbeHooker(packageName))
                installed++
                record("APP_PM_QUERY_PROBE_HOOK_INSTALLED package=$packageName method=${method.toGenericString()}")
            }.onFailure {
                installedMethods.remove(key)
                record("APP_PM_QUERY_PROBE_HOOK_FAILED package=$packageName method=${method.toGenericString()} error=${it.javaClass.name}")
            }
        }
        record("APP_PM_QUERY_PROBE_READY package=$packageName hooks=$installed")
    }

    private fun appPmQueryProbeHooker(packageName: String) = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent
        val result = chain.proceed()
        if (intent != null) {
            runCatching {
                val extracted = extractListResult(result)
                val values = extracted?.values.orEmpty()
                val preview = values.asSequence().mapNotNull { value ->
                    val info = value as? ResolveInfo ?: return@mapNotNull null
                    val activity = info.activityInfo ?: return@mapNotNull null
                    "${activity.packageName}/${activity.name}"
                }.take(16).joinToString(",")
                diagnostic(
                    "APP_PM_QUERY_PROBE package=$packageName action=${intent.action ?: "-"} " +
                        "component=${intent.component?.flattenToShortString() ?: "-"} pkg=${intent.`package` ?: "-"} " +
                        "type=${intent.type ?: "-"} dataScheme=${intent.data?.scheme ?: "-"} " +
                        "flags=0x${intent.flags.toString(16)} result=${result?.javaClass?.name ?: "null"} " +
                        "size=${values.size} preview=[$preview]"
                )
            }.onFailure {
                diagnostic("APP_PM_QUERY_PROBE_FAILED package=$packageName error=${it.javaClass.name}")
            }
        }
        result
    }

'''
s = s[:idx] + probe + s[idx:]

const_anchor = '        const val CHOOSER_DISCOVERY_HOOK_ID = "ic-adaptive-chooser-discovery"\n'
if const_anchor not in s:
    raise SystemExit('chooser hook id constant not found')
s = s.replace(
    const_anchor,
    const_anchor + '        const val APP_PM_QUERY_PROBE_HOOK_ID = "ic-app-pm-query-probe"\n',
    1,
)

p.write_text(s)
print('patched app-side IPackageManager query probe')
