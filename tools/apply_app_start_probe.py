from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

old = '''        } else if (param.packageName == SYSTEM_SCOPE_PACKAGE) {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=system_scope_pseudo_process")
        } else {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=third_party_scope_not_required")
        }
'''
new = '''        } else if (param.packageName == SYSTEM_SCOPE_PACKAGE) {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=system_scope_pseudo_process")
        } else if (param.packageName in snapshot.hiddenFromApps) {
            installAppStartProbe(param.classLoader, param.packageName)
        } else {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=third_party_not_source")
        }
'''
if old not in s:
    raise SystemExit('onPackageReady block not found')
s = s.replace(old, new, 1)

anchor = '    private fun installSystemServerQueryHooks(classLoader: ClassLoader) {'
idx = s.index(anchor)
probe = r'''    private fun installAppStartProbe(classLoader: ClassLoader, packageName: String) {
        var installed = 0
        val targets = listOf(
            "android.app.Instrumentation" to setOf("execStartActivity"),
            "android.app.Activity" to setOf("startActivityForResult"),
            "android.app.ContextImpl" to setOf("startActivity"),
        )
        targets.forEach { (className, names) ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                record("APP_START_PROBE_CLASS_UNAVAILABLE package=$packageName class=$className error=${it.javaClass.name}")
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { it.name in names && it.parameterTypes.any(Intent::class.java::isAssignableFrom) }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "APP_START_PROBE#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(APP_START_PROBE_HOOK_ID).intercept(appStartProbeHooker(packageName, className))
                        installed++
                        record("APP_START_PROBE_HOOK_INSTALLED package=$packageName class=$className method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("APP_START_PROBE_HOOK_FAILED package=$packageName class=$className method=${method.toGenericString()} error=${it.javaClass.name}")
                    }
                }
        }
        record("APP_START_PROBE_READY package=$packageName hooks=$installed")
    }

    private fun appStartProbeHooker(packageName: String, stage: String) = XposedInterface.Hooker { chain ->
        runCatching {
            val intent = chain.args.firstOrNull { it is Intent } as? Intent
            if (intent != null) {
                val data = intent.data
                val extras = runCatching { intent.extras?.keySet()?.sorted()?.joinToString(",") }.getOrNull().orEmpty()
                val nested = runCatching {
                    intent.extras?.keySet().orEmpty().mapNotNull { key ->
                        @Suppress("DEPRECATION")
                        val value = intent.extras?.get(key)
                        (value as? Intent)?.let { child ->
                            "$key:${child.action ?: "-"}:${child.component?.flattenToShortString() ?: "-"}:${child.type ?: "-"}:${child.data?.scheme ?: "-"}"
                        }
                    }.joinToString(";")
                }.getOrNull().orEmpty()
                diagnostic(
                    "APP_START_PROBE package=$packageName stage=$stage action=${intent.action ?: "-"} " +
                        "component=${intent.component?.flattenToShortString() ?: "-"} pkg=${intent.`package` ?: "-"} " +
                        "type=${intent.type ?: "-"} dataScheme=${data?.scheme ?: "-"} dataAuthority=${data?.authority ?: "-"} " +
                        "selectorAction=${intent.selector?.action ?: "-"} selectorComponent=${intent.selector?.component?.flattenToShortString() ?: "-"} " +
                        "clip=${intent.clipData?.itemCount ?: 0} flags=0x${intent.flags.toString(16)} extras=[$extras] nested=[$nested]"
                )
            }
        }.onFailure { diagnostic("APP_START_PROBE_FAILED package=$packageName stage=$stage error=${it.javaClass.name}") }
        chain.proceed()
    }

'''
s = s[:idx] + probe + s[idx:]

# Disable the broken system-server chooser rewrite while observing the app's untouched launch path.
old_redirect = '''            val template = learnedChooserTemplates[routeKey]
            if (template != null && tryRedirectLearnedChooser(request, intent, view, component, template, current)) return
'''
new_redirect = '''            val template = learnedChooserTemplates[routeKey]
            if (template != null && current.diagnostic) {
                diagnostic("CHOOSER_SYSTEM_REDIRECT_DISABLED caller=$callerPackage component=${component.flattenToShortString()} reason=app_start_probe")
            }
'''
if old_redirect not in s:
    raise SystemExit('redirect call not found')
s = s.replace(old_redirect, new_redirect, 1)

# Add hook id constant next to existing chooser id.
const_anchor = '        const val CHOOSER_DISCOVERY_HOOK_ID = '
pos = s.find(const_anchor)
if pos == -1:
    raise SystemExit('chooser hook id constant not found')
line_end = s.index('\n', pos)
s = s[:line_end+1] + '        const val APP_START_PROBE_HOOK_ID = "ic-app-start-probe"\n' + s[line_end+1:]

p.write_text(s)
print('patched app-side start probe')

scope = Path('app/src/main/resources/META-INF/xposed/scope.list')
lines = [x.strip() for x in scope.read_text().splitlines() if x.strip()]
if 'com.estrongs.android.pop' not in lines:
    lines.append('com.estrongs.android.pop')
scope.write_text('\n'.join(lines) + '\n')
print('added ES to default debug scope')
