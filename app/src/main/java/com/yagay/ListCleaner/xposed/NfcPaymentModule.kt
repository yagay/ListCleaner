package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.os.Binder
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.ENTRY_SURFACE_DEFINITIONS
import com.yagay.ListCleaner.domain.EntryAuthority
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ManagerIdentity
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Filters the NFC service's authoritative CATEGORY_PAYMENT list. */
class NfcPaymentModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        EntryPolicyProvider(
            preferences = preferences,
            kinds = NFC_KINDS,
            includePriorities = true,
            record = ::record,
        )
    }
    private val runtimeCapture by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RuntimeInterfaceCaptureWriter(::record)
    }
    private val authorityWriter by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AuthoritySnapshotWriter(preferences, ::record)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != NFC_PACKAGE) return
        var installed = 0
        val classes = discoverManagerClasses(param.classLoader)
        classes.forEach { clazz ->
            runCatching { clazz.declaredMethods.asSequence().filter(::isPaymentServicesMethod).toList() }
                .getOrDefault(emptyList())
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installedMethods.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).setId(HOOK_ID).intercept(paymentServicesHooker())
                        installed++
                        record("HOOK_INSTALLED class=${clazz.name} method=$key")
                    }.onFailure {
                        installedMethods.remove(key)
                        record("HOOK_FAILED class=${clazz.name} method=$key error=${it.javaClass.name}")
                    }
                }
        }
        if (installed > 0) {
            policyProvider.start()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        } else if (installedMethods.isEmpty()) {
            record("HOOK_FAILED package=${param.packageName} reason=payment_getServices_not_found classes=${classes.joinToString { it.name }}")
        }
    }

    /**
     * AOSP exposes INfcCardEmulation#getServices from CardEmulationManager$CardEmulationInterface,
     * not from the outer manager itself. Walk nested classes so OEM builds keep working even when
     * the Binder implementation class name changes while the manager structure remains compatible.
     */
    private fun discoverManagerClasses(classLoader: ClassLoader): List<Class<*>> {
        val discovered = linkedSetOf<Class<*>>()
        fun collect(clazz: Class<*>) {
            if (!discovered.add(clazz)) return
            runCatching { clazz.declaredClasses.toList() }
                .getOrDefault(emptyList())
                .forEach(::collect)
        }
        NFC_MANAGER_ROOT_CLASSES.forEach { className ->
            val root = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
            if (root == null) {
                record("CLASS_UNAVAILABLE class=$className")
            } else {
                collect(root)
            }
        }
        return discovered.toList()
    }

    private fun isPaymentServicesMethod(method: Method): Boolean =
        method.name == "getServices" &&
            List::class.java.isAssignableFrom(method.returnType) &&
            method.parameterTypes.any { it == String::class.java }

    private fun paymentServicesHooker() = XposedInterface.Hooker { chain ->
        val category = chain.args.firstOrNull { it is String } as? String
            ?: return@Hooker chain.proceed()
        if (category != PAYMENT_CATEGORY) return@Hooker chain.proceed()

        // AOSP's getServices(String category, int userId) starts with a String.
        // The old firstOrNull() cast to Int silently skipped this normal signature.
        val userId = chain.args.filterIsInstance<Int>().firstOrNull()
            ?.takeIf { it in 0..21474 } ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        val components = values.mapNotNull(::componentOf)
        runCatching {
            authorityWriter.replaceComponents(IntentKind.NFC_HCE, components, userId = userId)
        }.onFailure {
            // Older LSPosed remote preferences can be read-only. Never let optional
            // discovery persistence prevent live filtering or payment list capture.
            record("AUTHORITY_CACHE_UNAVAILABLE user=$userId error=${it.javaClass.simpleName}")
        }

        val callerUid = Binder.getCallingUid()
        val callerPid = Binder.getCallingPid()
        val policy = policyProvider.snapshot(userId)
        val selected = policy.selected(IntentKind.NFC_HCE)
        val priorities = policy.priorities(IntentKind.NFC_HCE)
        val localInternalCall = callerPid == Process.myPid()
        val managerQuery = ManagerIdentity.matches(callerUid, policy.managerAppId)
        val needsChanges = (policy.modeFor(IntentKind.NFC_HCE) != DisplayMode.SHOW_ALL &&
            selected.isNotEmpty()) || priorities.isNotEmpty()

        // Preserve the existing privilege bypass: a manager's discovery query must
        // not be restricted, and NFC's internal requests must not be mutated.
        val filtered = if (localInternalCall || managerQuery || !needsChanges) values else {
            val kept = values.filter { value ->
                val component = componentOf(value) ?: return@filter true
                val rule = ComponentRule(IntentKind.NFC_HCE, component.packageName,
                    component.className)
                policy.modeFor(IntentKind.NFC_HCE) == DisplayMode.SHOW_ALL ||
                    policy.modeFor(IntentKind.NFC_HCE).includes(
                        rule.id in selected, selected.isNotEmpty()
                    )
            }
            com.yagay.ListCleaner.domain.prioritizeApps(
                kept, priorities, { componentOf(it)?.packageName ?: "" }, { userId },
            )
        }

        // Always publish external CATEGORY_PAYMENT results, even without any selected
        // rule or when the returned list is empty. One snapshot per queried user
        // allows a personal PayPal and a managed-profile PayPal to coexist.
        if (!localInternalCall && !managerQuery) {
            val returnedComponents = filtered.mapNotNull(::componentOf)
            runtimeCapture.publish(
                kind = IntentKind.NFC_HCE,
                source = "NFC_PAYMENT_FILTERED_RETURN",
                userId = userId,
                packages = returnedComponents.map { it.packageName },
                components = returnedComponents.map { it.flattenToString() },
                componentLabels = componentLabels(filtered),
                callerPackage = processName,
                callerUid = callerUid,
                requestAction = "android.nfc.cardemulation.payment",
            )
            record("PAYMENT_RETURN user=$userId callerUid=$callerUid count=${returnedComponents.size} filtered=${filtered != values}")
        }
        if (filtered == values) original else {
            record("FILTER category=$category user=$userId callerUid=$callerUid before=${values.size} after=${filtered.size} selected=${selected.size}")
            ArrayList(filtered)
        }
    }

    private fun componentLabels(values: List<*>): Map<String, String> {
        // Labels from the system's ApduServiceInfo ResolveInfo are preferable to
        // package labels. An absent label is resolved by the manager preview later.
        val pm = runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val method = clazz.getDeclaredMethod("currentApplication").apply {
                isAccessible = true
            }
            (method.invoke(null) as? android.content.Context)?.packageManager
        }.getOrNull()
        return values.mapNotNull { service ->
            val component = componentOf(service) ?: return@mapNotNull null
            val info = ReflectionAccess.invokeNoArg(service, "getServiceInfo")
                as? android.content.pm.ResolveInfo
            val label = pm?.let { runCatching { info?.loadLabel(it)?.toString() }.getOrNull() }
                .orEmpty().ifBlank {
                    (ReflectionAccess.invokeNoArg(service, "getDescription") as? String).orEmpty()
                }.trim()
            component.flattenToString().takeIf { label.isNotEmpty() }?.let { it to label }
        }.toMap()
    }

    private fun componentOf(value: Any?): ComponentName? =
        ReflectionAccess.invokeNoArg(value, "getComponent") as? ComponentName

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.NfcPayment"
        const val HOOK_ID = "lc-nfc-payment-services"
        const val NFC_PACKAGE = "com.android.nfc"
        const val PAYMENT_CATEGORY = "payment"
        val NFC_KINDS = ENTRY_SURFACE_DEFINITIONS.values.asSequence()
            .filter { it.authority == EntryAuthority.NFC_CARD_EMULATION }
            .mapTo(linkedSetOf()) { it.kind }
        val NFC_MANAGER_ROOT_CLASSES = listOf(
            "com.android.nfc.cardemulation.CardEmulationManager",
        )
    }
}

