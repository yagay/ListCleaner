package com.yagay.ListCleaner.data

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.service.voice.VoiceInteractionService
import android.util.Xml
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OBSERVABLE_ENTRY_KINDS
import com.yagay.ListCleaner.domain.ObservedEntryProtocol
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import com.yagay.ListCleaner.domain.isPackageScopedEntry
import com.yagay.ListCleaner.domain.listCleanerAppType
import com.yagay.ListCleaner.domain.webTargetKind
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Discovery that mirrors the Android subsystem which actually owns each Settings list. */
internal class SpecialEntryDiscovery(private val context: Context) {
    private val pm = context.packageManager
    private val flags = PackageManager.MATCH_ALL or PackageManager.GET_META_DATA
    private val observedCache = ObservedEntryCache(context)

    fun scan(): List<ComponentCandidate> {
        val observed = observedRuntimeEntries()
        val liveObservedKinds = observed.asSequence()
            .filterNot { it.unavailable }
            .mapTo(mutableSetOf()) { it.rule.kind }

        val items = buildList {
            addAll(observed)
            addAll(assistantEntries())
            addAll(homeEntries())
            addAll(browserRoleEntries())
            addAll(inputMethodEntries())
            addAll(accessibilityEntries())
            addAll(vpnEntries())
            addAll(autofillEntries())
            addAll(documentProviders())

            // These managers are hidden from a normal app. Once their final system result has been
            // observed, do not mix PackageManager guesses back into the live catalog.
            if (IntentKind.PRINT !in liveObservedKinds) addAll(serviceFallback(IntentKind.PRINT))
            if (IntentKind.CREDENTIAL_PROVIDER !in liveObservedKinds) addAll(credentialProviderFallback())
            if (IntentKind.NFC_HCE !in liveObservedKinds) addAll(nfcPaymentFallback())

            addAll(packageManagerAuthoritativeServiceEntries())
        }
        return mergeSpecialCandidates(items).sortedWith(
            compareBy<ComponentCandidate>(
                { it.rule.kind.ordinal },
                { it.appLabel.lowercase() },
                { it.activityLabel.lowercase() },
            )
        )
    }

    private fun mergeSpecialCandidates(items: List<ComponentCandidate>): List<ComponentCandidate> =
        items.groupBy { it.rule.id }.values.map { matches ->
            val first = matches.firstOrNull { it.isCatalogCandidate } ?: matches.first()
            first.copy(
                evidence = matches.flatMap { it.evidence }.distinct().take(48),
                restricted = matches.all { it.restricted },
                unavailable = matches.all { it.unavailable },
            )
        }

