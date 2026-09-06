from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace('''                val layer = when (handle.id) {\n                    "$HOOK_ID-system" -> Layer.SYSTEM\n                    "$HOOK_ID-resolver" -> Layer.RESOLVER\n                    else -> null\n                }''','''                val layer = when (handle.id) {\n                    "$HOOK_ID-system" -> Layer.SYSTEM\n                    "$HOOK_ID-resolver" -> Layer.RESOLVER\n                    "$HOOK_ID-app" -> Layer.APP\n                    else -> null\n                }''')

s = s.replace('''                    if (baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE) {\n                        installResolverClientHooks(it)\n                    } else {\n                        installApplicationClientHooks(it, baseProcess)\n                    }''','''                    if (baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE) {\n                        installResolverClientHooks(it)\n                    } else {\n                        installApplicationClientHooks(it, baseProcess)\n                    }''')

s = s.replace('''        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->\n            if (installHook(method, Layer.RESOLVER)) installed++\n        }\n        record("APP_QUERY_HOOKS package=$packageName new=$installed total=${installedMethods.size}")''','''        clazz.declaredMethods.filter(::isQueryIntentActivitiesMethod).forEach { method ->\n            if (installHook(method, Layer.APP)) installed++\n        }\n        record("APP_QUERY_HOOKS package=$packageName new=$installed total=${installedMethods.size}")''')

s = s.replace('''            check(installedMethods.any {\n                it.endsWith(if (systemServer) "@SYSTEM" else "@RESOLVER")\n            }) { "No query hooks after reload; restart required" }''','''            check(installedMethods.any {\n                it.endsWith(if (systemServer) "@SYSTEM" else if (processName.substringBefore(':') == FRAMEWORK_PACKAGE || processName.substringBefore(':') == INTENT_RESOLVER_PACKAGE) "@RESOLVER" else "@APP")\n            }) { "No query hooks after reload; restart required" }''')

needle = '''        val extracted = extractListResult(original) ?: run {\n            diagnostic("skip $layer ${intent.action}: unsupported result ${original?.javaClass?.name}")\n            return original\n        }\n        val replacement = transform(kind, extracted.values, layer, callerUid) ?: return original'''
repl = '''        val extracted = extractListResult(original) ?: run {\n            diagnostic("skip $layer ${intent.action}: unsupported result ${original?.javaClass?.name}")\n            return original\n        }\n        if (layer == Layer.APP) {\n            if (extracted.values.size <= 1) {\n                diagnostic("APP_SKIP kind=$kind reason=single_or_empty_candidate count=${extracted.values.size}")\n                return original\n            }\n            if (!snapshot.hasSelection(kind)) {\n                diagnostic("APP_SKIP kind=$kind reason=no_rules_for_kind")\n                return original\n            }\n            val hasMatchingRule = extracted.values.any { value ->\n                val info = value as? ResolveInfo ?: return@any false\n                val activity = info.activityInfo ?: return@any false\n                val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(\n                    activity.packageName, activity.name, activity.targetActivity\n                )\n                "${kind.name}|${activity.packageName}|$canonicalClass" in snapshot.configured\n            }\n            if (!hasMatchingRule) {\n                diagnostic("APP_SKIP kind=$kind reason=no_matching_rule count=${extracted.values.size}")\n                return original\n            }\n        }\n        val replacement = transform(kind, extracted.values, layer, callerUid) ?: return original'''
if needle not in s:
    raise SystemExit('processQuery patch point not found')
s = s.replace(needle, repl)

# Add APP enum member, preserving existing enum declaration style.
if 'enum class Layer' in s and 'APP' not in s[s.index('enum class Layer'):s.index('enum class Layer')+120]:
    s = s.replace('enum class Layer { SYSTEM, RESOLVER }', 'enum class Layer { SYSTEM, RESOLVER, APP }')

p.write_text(s)
