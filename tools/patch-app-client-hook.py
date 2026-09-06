#!/usr/bin/env python3
from pathlib import Path

path = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
text = path.read_text(encoding='utf-8')

old_hot = '''            loaders.forEach {
                if (systemServer) installSystemServerQueryHooks(it)
                else installResolverClientHooks(it)
            }
'''
new_hot = '''            loaders.forEach {
                if (systemServer) {
                    installSystemServerQueryHooks(it)
                } else {
                    val baseProcess = processName.substringBefore(':')
                    if (baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE) {
                        installResolverClientHooks(it)
                    } else {
                        installApplicationClientHooks(it, baseProcess)
                    }
                }
            }
'''
if old_hot not in text:
    raise SystemExit('hot reload install block not found')
text = text.replace(old_hot, new_hot, 1)

old_ready = '''        if (param.packageName == FRAMEWORK_PACKAGE || param.packageName == INTENT_RESOLVER_PACKAGE) {
            installResolverClientHooks(param.classLoader)
        }
'''
new_ready = '''        if (param.packageName == FRAMEWORK_PACKAGE || param.packageName == INTENT_RESOLVER_PACKAGE) {
            installResolverClientHooks(param.classLoader)
        } else {
            // Additional LSPosed scope selected by the user: only intercept the app's own
            // PackageManager candidate query. Do not install Resolver UI ordering hooks here.
            installApplicationClientHooks(param.classLoader, param.packageName)
        }
'''
if old_ready not in text:
    raise SystemExit('package ready block not found')
text = text.replace(old_ready, new_ready, 1)

anchor = '''    private fun installFinalOrderingHooks(loader: ClassLoader) {
'''
addition = '''    private fun installApplicationClientHooks(classLoader: ClassLoader, packageName: String) {
        val clazz = runCatching {
            Class.forName("android.app.ApplicationPackageManager", false, classLoader)
        }.getOrElse {
            record("APP_QUERY_CLASS_UNAVAILABLE package=$packageName error=${it.javaClass.name}")
            return
        }
        var installed = 0
        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->
            if (installHook(method, Layer.RESOLVER)) installed++
        }
        record("APP_QUERY_HOOKS package=$packageName new=$installed total=${installedMethods.size}")
    }

'''
if anchor not in text:
    raise SystemExit('ordering anchor not found')
text = text.replace(anchor, addition + anchor, 1)

path.write_text(text, encoding='utf-8')
