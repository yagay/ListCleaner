from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise RuntimeError(f'target not found in {path}: {old[:120]!r}')
    p.write_text(s.replace(old, new, 1))

# Pure signature parser, testable without loading Xposed classes.
Path('app/src/main/java/com/yagay/ListCleaner/domain/VisibilitySignature.kt').write_text('''package com.yagay.ListCleaner.domain\n\ndata class VisibilityLayout(\n    val uidIndex: Int,\n    val callerSettingIndex: Int?,\n    val targetIndex: Int,\n)\n\n/** Parses known AOSP/OEM AppsFilter signatures without assuming fixed argument positions. */\nobject VisibilitySignature {\n    fun parse(typeNames: List<String>): VisibilityLayout? {\n        if (typeNames.isEmpty()) return null\n        val intIndices = typeNames.indices.filter { typeNames[it] == "int" || typeNames[it] == "java.lang.Integer" }\n        if (intIndices.size < 2) return null\n        val uidIndex = intIndices.first()\n        val targetIndex = typeNames.indices.lastOrNull { index ->\n            index > uidIndex && looksLikeTargetState(typeNames[index])\n        } ?: return null\n        val callerSettingIndex = (uidIndex + 1 until targetIndex).firstOrNull { index ->\n            val name = typeNames[index]\n            name != "int" && name != "java.lang.Integer" && name != "boolean" && name != "java.lang.Boolean"\n        }\n        return VisibilityLayout(uidIndex, callerSettingIndex, targetIndex)\n    }\n\n    private fun looksLikeTargetState(name: String): Boolean =\n        name.contains("PackageState", ignoreCase = true) ||\n            name.endsWith("PackageSetting") || name.contains(".PackageSetting")\n}\n''')

Path('app/src/test/java/com/yagay/ListCleaner/domain/VisibilitySignatureTest.kt').write_text('''package com.yagay.ListCleaner.domain\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertNull\nimport org.junit.Test\n\nclass VisibilitySignatureTest {\n    @Test fun android12AppsFilterLayoutIsParsed() {\n        assertEquals(VisibilityLayout(0, 1, 2), VisibilitySignature.parse(listOf(\n            "int", "com.android.server.pm.SettingBase", "com.android.server.pm.PackageSetting", "int"\n        )))\n    }\n\n    @Test fun modernAppsFilterLayoutIsParsed() {\n        assertEquals(VisibilityLayout(1, 2, 3), VisibilitySignature.parse(listOf(\n            "com.android.server.pm.Computer", "int", "java.lang.Object",\n            "com.android.server.pm.pkg.PackageStateInternal", "int"\n        )))\n    }\n\n    @Test fun unknownLayoutFailsOpen() {\n        assertNull(VisibilitySignature.parse(listOf("java.lang.String", "int", "int")))\n    }\n}\n''')

# Manifest: this manager intentionally provides a complete installed-app picker for the HMA fallback.
replace_once(
    'app/src/main/AndroidManifest.xml',
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n',
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>\n'
)

module = 'app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt'
replace_once(
    module,
    'import com.yagay.ListCleaner.domain.ManagerIdentity\n',
    'import com.yagay.ListCleaner.domain.ManagerIdentity\nimport com.yagay.ListCleaner.domain.VisibilityLayout\nimport com.yagay.ListCleaner.domain.VisibilitySignature\n'
)

# Hot reload must rebuild the adapter from the actual hooked method.
replace_once(
    module,
    '''                    method != null && handle.id == VISIBILITY_HOOK_ID && method.name == "shouldFilterApplication" -> {\n                        handle.replaceHook(systemVisibilityHooker())\n                        installedMethods.add("VISIBILITY#${method.toGenericString()}")\n                    }''',
    '''                    method != null && handle.id == VISIBILITY_HOOK_ID && method.name == "shouldFilterApplication" -> {\n                        val adapter = visibilityAdapter(method)\n                        if (adapter != null) {\n                            handle.replaceHook(systemVisibilityHooker(adapter))\n                            installedMethods.add("VISIBILITY#${method.toGenericString()}")\n                        } else {\n                            record("VISIBILITY_RELOAD_SKIP method=${method.toGenericString()} reason=unsupported_signature")\n                            handle.unhook()\n                        }\n                    }'''
)

