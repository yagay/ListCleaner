from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace(
'''                hook(method).setId(APP_PM_QUERY_PROBE_HOOK_ID).intercept(appPmQueryProbeHooker(packageName))
                installed++
                record("APP_PM_QUERY_PROBE_HOOK_INSTALLED package=$packageName method=${method.toGenericString()}")
''',
'''                hook(method).setId(APP_PM_QUERY_FILTER_HOOK_ID).intercept(appPmQueryFilterHooker(packageName))
                installed++
                record("APP_PM_QUERY_FILTER_HOOK_INSTALLED package=$packageName method=${method.toGenericString()}")
''',
1)
s = s.replace(
'''        record("APP_PM_QUERY_PROBE_READY package=$packageName hooks=$installed")
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
''',
'''        record("APP_PM_QUERY_FILTER_READY package=$packageName hooks=$installed")
    }

    private fun appPmQueryFilterHooker(packageName: String) = XposedInterface.Hooker { chain ->
        val outerIntent = chain.args.firstOrNull { it is Intent } as? Intent
        val intent = outerIntent?.selector ?: outerIntent
        val original = chain.proceed()
        if (intent == null) return@Hooker original
        runCatching {
            pollPreferences()
            val explicit = intent.component != null || intent.`package` != null ||
                outerIntent.component != null || outerIntent.`package` != null
            val kind = intent.intentKind(null)
            if (explicit || kind == null) {
                diagnostic("APP_PM_QUERY_FILTER_SKIP package=$packageName action=${intent.action ?: "-"} explicit=$explicit kind=${kind ?: "-"}")
                return@runCatching original
            }
            val extracted = extractListResult(original) ?: run {
                diagnostic("APP_PM_QUERY_FILTER_SKIP package=$packageName action=${intent.action ?: "-"} reason=unsupported_result result=${original?.javaClass?.name ?: "null"}")
                return@runCatching original
            }
            val callerUid = Process.myUid()
            val replacement = transform(kind, extracted.values, Layer.APP, callerUid)
            if (replacement == null) {
                diagnostic("APP_PM_QUERY_FILTER_NO_CHANGE package=$packageName kind=$kind size=${extracted.values.size}")
                return@runCatching original
            }
            val rebuilt = extracted.rebuild(replacement)
            diagnostic("APP_PM_QUERY_FILTER_APPLIED package=$packageName kind=$kind before=${extracted.values.size} after=${replacement.size} callerUid=$callerUid")
            rebuilt
        }.getOrElse {
            diagnostic("APP_PM_QUERY_FILTER_FAILED package=$packageName error=${it.javaClass.name}; keeping original")
            original
        }
    }
''',
1)

s = s.replace('private enum class Layer { SYSTEM, RESOLVER }', 'private enum class Layer { SYSTEM, RESOLVER, APP }', 1)
s = s.replace('const val APP_PM_QUERY_PROBE_HOOK_ID = "ic-app-pm-query-probe"', 'const val APP_PM_QUERY_FILTER_HOOK_ID = "ic-app-pm-query-filter"', 1)

if 'APP_PM_QUERY_FILTER_APPLIED' not in s:
    raise SystemExit('filter hook replacement failed')
if 'private enum class Layer { SYSTEM, RESOLVER, APP }' not in s:
    raise SystemExit('Layer.APP replacement failed')
if 'APP_PM_QUERY_FILTER_HOOK_ID' not in s:
    raise SystemExit('filter hook id replacement failed')

p.write_text(s)
print('patched app-side IPackageManager query filtering')
