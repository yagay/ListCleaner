from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

s = s.replace(
'''        } else if (param.packageName in snapshot.hiddenFromApps) {
            installAppStartProbe(param.classLoader, param.packageName)
            installAppPmQueryProbe(param.classLoader, param.packageName)
        } else {
''',
'''        } else if (param.packageName in snapshot.hiddenFromApps) {
            installAppStartProbe(param.classLoader, param.packageName)
            installAppPmQueryProbe(param.classLoader, param.packageName)
            installAppMenuConsumerProbe(param.classLoader, param.packageName)
        } else {
''',
1)

anchor = '    private fun installAppStartProbe(classLoader: ClassLoader, packageName: String) {'
idx = s.find(anchor)
if idx < 0:
    raise SystemExit('installAppStartProbe anchor not found')

probe = r'''    private fun installAppMenuConsumerProbe(classLoader: ClassLoader, packageName: String) {
        var installed = 0
        val targets = listOf(
            "android.widget.ListView" to "setAdapter",
            "android.widget.GridView" to "setAdapter",
            "androidx.recyclerview.widget.RecyclerView" to "setAdapter",
        )
        targets.forEach { (className, methodName) ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                diagnostic("APP_MENU_CONSUMER_CLASS_UNAVAILABLE package=$packageName class=$className")
                return@forEach
            }
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter { method -> method.name == methodName && method.parameterTypes.size == 1 }
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = "APP_MENU_CONSUMER#${method.toGenericString()}"
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(APP_MENU_CONSUMER_HOOK_ID)
                            .intercept(appMenuConsumerProbeHooker(packageName, className))
                        installed++
                        record("APP_MENU_CONSUMER_HOOK_INSTALLED package=$packageName class=$className method=${method.toGenericString()}")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("APP_MENU_CONSUMER_HOOK_FAILED package=$packageName class=$className error=${it.javaClass.name}")
                    }
                }
        }
        record("APP_MENU_CONSUMER_READY package=$packageName hooks=$installed")
    }

    private fun appMenuConsumerProbeHooker(packageName: String, stage: String) = XposedInterface.Hooker { chain ->
        val adapter = chain.args.firstOrNull()
        val result = chain.proceed()
        if (adapter != null) runCatching {
            val summaries = inspectAdapterCollections(adapter)
            diagnostic(
                "APP_MENU_CONSUMER package=$packageName stage=$stage adapter=${adapter.javaClass.name} " +
                    "collections=${summaries.size} data=[${summaries.joinToString(" | ")}]"
            )
        }.onFailure {
            diagnostic("APP_MENU_CONSUMER_FAILED package=$packageName stage=$stage adapter=${adapter.javaClass.name} error=${it.javaClass.name}")
        }
        result
    }

    private fun inspectAdapterCollections(adapter: Any): List<String> {
        val summaries = mutableListOf<String>()
        allInstanceFields(adapter.javaClass).asSequence()
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .take(APP_MENU_MAX_FIELDS)
            .forEach { field ->
                if (summaries.size >= APP_MENU_MAX_COLLECTIONS) return@forEach
                val value = runCatching {
                    field.isAccessible = true
                    field.get(adapter)
                }.getOrNull() ?: return@forEach
                when (value) {
                    is Collection<*> -> summarizeMenuCollection(field.name, value)?.let(summaries::add)
                    is Map<*, *> -> summarizeMenuCollection(field.name + ".values", value.values)?.let(summaries::add)
                    is Array<*> -> summarizeMenuCollection(field.name, value.asList())?.let(summaries::add)
                }
            }
        return summaries
    }

    private fun summarizeMenuCollection(fieldName: String, values: Collection<*>): String? {
        if (values.isEmpty()) return null
        val samples = values.asSequence().take(APP_MENU_MAX_ITEMS).mapNotNull(::describeMenuValue).toList()
        if (samples.isEmpty()) return null
        val itemTypes = values.asSequence().take(APP_MENU_MAX_ITEMS).mapNotNull { it?.javaClass?.name }.distinct().take(4).joinToString(",")
        return "$fieldName(size=${values.size},types=[$itemTypes],samples=[${samples.joinToString(",")}])"
    }

    private fun describeMenuValue(value: Any?): String? {
        value ?: return null
        return when (value) {
            is ResolveInfo -> value.activityInfo?.let { "ResolveInfo:${it.packageName}/${it.name}" }
            is ActivityInfo -> "ActivityInfo:${value.packageName}/${value.name}"
            is ComponentName -> "Component:${value.flattenToShortString()}"
            is Intent -> "Intent:${value.action ?: "-"}:${value.component?.flattenToShortString() ?: "-"}:${value.`package` ?: "-"}:${value.type ?: "-"}:${value.data?.scheme ?: "-"}"
            is String -> value.takeIf(::looksLikePackageName)?.let { "String:$it" }
            else -> describeMenuModel(value)
        }
    }

    private fun describeMenuModel(value: Any): String? {
        val hits = mutableListOf<String>()
        allInstanceFields(value.javaClass).asSequence()
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .take(APP_MENU_MAX_MODEL_FIELDS)
            .forEach { field ->
                if (hits.size >= APP_MENU_MAX_MODEL_HITS) return@forEach
                val nested = runCatching {
                    field.isAccessible = true
                    field.get(value)
                }.getOrNull() ?: return@forEach
                val hit = when (nested) {
                    is ResolveInfo -> nested.activityInfo?.let { "${field.name}=RI:${it.packageName}/${it.name}" }
                    is ActivityInfo -> "${field.name}=AI:${nested.packageName}/${nested.name}"
                    is ComponentName -> "${field.name}=CN:${nested.flattenToShortString()}"
                    is Intent -> "${field.name}=I:${nested.action ?: "-"}:${nested.component?.flattenToShortString() ?: "-"}:${nested.`package` ?: "-"}"
                    is String -> nested.takeIf(::looksLikePackageName)?.let { "${field.name}=S:$it" }
                    else -> null
                }
                if (hit != null) hits += hit
            }
        return hits.takeIf { it.isNotEmpty() }?.let { "${value.javaClass.name}{${it.joinToString(";")}}" }
    }

'''
s = s[:idx] + probe + s[idx:]

const_anchor = '        const val APP_PM_QUERY_FILTER_HOOK_ID = "ic-app-pm-query-filter"\n'
if const_anchor not in s:
    raise SystemExit('APP_PM_QUERY_FILTER_HOOK_ID constant anchor not found')
s = s.replace(
    const_anchor,
    const_anchor + '''        const val APP_MENU_CONSUMER_HOOK_ID = "ic-app-menu-consumer-probe"\n        const val APP_MENU_MAX_FIELDS = 48\n        const val APP_MENU_MAX_COLLECTIONS = 12\n        const val APP_MENU_MAX_ITEMS = 12\n        const val APP_MENU_MAX_MODEL_FIELDS = 24\n        const val APP_MENU_MAX_MODEL_HITS = 6\n''',
    1,
)

if 'APP_MENU_CONSUMER_READY' not in s:
    raise SystemExit('consumer probe insertion failed')
p.write_text(s)
print('patched generic app menu consumer probe')
