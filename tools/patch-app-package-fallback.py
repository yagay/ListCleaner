from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

old = '''        val selectedKinds: Set<IntentKind> = selectedKinds(configured)\n        fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds\n    }'''
new = '''        val selectedKinds: Set<IntentKind> = selectedKinds(configured)\n        private val selectedPackages: Map<IntentKind, Set<String>> = configured.mapNotNull { id ->\n            val parts = id.split('|', limit = 3)\n            val kind = parts.getOrNull(0)?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() } ?: return@mapNotNull null\n            val packageName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null\n            kind to packageName\n        }.groupBy({ it.first }, { it.second }).mapValues { (_, packages) -> packages.toSet() }\n        fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds\n        fun hasPackageSelection(kind: IntentKind, packageName: String): Boolean = packageName in selectedPackages[kind].orEmpty()\n    }'''
assert old in s
s = s.replace(old, new, 1)

old = '''            loaders.forEach {\n                if (systemServer) {\n                    installSystemServerQueryHooks(it)\n                } else {\n                    val baseProcess = processName.substringBefore(':')\n                    if (baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE) {\n                        installResolverClientHooks(it)\n                    } else {\n                        installApplicationClientHooks(it, baseProcess)\n                    }\n                }\n            }\n            check(installedMethods.any {\n                it.endsWith(if (systemServer) "@SYSTEM" else if (processName.substringBefore(':') == FRAMEWORK_PACKAGE || processName.substringBefore(':') == INTENT_RESOLVER_PACKAGE) "@RESOLVER" else "@APP")\n            }) { "No query hooks after reload; restart required" }'''
new = '''            val baseProcess = processName.substringBefore(':')\n            val expectedLayer = when {\n                systemServer -> Layer.SYSTEM\n                baseProcess == FRAMEWORK_PACKAGE || baseProcess == INTENT_RESOLVER_PACKAGE -> Layer.RESOLVER\n                baseProcess == SYSTEM_SCOPE_PACKAGE -> null\n                else -> Layer.APP\n            }\n            loaders.forEach {\n                when (expectedLayer) {\n                    Layer.SYSTEM -> installSystemServerQueryHooks(it)\n                    Layer.RESOLVER -> installResolverClientHooks(it)\n                    Layer.APP -> installApplicationClientHooks(it, baseProcess)\n                    null -> record("HOT_RELOAD_SKIP package=$baseProcess reason=system_scope_pseudo_process")\n                }\n            }\n            if (expectedLayer != null) {\n                check(installedMethods.any { it.endsWith("@$expectedLayer") }) {\n                    "No query hooks after reload; restart required"\n                }\n            }'''
assert old in s
s = s.replace(old, new, 1)

old = '''        if (param.packageName == FRAMEWORK_PACKAGE || param.packageName == INTENT_RESOLVER_PACKAGE) {\n            installResolverClientHooks(param.classLoader)\n        } else {\n            // Additional LSPosed scope selected by the user: only intercept the app's own\n            // PackageManager candidate query. Do not install Resolver UI ordering hooks here.\n            installApplicationClientHooks(param.classLoader, param.packageName)\n        }'''
new = '''        if (param.packageName == FRAMEWORK_PACKAGE || param.packageName == INTENT_RESOLVER_PACKAGE) {\n            installResolverClientHooks(param.classLoader)\n        } else if (param.packageName == SYSTEM_SCOPE_PACKAGE) {\n            record("PACKAGE_READY_SKIP package=${param.packageName} reason=system_scope_pseudo_process")\n        } else {\n            // Additional LSPosed scope selected by the user: only intercept the app's own\n            // PackageManager candidate query. Do not install Resolver UI ordering hooks here.\n            installApplicationClientHooks(param.classLoader, param.packageName)\n        }'''
assert old in s
s = s.replace(old, new, 1)

old = '''            val hasMatchingRule = extracted.values.any { value ->\n                val info = value as? ResolveInfo ?: return@any false\n                val activity = info.activityInfo ?: return@any false\n                val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(\n                    activity.packageName, activity.name, activity.targetActivity\n                )\n                "${kind.name}|${activity.packageName}|$canonicalClass" in snapshot.configured\n            }\n            if (!hasMatchingRule) {\n                diagnostic("APP_SKIP kind=$kind reason=no_matching_rule count=${extracted.values.size}")\n                return original\n            }'''
new = '''            val hasMatchingRule = extracted.values.any { value ->\n                val info = value as? ResolveInfo ?: return@any false\n                val activity = info.activityInfo ?: return@any false\n                isSelectedCandidate(kind, activity, snapshot, Layer.APP)\n            }\n            if (!hasMatchingRule) {\n                diagnostic("APP_SKIP kind=$kind reason=no_component_or_package_match count=${extracted.values.size}")\n                return original\n            }'''
assert old in s
s = s.replace(old, new, 1)

old = '''                val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(\n                    activity.packageName, activity.name, activity.targetActivity\n                )\n                val selected = "${kind.name}|${activity.packageName}|$canonicalClass" in current.configured\n                diagnostic(\n                    "CANDIDATE $layer $kind ${activity.packageName}/${activity.name} target=${activity.targetActivity} canonical=$canonicalClass selected=$selected",\n                    detail = true\n                )'''
new = '''                val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(\n                    activity.packageName, activity.name, activity.targetActivity\n                )\n                val exactSelected = "${kind.name}|${activity.packageName}|$canonicalClass" in current.configured\n                val packageFallback = layer == Layer.APP && !exactSelected && current.hasPackageSelection(kind, activity.packageName)\n                val selected = exactSelected || packageFallback\n                diagnostic(\n                    "CANDIDATE $layer $kind ${activity.packageName}/${activity.name} target=${activity.targetActivity} canonical=$canonicalClass selected=$selected match=${if (exactSelected) "component" else if (packageFallback) "package" else "none"}",\n                    detail = true\n                )'''
assert old in s
s = s.replace(old, new, 1)

marker = '''    private fun transform(kind: IntentKind, values: List<*>, layer: Layer, callerUid: Int): List<*>? {'''
helper = '''    private fun isSelectedCandidate(kind: IntentKind, activity: ActivityInfo, current: RuleSnapshot, layer: Layer): Boolean {\n        val canonicalClass = com.yagay.ListCleaner.domain.ComponentIdentity.canonicalClassName(\n            activity.packageName, activity.name, activity.targetActivity\n        )\n        if ("${kind.name}|${activity.packageName}|$canonicalClass" in current.configured) return true\n        return layer == Layer.APP && current.hasPackageSelection(kind, activity.packageName)\n    }\n\n'''
assert marker in s
s = s.replace(marker, helper + marker, 1)

old = '''        const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"\n        const val SYSTEM_UI_PACKAGE = "com.android.systemui"'''
new = '''        const val INTENT_RESOLVER_PACKAGE = "com.android.intentresolver"\n        const val SYSTEM_SCOPE_PACKAGE = "system"\n        const val SYSTEM_UI_PACKAGE = "com.android.systemui"'''
assert old in s
s = s.replace(old, new, 1)

p.write_text(s)