# Install only signatures we can identify safely.
replace_once(
    module,
    '''            methods.forEach { method ->\n                val key = "VISIBILITY#${method.toGenericString()}"\n                if (!installedMethods.add(key)) return@forEach\n                runCatching {\n                    method.isAccessible = true\n                    hook(method).setId(VISIBILITY_HOOK_ID).intercept(systemVisibilityHooker())\n                    installed++\n                }.onFailure {''',
    '''            methods.forEach { method ->\n                val adapter = visibilityAdapter(method)\n                if (adapter == null) {\n                    record("VISIBILITY_HOOK_SKIP method=${method.toGenericString()} reason=unsupported_signature")\n                    return@forEach\n                }\n                val key = "VISIBILITY#${method.toGenericString()}"\n                if (!installedMethods.add(key)) return@forEach\n                runCatching {\n                    method.isAccessible = true\n                    hook(method).setId(VISIBILITY_HOOK_ID).intercept(systemVisibilityHooker(adapter))\n                    installed++\n                    record("VISIBILITY_HOOK_INSTALLED method=${method.toGenericString()} uidIndex=${adapter.uidIndex} callerIndex=${adapter.callerSettingIndex} targetIndex=${adapter.targetIndex}")\n                }.onFailure {'''
)

# Replace runtime positional guessing with the install-time adapter and Android 12 caller-setting fallback.
start = '''    private fun systemVisibilityHooker() = XposedInterface.Hooker { chain ->\n'''
end = '''    private fun hasGetPackagesForUid(clazz: Class<*>): Boolean =\n'''
s = Path(module).read_text()
if start not in s or end not in s:
    raise RuntimeError('visibility hook block markers not found')
a = s.index(start)
b = s.index(end, a)
new_block = '''    private fun visibilityAdapter(method: Method): VisibilityLayout? =\n        VisibilitySignature.parse(method.parameterTypes.map { it.name })\n\n    private fun systemVisibilityHooker(adapter: VisibilityLayout) = XposedInterface.Hooker { chain ->\n        pollPreferences()\n        val current = snapshot\n        // Package-level hiding is intentionally limited to HIDE_SELECTED. SHOW_SELECTED at package\n        // visibility level would hide unrelated packages and can break the caller process.\n        if (current.displayMode != DisplayMode.HIDE_SELECTED ||\n            current.hiddenFromApps.isEmpty() || current.allSelectedPackages.isEmpty()) {\n            return@Hooker chain.proceed()\n        }\n\n        val args = chain.args\n        val callingUid = args.getOrNull(adapter.uidIndex) as? Int ?: return@Hooker chain.proceed()\n        if (callingUid < 10_000) return@Hooker chain.proceed()\n\n        // Newer Android exposes a Computer/PackageDataSnapshot object; Android 12 does not, so\n        // fall back to the calling SettingBase/PackageSetting supplied by AppsFilter itself.\n        val computer = args.firstOrNull { value -> value != null && hasGetPackagesForUid(value.javaClass) }\n        val callers = if (computer != null) packagesForUid(computer, callingUid)\n            else packageNamesFromCallerSetting(adapter.callerSettingIndex?.let(args::getOrNull))\n        if (callers.isEmpty() || callers.none { it in current.hiddenFromApps }) return@Hooker chain.proceed()\n\n        val target = packageNameFromState(args.getOrNull(adapter.targetIndex)) ?: return@Hooker chain.proceed()\n        if (target in callers || target == MANAGER_PACKAGE || target !in current.allSelectedPackages) {\n            return@Hooker chain.proceed()\n        }\n\n        diagnostic("SYSTEM_VISIBILITY_FILTER uid=$callingUid caller=${callers.sorted()} target=$target")\n        true\n    }\n\n    private fun packageNamesFromCallerSetting(value: Any?): Set<String> {\n        if (value == null) return emptySet()\n        packageNameFromState(value)?.let { return setOf(it) }\n        val classes = generateSequence(value.javaClass as Class<*>?) { it.superclass }.toList()\n        val result = linkedSetOf<String>()\n        classes.asSequence().flatMap { it.declaredFields.asSequence() }\n            .filter { field ->\n                val name = field.name.lowercase()\n                "package" in name || "packages" in name\n            }.take(12).forEach { field ->\n                runCatching {\n                    field.isAccessible = true\n                    when (val nested = field.get(value)) {\n                        is Array<*> -> nested.asSequence()\n                        is Collection<*> -> nested.asSequence()\n                        is Map<*, *> -> nested.values.asSequence()\n                        else -> emptySequence()\n                    }.mapNotNull(::packageNameFromState).forEach(result::add)\n                }\n            }\n        return result\n    }\n\n'''
Path(module).write_text(s[:a] + new_block + s[b:])

# Remove the old target-index fallback helper entirely.
s = Path(module).read_text()
old = '''    private fun packageNameFromVisibilityArgs(args: List<Any?>): String? {\n        // Android 13+ AppsFilterImpl currently carries the target package state near index 3.\n        val preferred = args.getOrNull(3)?.let(::packageNameFromState)\n        if (!preferred.isNullOrBlank()) return preferred\n        return args.asSequence().mapNotNull(::packageNameFromState).firstOrNull()\n    }\n\n'''
if old not in s:
    raise RuntimeError('old packageNameFromVisibilityArgs block not found')
