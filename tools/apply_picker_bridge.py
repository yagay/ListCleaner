from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

old = '    private data class SelfChooserSession(val kind: IntentKind, val startedAt: Long, val component: String)\n    private val recentSelfChooserSessions = ConcurrentHashMap<String, SelfChooserSession>()\n'
new = '    private data class SelfChooserSession(val kind: IntentKind, val startedAt: Long, val component: String, val targetIntent: Intent)\n    private val recentSelfChooserSessions = ConcurrentHashMap<String, SelfChooserSession>()\n    private val pickerBridgeLaunched = ConcurrentHashMap<String, Long>()\n'
assert old in s
s = s.replace(old, new, 1)

old = '            installAppStartProbe(param.classLoader, param.packageName)\n            installAppPmQueryProbe(param.classLoader, param.packageName)\n            installAppMenuConsumerProbe(param.classLoader, param.packageName)\n'
new = '            installAppStartProbe(param.classLoader, param.packageName)\n            installAppPickerBridge(param.classLoader, param.packageName)\n            installAppPmQueryProbe(param.classLoader, param.packageName)\n            installAppMenuConsumerProbe(param.classLoader, param.packageName)\n'
assert old in s
s = s.replace(old, new, 1)

old = '''                        recentSelfChooserSessions[packageName] = SelfChooserSession(\n                            childKind,\n                            SystemClock.elapsedRealtime(),\n                            ownComponent.flattenToShortString(),\n                        )\n'''
new = '''                        recentSelfChooserSessions[packageName] = SelfChooserSession(\n                            childKind,\n                            SystemClock.elapsedRealtime(),\n                            ownComponent.flattenToShortString(),\n                            child,\n                        )\n'''
assert old in s
s = s.replace(old, new, 1)

