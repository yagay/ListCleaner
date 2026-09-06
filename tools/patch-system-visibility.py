from pathlib import Path

p = Path('app/src/main/java/com/yagay/ListCleaner/xposed/ListCleanerModule.kt')
s = p.read_text()

# Update architecture documentation.
s = s.replace('''/**
 * Level 1 filters PackageManager resolver results. Level 2 gives manually scoped third-party
 * apps a process-wide virtual package visibility view so preloaded/cached app lists cannot
 * reintroduce hidden targets. Real package/component state is never changed; failures fail-open.
 */''','''/**
 * Level 1 filters PackageManager resolver results. Level 2 runs only in system_server and adds
 * caller-aware Android package-visibility filtering for apps selected in the hidden-app list.
 * Third-party apps do not need LSPosed scope. Real package/component state is never changed.
 */''')

# Rule snapshot receives caller list from atomic remote config.
s = s.replace('''        val diagnostic: Boolean,
        val managerAppId: Int = -1,
        val digest: String = ""
''','''        val diagnostic: Boolean,
        val managerAppId: Int = -1,
        val digest: String = "",
        val hiddenFromApps: Set<String> = emptySet()
''')

# System-server install also installs package visibility hooks.
s = s.replace('''        record("SYSTEM_HOOKS new=$installed total=${installedMethods.size}")
    }

    private fun installResolverClientHooks''','''        record("SYSTEM_HOOKS new=$installed total=${installedMethods.size}")
        installSystemVisibilityHooks(classLoader)
    }

    private fun installSystemVisibilityHooks(classLoader: ClassLoader) {
        var installed = 0
        SYSTEM_VISIBILITY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrElse {
                diagnostic("VISIBILITY_CLASS_UNAVAILABLE class=$className error=${it.javaClass.name}")
                return@forEach
            }
            val methods = runCatching {
                generateSequence(clazz as Class<*>?) { it.superclass }
                    .flatMap { it.declaredMethods.asSequence() }
                    .filter { method ->
                        method.name == "shouldFilterApplication" &&
                            method.returnType == Boolean::class.javaPrimitiveType
                    }.distinctBy(Method::toGenericString).toList()
            }.getOrElse {
                record("VISIBILITY_DISCOVERY_FAILED class=$className error=${it.javaClass.name}")
                emptyList()
            }
            methods.forEach { method ->
                val key = "VISIBILITY#${method.toGenericString()}"
                if (!installedMethods.add(key)) return@forEach
                runCatching {
                    method.isAccessible = true
                    hook(method).setId(VISIBILITY_HOOK_ID).intercept(systemVisibilityHooker())
                    installed++
                }.onFailure {
                    installedMethods.remove(key)
                    record("VISIBILITY_HOOK_FAILED method=${method.toGenericString()} error=${it.javaClass.name}")
                }
            }
        }
        record("VISIBILITY_HOOKS new=$installed callers=${snapshot.hiddenFromApps.size} targets=${snapshot.allSelectedPackages.size}")
    }

    private fun systemVisibilityHooker() = XposedInterface.Hooker { chain ->
        pollPreferences()
        val current = snapshot
        // Package-level hiding is intentionally limited to HIDE_SELECTED. SHOW_SELECTED at package
        // visibility level would hide unrelated packages and can break the caller process.
        if (current.displayMode != DisplayMode.HIDE_SELECTED ||
            current.hiddenFromApps.isEmpty() || current.allSelectedPackages.isEmpty()) {
            return@Hooker chain.proceed()
        }

        val args = chain.args
        val callingUid = (args.getOrNull(1) as? Int)
            ?: args.filterIsInstance<Int>().firstOrNull { it >= 10_000 }
            ?: return@Hooker chain.proceed()
        if (callingUid < 10_000) return@Hooker chain.proceed()

        val computer = args.firstOrNull { value -> value != null && hasGetPackagesForUid(value.javaClass) }
            ?: return@Hooker chain.proceed()
        val callers = packagesForUid(computer, callingUid)
        if (callers.isEmpty() || callers.none { it in current.hiddenFromApps }) return@Hooker chain.proceed()

        val target = packageNameFromVisibilityArgs(args) ?: return@Hooker chain.proceed()
        if (target in callers || target == MANAGER_PACKAGE || target !in current.allSelectedPackages) {
            return@Hooker chain.proceed()
        }

        diagnostic("SYSTEM_VISIBILITY_FILTER uid=$callingUid caller=${callers.sorted()} target=$target")
        true
    }

    private fun hasGetPackagesForUid(clazz: Class<*>): Boolean =
        generateSequence(clazz as Class<*>?) { it.superclass }.any { current ->
            current.declaredMethods.any { method ->
                method.name == "getPackagesForUid" && method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType
            }
        }

    private fun packagesForUid(computer: Any, uid: Int): Set<String> {
        val method = generateSequence(computer.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { candidate ->
                candidate.name == "getPackagesForUid" && candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0] == Int::class.javaPrimitiveType
            } ?: return emptySet()
        val identity = Binder.clearCallingIdentity()
        return try {
            method.isAccessible = true
            when (val result = method.invoke(computer, uid)) {
                is Array<*> -> result.filterIsInstance<String>().toSet()
                is Collection<*> -> result.filterIsInstance<String>().toSet()
                else -> emptySet()
            }
        } catch (failure: Throwable) {
            diagnostic("SYSTEM_VISIBILITY_CALLER_FAILED uid=$uid error=${failure.javaClass.name}")
            emptySet()
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun packageNameFromVisibilityArgs(args: List<Any?>): String? {
        // Android 13+ AppsFilterImpl currently carries the target package state near index 3.
        val preferred = args.getOrNull(3)?.let(::packageNameFromState)
        if (!preferred.isNullOrBlank()) return preferred
        return args.asSequence().mapNotNull(::packageNameFromState).firstOrNull()
    }

    private fun packageNameFromState(value: Any?): String? {
        if (value == null || value is Number || value is Boolean || value is ClassLoader) return null
        if (value is String) return value.takeIf(::looksLikePackageName)

        val classes = generateSequence(value.javaClass as Class<*>?) { it.superclass }.toList()
        val getter = classes.asSequence().flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { method ->
                method.parameterTypes.isEmpty() && method.returnType == String::class.java &&
                    method.name in setOf("getPackageName", "getName")
            }
        runCatching {
            getter?.isAccessible = true
            (getter?.invoke(value) as? String)?.takeIf(::looksLikePackageName)
        }.getOrNull()?.let { return it }

        val field = classes.asSequence().flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.type == String::class.java && it.name in setOf("mName", "name", "packageName", "mPackageName") }
        return runCatching {
            field?.isAccessible = true
            (field?.get(value) as? String)?.takeIf(::looksLikePackageName)
        }.getOrNull()
    }

    private fun looksLikePackageName(value: String): Boolean =
        value.length in 3..255 && '.' in value && value.none { it.isWhitespace() || it.isISOControl() }

    private fun installResolverClientHooks''')