Path(module).write_text(s.replace(old, '', 1))

# Android 12 + newer class names.
replace_once(
    module,
    '''        val SYSTEM_VISIBILITY_CLASSES = listOf(\n            "com.android.server.pm.AppsFilterImpl"\n        )''',
    '''        val SYSTEM_VISIBILITY_CLASSES = listOf(\n            "com.android.server.pm.AppsFilterImpl",\n            "com.android.server.pm.AppsFilter"\n        )'''
)

# Dead package-fallback diagnostic branch from the removed app-process experiment.
replace_once(
    module,
    '''                val exactSelected = "${kind.name}|${activity.packageName}|$canonicalClass" in current.configured\n                val packageFallback = false\n                val selected = exactSelected || packageFallback\n                diagnostic(\n                    "CANDIDATE $layer $kind ${activity.packageName}/${activity.name} target=${activity.targetActivity} canonical=$canonicalClass selected=$selected match=${if (exactSelected) "component" else if (packageFallback) "package" else "none"}",\n                    detail = true\n                )''',
    '''                val selected = "${kind.name}|${activity.packageName}|$canonicalClass" in current.configured\n                diagnostic(\n                    "CANDIDATE $layer $kind ${activity.packageName}/${activity.name} target=${activity.targetActivity} canonical=$canonicalClass selected=$selected match=${if (selected) "component" else "none"}",\n                    detail = true\n                )'''
)

# Atomic rule inversion: one preference write / revision / remote sync.
repo = 'app/src/main/java/com/yagay/ListCleaner/data/RuleRepository.kt'
replace_once(
    repo,
    '''    @Synchronized fun setSelected(rules: Collection<ComponentRule>, selected: Boolean) {\n        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }\n        val next = mutableRules.value.toMutableSet().apply {\n            if (selected) addAll(valid) else removeAll(valid.toSet())\n        }.toSet()\n        updateRules(next)\n    }\n''',
    '''    @Synchronized fun setSelected(rules: Collection<ComponentRule>, selected: Boolean) {\n        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }\n        val next = mutableRules.value.toMutableSet().apply {\n            if (selected) addAll(valid) else removeAll(valid.toSet())\n        }.toSet()\n        updateRules(next)\n    }\n\n    @Synchronized fun invertSelected(rules: Collection<ComponentRule>) {\n        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }.distinct()\n        if (valid.isEmpty()) return\n        val next = mutableRules.value.toMutableSet().apply {\n            valid.forEach { rule -> if (!add(rule)) remove(rule) }\n        }.toSet()\n        updateRules(next)\n    }\n'''
)

vm = 'app/src/main/java/com/yagay/ListCleaner/ui/MainViewModel.kt'
replace_once(
    vm,
    '''    fun invertRules(rules: Collection<ComponentRule>) {\n        if (!canEdit() || rules.isEmpty()) return\n        val current = app.rules.rules.value\n        val unique = rules.distinct()\n        val select = unique.filter { it !in current }\n        val unselect = unique.filter { it in current }\n        if (select.isNotEmpty()) app.rules.setSelected(select, true)\n        if (unselect.isNotEmpty()) app.rules.setSelected(unselect, false)\n    }''',
    '''    fun invertRules(rules: Collection<ComponentRule>) {\n        if (!canEdit() || rules.isEmpty()) return\n        app.rules.invertSelected(rules)\n    }'''
)

# Clarify the fallback is HIDE_SELECTED-only in both entry and picker descriptions.
tabs = 'app/src/main/java/com/yagay/ListCleaner/ui/MainTabs.kt'
replace_once(
    tabs,
    '''Text("勾选立即保存；该功能通过 android/system_server 的系统级应用可见性过滤实现，属于包级隐藏，不区分同一应用内的单个 Activity 或组件。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)''',
    '''Text("勾选立即保存；仅在“隐藏选中”模式生效。该功能通过 android/system_server 的系统级应用可见性过滤实现，属于包级隐藏，不区分同一应用内的单个 Activity 或组件。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'''
)
picker = 'app/src/main/java/com/yagay/ListCleaner/ui/AppScopePicker.kt'
replace_once(
    picker,
    '''"这里选择的是“从哪些应用中隐藏规则目标”。这些应用不需要加入 LSPosed Hook 作用域；List Cleaner 只在 system/system_server 侧应用隐藏。勾选立即保存。",''',
    '''"这里选择的是“从哪些应用中隐藏规则目标”。仅在“隐藏选中”模式生效；这些应用不需要加入 LSPosed Hook 作用域，List Cleaner 只在 system/system_server 侧应用包级隐藏。勾选立即保存。",'''
)

print('HMA compatibility hardening applied')