anchor = '    private fun installSystemServerQueryHooks(classLoader: ClassLoader) {\n'
assert anchor in s
bridge = r'''    private fun installAppPickerBridge(classLoader: ClassLoader, packageName: String) {
        val clazz = runCatching { Class.forName("android.app.Instrumentation", false, classLoader) }.getOrElse {
            record("APP_PICKER_BRIDGE_CLASS_UNAVAILABLE package=$packageName error=${it.javaClass.name}")
            return
        }
        var installed = 0
        clazz.declaredMethods.asSequence()
            .filter { method ->
                method.name == "callActivityOnCreate" &&
                    method.parameterTypes.any { Activity::class.java.isAssignableFrom(it) }
            }
            .distinctBy(Method::toGenericString)
            .forEach { method ->
                val key = "APP_PICKER_CREATE#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(APP_PICKER_BRIDGE_HOOK_ID).intercept(appPickerCreateHooker(packageName))
                    installed++
                    record("APP_PICKER_BRIDGE_CREATE_HOOK_INSTALLED package=$packageName method=${method.toGenericString()}")
                }.onFailure {
                    installedMethods.remove(key)
                    record("APP_PICKER_BRIDGE_CREATE_HOOK_FAILED package=$packageName error=${it.javaClass.name}")
                }
            }
        clazz.declaredMethods.asSequence()
            .filter { method ->
                method.name == "callActivityOnActivityResult" &&
                    method.parameterTypes.any { Activity::class.java.isAssignableFrom(it) } &&
                    method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) }
            }
            .distinctBy(Method::toGenericString)
            .forEach { method ->
                val key = "APP_PICKER_RESULT#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                val activityIndex = method.parameterTypes.indexOfFirst { Activity::class.java.isAssignableFrom(it) }
                val intIndices = method.parameterTypes.indices.filter { method.parameterTypes[it] == Int::class.javaPrimitiveType }
                val requestIndex = intIndices.getOrNull(0) ?: return@forEach
                val resultIndex = intIndices.getOrNull(1) ?: return@forEach
                val intentIndex = method.parameterTypes.indexOfLast { Intent::class.java.isAssignableFrom(it) }
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(APP_PICKER_BRIDGE_HOOK_ID).intercept(
                        appPickerResultHooker(packageName, activityIndex, requestIndex, resultIndex, intentIndex)
                    )
                    installed++
                    record("APP_PICKER_BRIDGE_RESULT_HOOK_INSTALLED package=$packageName method=${method.toGenericString()}")
                }.onFailure {
                    installedMethods.remove(key)
                    record("APP_PICKER_BRIDGE_RESULT_HOOK_FAILED package=$packageName error=${it.javaClass.name}")
                }
            }
        record("APP_PICKER_BRIDGE_READY package=$packageName hooks=$installed")
    }

    private fun appPickerCreateHooker(packageName: String) = XposedInterface.Hooker { chain ->
        val result = chain.proceed()
        runCatching {
            if (!snapshot.diagnostic) return@runCatching
            val activity = chain.args.firstOrNull { it is Activity } as? Activity ?: return@runCatching
            val session = recentSelfChooserSessions[packageName] ?: return@runCatching
            val age = SystemClock.elapsedRealtime() - session.startedAt
            if (age !in 0..SELF_CHOOSER_SESSION_TTL_MS) return@runCatching
            val activityComponent = activity.componentName?.flattenToShortString() ?: return@runCatching
            if (activityComponent != session.component) return@runCatching
            val launchKey = "$packageName|${session.component}|${session.startedAt}"
            if (pickerBridgeLaunched.putIfAbsent(launchKey, SystemClock.elapsedRealtime()) != null) return@runCatching

            val queryIntent = Intent(session.targetIntent).apply {
                setComponent(null)
                setPackage(null)
            }
            val picker = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, queryIntent)
            }
            diagnostic(
                "APP_PICKER_BRIDGE_LAUNCH package=$packageName component=$activityComponent kind=${session.kind} " +
                    "targetAction=${queryIntent.action ?: "-"} targetType=${queryIntent.type ?: "-"} targetScheme=${queryIntent.data?.scheme ?: "-"}"
            )
            activity.startActivityForResult(picker, APP_PICKER_REQUEST_CODE)
        }.onFailure {
            diagnostic("APP_PICKER_BRIDGE_LAUNCH_FAILED package=$packageName error=${it.javaClass.name}")
        }
        result
    }

    private fun appPickerResultHooker(
        packageName: String,
        activityIndex: Int,
        requestIndex: Int,
        resultIndex: Int,
        intentIndex: Int,
    ) = XposedInterface.Hooker { chain ->
        runCatching {
            val requestCode = chain.args.getOrNull(requestIndex) as? Int ?: return@runCatching
            if (requestCode != APP_PICKER_REQUEST_CODE) return@runCatching
            val activity = chain.args.getOrNull(activityIndex) as? Activity ?: return@runCatching
            val resultCode = chain.args.getOrNull(resultIndex) as? Int ?: Activity.RESULT_CANCELED
            val data = chain.args.getOrNull(intentIndex) as? Intent
            val session = recentSelfChooserSessions[packageName]
            val chosen = data?.component ?: runCatching {
                @Suppress("DEPRECATION")
                data?.extras?.get(Intent.EXTRA_CHOSEN_COMPONENT) as? ComponentName
            }.getOrNull()
            diagnostic(
                "APP_PICKER_BRIDGE_RESULT package=$packageName activity=${activity.componentName?.flattenToShortString() ?: "-"} " +
                    "resultCode=$resultCode component=${chosen?.flattenToShortString() ?: "-"} action=${data?.action ?: "-"} " +
                    "extras=${runCatching { data?.extras?.keySet()?.sorted()?.joinToString(",") }.getOrNull().orEmpty()}"
            )
            if (resultCode != Activity.RESULT_OK || chosen == null || session == null) return@runCatching
            val age = SystemClock.elapsedRealtime() - session.startedAt
            if (age !in 0..SELF_CHOOSER_SESSION_TTL_MS) return@runCatching
            if (activity.componentName?.flattenToShortString() != session.component) return@runCatching

            val target = Intent(session.targetIntent).apply { setComponent(chosen) }
            diagnostic(
                "APP_PICKER_BRIDGE_DISPATCH package=$packageName component=${chosen.flattenToShortString()} " +
                    "action=${target.action ?: "-"} type=${target.type ?: "-"} scheme=${target.data?.scheme ?: "-"} callerActivity=${session.component}"
            )
            activity.startActivity(target)
            activity.finish()
            recentSelfChooserSessions.remove(packageName, session)
        }.onFailure {
            diagnostic("APP_PICKER_BRIDGE_RESULT_FAILED package=$packageName error=${it.javaClass.name}")
        }
        chain.proceed()
    }

'''
s = s.replace(anchor, bridge + anchor, 1)

old = '        const val APP_START_PROBE_HOOK_ID = "ic-app-start-probe"\n        const val SELF_CHOOSER_SESSION_TTL_MS = 8_000L\n'
new = '        const val APP_START_PROBE_HOOK_ID = "ic-app-start-probe"\n        const val APP_PICKER_BRIDGE_HOOK_ID = "ic-app-picker-bridge"\n        const val APP_PICKER_REQUEST_CODE = 0x4C43\n        const val SELF_CHOOSER_SESSION_TTL_MS = 8_000L\n'
assert old in s
s = s.replace(old, new, 1)

p.write_text(s)