# Third-party packages are no longer hook targets. Keep system/resolver only.
s = s.replace('''        } else {
            // Additional LSPosed scope selected by the user: only intercept the app's own
            // PackageManager candidate query. Do not install Resolver UI ordering hooks here.
            installApplicationClientHooks(param.classLoader, param.packageName)
        }
''','''        } else {
            record("PACKAGE_READY_SKIP package=${param.packageName} reason=third_party_scope_not_required")
        }
''')

# Hot reload should not require/install APP layer anymore.
s = s.replace('''                reloadBaseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> "$HOOK_ID-app"
''','''                reloadBaseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> null
''')
s = s.replace('''                baseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> Layer.APP
''','''                baseProcess == SYSTEM_SCOPE_PACKAGE -> null
                else -> null
''')

# Preserve/refresh visibility hook handles during hot reload.
s = s.replace('''                    method != null && handle.id == "ic-final-order" -> {''','''                    method != null && handle.id == VISIBILITY_HOOK_ID && method.name == "shouldFilterApplication" -> {
                        handle.replaceHook(systemVisibilityHooker())
                        installedMethods.add("VISIBILITY#${method.toGenericString()}")
                    }
                    method != null && handle.id == "ic-final-order" -> {''')

# Decode the new caller list from config.
s = s.replace('''                snapshot = RuleSnapshot(config.rules.map { it.id }.toSet(), config.mode,
                    config.priorities, config.diagnostic, config.managerAppId, digest)
''','''                snapshot = RuleSnapshot(config.rules.map { it.id }.toSet(), config.mode,
                    config.priorities, config.diagnostic, config.managerAppId, digest, config.hiddenFromApps)
''')
s = s.replace('''                record("RULES_READ reason=$reason count=${snapshot.configured.size} mode=${config.mode} diagnostic=${config.diagnostic} atomic=true priorities=${config.priorities.apps.mapValues { it.value.size }} digest=$digest")
''','''                record("RULES_READ reason=$reason count=${snapshot.configured.size} mode=${config.mode} diagnostic=${config.diagnostic} atomic=true priorities=${config.priorities.apps.mapValues { it.value.size }} hiddenFromApps=${config.hiddenFromApps.size} digest=$digest")
''')

# Constants.
s = s.replace('''        const val VIRTUAL_OPTIONS_HOOK_ID = "ic-virtual-query-options"
        val SYSTEM_QUERY_CLASSES = listOf(
''','''        const val VIRTUAL_OPTIONS_HOOK_ID = "ic-virtual-query-options"
        const val VISIBILITY_HOOK_ID = "ic-system-package-visibility"
        const val MANAGER_PACKAGE = "com.yagay.ListCleaner"
        val SYSTEM_VISIBILITY_CLASSES = listOf(
            "com.android.server.pm.AppsFilterImpl"
        )
        val SYSTEM_QUERY_CLASSES = listOf(
''')

p.write_text(s)