    /** Runtime bridge exposes ShortcutService plus final system-manager observations. */
    @Suppress("DEPRECATION")
    private fun observedRuntimeEntries(): List<ComponentCandidate> {
        val intent = Intent(ObservedEntryProtocol.ACTION).setPackage(ObservedEntryProtocol.PACKAGE)
        val liveRecords = runCatching { pm.queryIntentActivities(intent, flags) }
            .getOrDefault(emptyList())
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                val kind = activity.metaData?.getString(ObservedEntryProtocol.META_KIND)
                    ?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() }
                    ?.takeIf { it in OBSERVABLE_ENTRY_KINDS }
                    ?: return@mapNotNull null
                val rule = ComponentRule(kind, activity.packageName, activity.name)
                if (!rule.isValid()) return@mapNotNull null
                ObservedEntryRecord(
                    kind = kind.name,
                    packageName = rule.packageName,
                    syntheticClass = rule.className,
                    label = resolved.nonLocalizedLabel?.toString().orEmpty(),
                    activityClass = activity.metaData?.getString(ObservedEntryProtocol.META_ACTIVITY),
                    observedAt = System.currentTimeMillis(),
                ).validatedOrNull()
            }
        val liveKeys = liveRecords.mapTo(hashSetOf()) { it.key }
        val records = observedCache.merge(liveRecords)

        return records.mapNotNull { observed ->
            val kind = runCatching { IntentKind.valueOf(observed.kind) }.getOrNull()
                ?.takeIf { it in OBSERVABLE_ENTRY_KINDS }
                ?: return@mapNotNull null
            val rule = ComponentRule(kind, observed.packageName, observed.syntheticClass)
            if (!rule.isValid()) return@mapNotNull null
            val app = runCatching { pm.getApplicationInfo(observed.packageName, 0) }.getOrNull()
                ?: return@mapNotNull null
            val live = observed.key in liveKeys
            ComponentCandidate(
                rule = rule,
                appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(observed.packageName),
                activityLabel = observed.label.ifBlank {
                    observed.activityClass?.substringAfterLast('.') ?: runCatching { app.loadLabel(pm).toString() }
                        .getOrDefault(kind.name)
                },
                appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                appType = app.listCleanerAppType(),
                evidence = buildList {
                    add("AUTHORITY_OBSERVED kind=${kind.name} source=${if (live) "live" else "cache_history"}")
                    observed.activityClass?.takeIf { it.isNotBlank() }?.let { add("component=$it") }
                    add("observedAt=${observed.observedAt}")
                },
                unavailable = !live,
            )
        }
    }

    /** Mirror Android PermissionController's package-level Assistant qualification. */
    @Suppress("DEPRECATION")
    private fun assistantEntries(): List<ComponentCandidate> {
        val evidenceByPackage = linkedMapOf<String, MutableList<String>>()
        val appByPackage = linkedMapOf<String, ApplicationInfo>()
        fun observe(packageName: String, app: ApplicationInfo?, evidence: String) {
            if (packageName.isBlank()) return
            app?.let { appByPackage.putIfAbsent(packageName, it) }
            evidenceByPackage.getOrPut(packageName) { mutableListOf("ASSISTANT_ROLE package_level=true") }
                .add(evidence)
        }

        val activityFlags = PackageManager.MATCH_DEFAULT_ONLY or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_ASSIST), activityFlags) }
            .getOrDefault(emptyList())
            .forEach { resolved ->
                val activity = resolved.activityInfo ?: return@forEach
                val app = activity.applicationInfo ?: return@forEach
                if (!activity.enabled || !app.enabled) return@forEach
                observe(activity.packageName, app, "ASSISTANT_ACTIVITY component=${activity.packageName}/${activity.name}")
            }

        val lowRam = runCatching {
            context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        }.getOrDefault(false)
        if (!lowRam) {
            val serviceFlags = PackageManager.GET_META_DATA or
                PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
            runCatching { pm.queryIntentServices(Intent(VoiceInteractionService.SERVICE_INTERFACE), serviceFlags) }
                .getOrDefault(emptyList())
                .forEach { resolved ->
                    val service = resolved.serviceInfo ?: return@forEach
                    val app = service.applicationInfo ?: return@forEach
                    if (!service.enabled || !app.enabled || !isAssistantVoiceInteractionService(service)) return@forEach
                    observe(
                        service.packageName,
                        app,
                        "ASSISTANT_VOICE_SERVICE component=${service.packageName}/${service.name} supportsAssist=true"
                    )
                }
        }
        return packageCandidates(IntentKind.ASSISTANT, evidenceByPackage, appByPackage)
    }

    /** HOME role requires MAIN + HOME; CATEGORY_DEFAULT is deliberately not required. */
    @Suppress("DEPRECATION")
    private fun homeEntries(): List<ComponentCandidate> {
        val evidenceByPackage = linkedMapOf<String, MutableList<String>>()
        val appByPackage = linkedMapOf<String, ApplicationInfo>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val roleFlags = PackageManager.MATCH_ALL or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        runCatching { pm.queryIntentActivities(intent, roleFlags) }
            .getOrDefault(emptyList())
            .forEach { resolved ->
                val activity = resolved.activityInfo ?: return@forEach
                val app = activity.applicationInfo ?: return@forEach
                if (!activity.enabled || !app.enabled) return@forEach
                appByPackage.putIfAbsent(activity.packageName, app)
                evidenceByPackage.getOrPut(activity.packageName) { mutableListOf("HOME_ROLE package_level=true") }
                    .add("HOME_ACTIVITY component=${activity.packageName}/${activity.name}")
            }
        return packageCandidates(IntentKind.HOME, evidenceByPackage, appByPackage)
    }

    /** BrowserRoleBehavior is package-scoped and uses broad web matching rather than one component. */
    @Suppress("DEPRECATION")
    private fun browserRoleEntries(): List<ComponentCandidate> {
        val evidenceByPackage = linkedMapOf<String, MutableList<String>>()
        val appByPackage = linkedMapOf<String, ApplicationInfo>()
        val roleFlags = PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        listOf("http", "https").forEach { scheme ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://example.com/"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            runCatching { pm.queryIntentActivities(intent, roleFlags) }
                .getOrDefault(emptyList())
                .forEach { resolved ->
                    val activity = resolved.activityInfo ?: return@forEach
                    val app = activity.applicationInfo ?: return@forEach
                    if (!activity.enabled || !app.enabled || resolved.webTargetKind() != IntentKind.BROWSER) return@forEach
                    appByPackage.putIfAbsent(activity.packageName, app)
                    evidenceByPackage.getOrPut(activity.packageName) { mutableListOf("BROWSER_ROLE package_level=true") }
                        .add("BROWSER_ACTIVITY scheme=$scheme component=${activity.packageName}/${activity.name}")
                }
        }
        return packageCandidates(IntentKind.BROWSER, evidenceByPackage, appByPackage)
    }

    /** SettingsLib uses InputMethodManager#getInputMethodList as its source of truth. */
    private fun inputMethodEntries(): List<ComponentCandidate> {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return serviceFallback(IntentKind.INPUT_METHOD)
        val infos = runCatching { manager.inputMethodList }.getOrNull() ?: return serviceFallback(IntentKind.INPUT_METHOD)
        return infos.mapNotNull { info -> inputMethodCandidate(info) }
    }

    private fun inputMethodCandidate(info: InputMethodInfo): ComponentCandidate? {
        val service = info.serviceInfo ?: return null
        val app = service.applicationInfo ?: return null
        val rule = ComponentRule(IntentKind.INPUT_METHOD, service.packageName, service.name)
        if (!rule.isValid()) return null
        return componentCandidate(
            rule,
            app,
            runCatching { info.loadLabel(pm).toString() }.getOrDefault(service.name.substringAfterLast('.')),
            listOf("INPUT_METHOD_MANAGER id=${info.id}", "component=${service.packageName}/${service.name}"),
        )
    }

    /** Mirror AccessibilitySettings: shortcut activities plus manager services, deduped by package+label. */
    private fun accessibilityEntries(): List<ComponentCandidate> {
        val shortcuts = accessibilityShortcutEntries()
        val shortcutPackageLabels = shortcuts.mapTo(hashSetOf()) { it.rule.packageName to it.activityLabel }
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val serviceInfos = runCatching { manager?.installedAccessibilityServiceList }.getOrNull()
            ?: return shortcuts + serviceFallback(IntentKind.ACCESSIBILITY)
        val services = serviceInfos.mapNotNull { info -> accessibilityServiceCandidate(info) }
            .filterNot { it.rule.packageName to it.activityLabel in shortcutPackageLabels }
        return shortcuts + services
    }

    private fun accessibilityServiceCandidate(info: AccessibilityServiceInfo): ComponentCandidate? {
        val service = info.resolveInfo?.serviceInfo ?: return null
        val app = service.applicationInfo ?: return null
        val rule = ComponentRule(IntentKind.ACCESSIBILITY, service.packageName, service.name)
        if (!rule.isValid()) return null
        val label = runCatching { info.resolveInfo?.loadLabel(pm)?.toString() }
            .getOrNull().orEmpty().ifBlank { service.name.substringAfterLast('.') }
        return componentCandidate(
            rule,
            app,
            label,
            listOf("ACCESSIBILITY_MANAGER", "component=${service.packageName}/${service.name}"),
        )
    }

    /** AOSP VpnSettings uses allowed ACTIVATE_VPN/AppOps packages, one row per package. */
    private fun vpnEntries(): List<ComponentCandidate> {
        val outputs = VPN_APP_OPS.map { op -> runReadOnlyRoot("cmd appops query-op --user current $op allow") }
        if (outputs.any { it == null }) return packageScopedServiceFallback(IntentKind.VPN, "VPN_APPOPS unavailable")
        val packages = outputs.filterNotNull().flatMap { output ->
            output.lineSequence().map(String::trim).filter(PACKAGE_NAME::matches).toList()
        }.toSortedSet()
        val evidence = packages.associateWith { mutableListOf("VPN_APP_OPS mode=allow package_level=true") as List<String> }
        return packageCandidates(IntentKind.VPN, evidence, emptyMap())
    }

    /** Android 16 Settings merges autofill rows by package name. */
    @Suppress("DEPRECATION")
    private fun autofillEntries(): List<ComponentCandidate> {
        val definition = SYSTEM_SERVICE_ENTRY_DEFINITIONS.first { it.kind == IntentKind.AUTOFILL }
        val internalCredentialAutofill = defaultCredentialAutofillComponent()
        val evidence = linkedMapOf<String, MutableList<String>>()
        val apps = linkedMapOf<String, ApplicationInfo>()
        runCatching { pm.queryIntentServices(Intent(definition.action), PackageManager.GET_META_DATA) }
            .getOrDefault(emptyList())
            .forEach { resolved ->
                val service = resolved.serviceInfo ?: return@forEach
                val app = service.applicationInfo ?: return@forEach
                if (!definition.acceptsPermission(service.permission)) return@forEach
                if (internalCredentialAutofill == "${service.packageName}/${service.name}") return@forEach
                apps.putIfAbsent(service.packageName, app)
                evidence.getOrPut(service.packageName) {
                    mutableListOf("AUTOFILL_AVAILABLE_SERVICES package_level=true")
                }.add("component=${service.packageName}/${service.name}")
            }
        return packageCandidates(IntentKind.AUTOFILL, evidence, apps)
    }

    private fun defaultCredentialAutofillComponent(): String? = runCatching {
        val id = context.resources.getIdentifier("config_defaultCredentialManagerAutofillService", "string", "android")
        if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }.getOrNull()

    /** DocumentsUI trusts the PROVIDER_INTERFACE PackageManager query; do not invent extra gates. */
    @Suppress("DEPRECATION")
    private fun documentProviders(): List<ComponentCandidate> {
        return runCatching { pm.queryIntentContentProviders(Intent(DocumentsContract.PROVIDER_INTERFACE), flags) }
            .getOrDefault(emptyList())
            .mapNotNull { resolved ->
                val provider = resolved.providerInfo ?: return@mapNotNull null
                val app = provider.applicationInfo ?: return@mapNotNull null
                val rule = ComponentRule(IntentKind.DOCUMENT_PROVIDER, provider.packageName, provider.name)
                if (!rule.isValid()) return@mapNotNull null
                componentCandidate(
                    rule,
                    app,
                    runCatching { provider.loadLabel(pm).toString() }.getOrDefault(provider.name.substringAfterLast('.')),
                    buildList {
                        add("DOCUMENT_PROVIDER authority=PackageManager action=${DocumentsContract.PROVIDER_INTERFACE}")
                        provider.authority?.takeIf { it.isNotBlank() }?.let { add("authority=$it") }
                    },
                )
            }
    }

    /** CredentialManager is authoritative; this PM result is only used before a live manager result is observed. */
    private fun credentialProviderFallback(): List<ComponentCandidate> =
        packageScopedServiceFallback(IntentKind.CREDENTIAL_PROVIDER, "fallback=package_manager")

    /** NFC Settings displays only CATEGORY_PAYMENT. Runtime CardEmulation observation replaces this fallback. */
    @Suppress("DEPRECATION")
    private fun nfcPaymentFallback(): List<ComponentCandidate> {
        val definitions = SYSTEM_SERVICE_ENTRY_DEFINITIONS.filter { it.kind == IntentKind.NFC_HCE }
        return definitions.flatMap { definition ->
            val onHost = definition.action == NFC_HOST_ACTION
            runCatching { pm.queryIntentServices(Intent(definition.action), PackageManager.GET_META_DATA) }
                .getOrDefault(emptyList())
                .mapNotNull { resolved ->
                    val service = resolved.serviceInfo ?: return@mapNotNull null
                    val app = service.applicationInfo ?: return@mapNotNull null
                    if (!definition.acceptsPermission(service.permission) || !hasStaticPaymentAidGroup(service, onHost)) {
                        return@mapNotNull null
                    }
                    val rule = ComponentRule(IntentKind.NFC_HCE, service.packageName, service.name)
                    if (!rule.isValid()) return@mapNotNull null
                    componentCandidate(
                        rule,
                        app,
                        runCatching { service.loadLabel(pm).toString() }.getOrDefault(service.name.substringAfterLast('.')),
                        listOf(
                            "NFC_PAYMENT fallback=manifest_static_category",
                            "component=${service.packageName}/${service.name}",
                        ),
                    )
                }
        }
    }

    private fun hasStaticPaymentAidGroup(service: ServiceInfo, onHost: Boolean): Boolean = runCatching {
        val metadata = if (onHost) NFC_HOST_META else NFC_OFFHOST_META
        service.loadXmlMetaData(pm, metadata)?.use { parser ->
            var type = parser.eventType
            while (type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (type == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "aid-group") {
                    val category = parser.getAttributeValue(ANDROID_NS, "category")
                        ?: parser.getAttributeValue(null, "category")
                    if (category == NFC_PAYMENT_CATEGORY) return@use true
                }
                type = parser.next()
            }
            false
        } ?: false
    }.getOrDefault(false)

    /** PM is the actual authority for these categories on AOSP. */
    private fun packageManagerAuthoritativeServiceEntries(): List<ComponentCandidate> =
        setOf(IntentKind.NOTIFICATION_LISTENER, IntentKind.WALLPAPER, IntentKind.DREAM, IntentKind.CALL_SCREENING)
            .flatMap(::serviceFallback)

    @Suppress("DEPRECATION")
    private fun serviceFallback(kind: IntentKind): List<ComponentCandidate> =
        SYSTEM_SERVICE_ENTRY_DEFINITIONS.filter { it.kind == kind }.flatMap { definition ->
            runCatching { pm.queryIntentServices(Intent(definition.action), flags) }
                .getOrDefault(emptyList())
                .mapNotNull { resolved -> serviceCandidate(definition.kind, definition.action, definition, resolved) }
        }

    private fun packageScopedServiceFallback(kind: IntentKind, reason: String): List<ComponentCandidate> {
        val items = serviceFallback(kind).map { candidate ->
            candidate.copy(evidence = candidate.evidence + reason)
        }
        return mergeSpecialCandidates(items.map { item ->
            item.copy(
                rule = SyntheticEntryKeys.packageScopedRule(kind, item.rule.packageName),
                activityLabel = item.appLabel,
            )
        })
    }

    private fun serviceCandidate(
        kind: IntentKind,
        action: String,
        definition: com.yagay.ListCleaner.domain.SystemServiceEntryDefinition,
        resolved: ResolveInfo,
    ): ComponentCandidate? {
        val service = resolved.serviceInfo ?: return null
        val app = service.applicationInfo ?: return null
        if (!service.enabled || !app.enabled || !definition.acceptsPermission(service.permission)) return null
        val rule = if (kind.isPackageScopedEntry()) {
            SyntheticEntryKeys.packageScopedRule(kind, service.packageName)
        } else {
            ComponentRule(kind, service.packageName, service.name)
        }
        if (!rule.isValid()) return null
        return componentCandidate(
            rule,
            app,
            if (kind.isPackageScopedEntry()) runCatching { app.loadLabel(pm).toString() }.getOrDefault(service.packageName)
            else runCatching { service.loadLabel(pm).toString() }.getOrDefault(service.name.substringAfterLast('.')),
            buildList {
                add("SERVICE_ENTRY action=$action")
                add("component=${service.packageName}/${service.name}")
                add("exported=${service.exported}")
                service.permission?.takeIf { it.isNotBlank() }?.let { add("permission=$it") }
            },
        )
    }

    private fun componentCandidate(
        rule: ComponentRule,
        app: ApplicationInfo,
        label: String,
        evidence: List<String>,
    ): ComponentCandidate = ComponentCandidate(
        rule = rule,
        appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(rule.packageName),
        activityLabel = label,
        appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
        appType = app.listCleanerAppType(),
        evidence = evidence,
    )

    private fun packageCandidates(
        kind: IntentKind,
        evidenceByPackage: Map<String, List<String>>,
        appByPackage: Map<String, ApplicationInfo>,
    ): List<ComponentCandidate> = evidenceByPackage.mapNotNull { (packageName, evidence) ->
        val app = appByPackage[packageName]
            ?: runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            ?: return@mapNotNull null
        val rule = SyntheticEntryKeys.packageScopedRule(kind, packageName)
        val label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(packageName)
        ComponentCandidate(
            rule = rule,
            appLabel = label,
            activityLabel = label,
            appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
            appType = app.listCleanerAppType(),
            evidence = evidence.distinct(),
        )
    }

    private fun isAssistantVoiceInteractionService(service: ServiceInfo): Boolean {
        if (service.permission != Manifest.permission.BIND_VOICE_INTERACTION) return false
        return runCatching {
            service.loadXmlMetaData(pm, VoiceInteractionService.SERVICE_META_DATA)?.use { parser ->
                var type = parser.eventType
                while (type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && type != org.xmlpull.v1.XmlPullParser.START_TAG) {
                    type = parser.next()
                }
                if (type != org.xmlpull.v1.XmlPullParser.START_TAG) return@use false
                val attrs = Xml.asAttributeSet(parser)
                var sessionService: String? = null
                var recognitionService: String? = null
                var supportsAssist = false
                for (index in 0 until attrs.attributeCount) {
                    when (attrs.getAttributeNameResource(index)) {
                        android.R.attr.sessionService -> sessionService = attrs.getAttributeValue(index)
                        android.R.attr.recognitionService -> recognitionService = attrs.getAttributeValue(index)
                        android.R.attr.supportsAssist -> supportsAssist = attrs.getAttributeBooleanValue(index, false)
                    }
                }
                sessionService != null && recognitionService != null && supportsAssist
            } ?: false
        }.getOrDefault(false)
    }

    /** Exact flags and metadata contract used by AccessibilityManager's shortcut discovery. */
    @Suppress("DEPRECATION")
    private fun accessibilityShortcutEntries(): List<ComponentCandidate> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_ACCESSIBILITY_SHORTCUT_TARGET)
        val queryFlags = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        return runCatching { pm.queryIntentActivities(intent, queryFlags) }
            .getOrDefault(emptyList())
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                val app = activity.applicationInfo ?: return@mapNotNull null
                if (!isAccessibilityShortcutTarget(activity)) return@mapNotNull null
                val rule = ComponentRule(IntentKind.ACCESSIBILITY, activity.packageName, activity.name)
                if (!rule.isValid()) return@mapNotNull null
                componentCandidate(
                    rule,
                    app,
                    runCatching { activity.loadLabel(pm).toString() }.getOrDefault(activity.name.substringAfterLast('.')),
                    listOf(
                        "ACCESSIBILITY_SHORTCUT authority=AccessibilityManager",
                        "component=${activity.packageName}/${activity.name}",
                    ),
                )
            }
    }

    private fun isAccessibilityShortcutTarget(activity: ActivityInfo): Boolean = runCatching {
        activity.loadXmlMetaData(pm, ACCESSIBILITY_SHORTCUT_META)?.use { parser ->
            var type = parser.eventType
            while (type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && type != org.xmlpull.v1.XmlPullParser.START_TAG) {
                type = parser.next()
            }
            type == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == ACCESSIBILITY_SHORTCUT_TAG
        } ?: false
    }.getOrDefault(false)

    private fun runReadOnlyRoot(command: String): String? = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = ByteArrayOutputStream()
        val reader = Thread({
            process.inputStream.use { input ->
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (total < ROOT_OUTPUT_LIMIT) {
                        val accepted = minOf(count, ROOT_OUTPUT_LIMIT - total)
                        output.write(buffer, 0, accepted)
                        total += accepted
                    }
                }
            }
        }, "listcleaner-authority-query").apply { isDaemon = true; start() }
        if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        reader.join(500)
        if (process.exitValue() != 0) return@runCatching null
        output.toString(StandardCharsets.UTF_8.name())
    }.getOrNull()

    private companion object {
        const val ACCESSIBILITY_SHORTCUT_META = "android.accessibilityshortcut.target"
        const val ACCESSIBILITY_SHORTCUT_TAG = "accessibility-shortcut-target"
        const val NFC_HOST_ACTION = "android.nfc.cardemulation.action.HOST_APDU_SERVICE"
        const val NFC_HOST_META = "android.nfc.cardemulation.host_apdu_service"
        const val NFC_OFFHOST_META = "android.nfc.cardemulation.off_host_apdu_service"
        const val NFC_PAYMENT_CATEGORY = "payment"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val ROOT_OUTPUT_LIMIT = 256 * 1024
        const val ROOT_TIMEOUT_SECONDS = 5L
        val VPN_APP_OPS = listOf("ACTIVATE_VPN", "ACTIVATE_PLATFORM_VPN")
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    }
}
