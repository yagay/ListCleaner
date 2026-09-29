package com.yagay.ListCleaner.data

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.service.voice.VoiceInteractionService
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.domain.AppType
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryProtocol
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import com.yagay.ListCleaner.domain.isPackageScopedEntry
import com.yagay.ListCleaner.domain.listCleanerAppType
import com.yagay.ListCleaner.domain.webTargetKind

/** Discovery for system-managed entry surfaces that are not ordinary Activity resolver results. */
internal class SpecialEntryDiscovery(private val context: Context) {
    private val pm = context.packageManager
    private val flags = PackageManager.MATCH_ALL or PackageManager.GET_META_DATA
    private val observedCache = ObservedEntryCache(context)

    fun scan(): List<ComponentCandidate> = mergeSpecialCandidates(
        observedShortcutEntries() +
            assistantEntries() +
            homeEntries() +
            browserRoleEntries() +
            accessibilityShortcutEntries() +
            documentProviders() +
            systemServiceEntries()
    ).sortedWith(
        compareBy<ComponentCandidate>(
            { it.rule.kind.ordinal },
            { it.appLabel.lowercase() },
            { it.activityLabel.lowercase() },
        )
    )

    private fun mergeSpecialCandidates(items: List<ComponentCandidate>): List<ComponentCandidate> =
        items.groupBy { it.rule.id }.values.map { matches ->
            val first = matches.first()
            first.copy(
                evidence = matches.flatMap { it.evidence }.distinct().take(48),
                restricted = matches.all { it.restricted },
                unavailable = matches.all { it.unavailable },
            )
        }

    @Suppress("DEPRECATION")
    private fun observedShortcutEntries(): List<ComponentCandidate> {
        val intent = Intent(ObservedEntryProtocol.ACTION).setPackage(ObservedEntryProtocol.PACKAGE)
        val liveRecords = runCatching { pm.queryIntentActivities(intent, flags) }
            .getOrDefault(emptyList())
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                val kind = activity.metaData?.getString(ObservedEntryProtocol.META_KIND)
                    ?.let { runCatching { IntentKind.valueOf(it) }.getOrNull() }
                    ?.takeIf { it == IntentKind.SHORTCUT_ITEM || it == IntentKind.DIRECT_SHARE }
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
                ?.takeIf { it == IntentKind.SHORTCUT_ITEM || it == IntentKind.DIRECT_SHARE }
                ?: return@mapNotNull null
            val rule = ComponentRule(kind, observed.packageName, observed.syntheticClass)
            if (!rule.isValid()) return@mapNotNull null
            val app = runCatching { pm.getApplicationInfo(observed.packageName, 0) }.getOrNull()
                ?: return@mapNotNull null
            val live = observed.key in liveKeys
            ComponentCandidate(
                rule = rule,
                appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(observed.packageName),
                activityLabel = observed.label.ifBlank { observed.activityClass?.substringAfterLast('.') ?: kind.name },
                appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                appType = app.listCleanerAppType(),
                evidence = buildList {
                    add("OBSERVED_${kind.name} source=${if (live) "live" else "cache_history"}")
                    observed.activityClass?.takeIf { it.isNotBlank() }?.let { add("activity=$it") }
                    add("observedAt=${observed.observedAt}")
                },
                // A local cache entry from an earlier system_server lifetime is evidence/history,
                // not proof that a dynamic shortcut still exists right now.
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
                observe(
                    activity.packageName,
                    app,
                    "ASSISTANT_ACTIVITY component=${activity.packageName}/${activity.name}"
                )
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
                    if (!service.enabled || !app.enabled || !isAssistantVoiceInteractionService(service)) {
                        return@forEach
                    }
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
                    if (!activity.enabled || !app.enabled || resolved.webTargetKind() != IntentKind.BROWSER) {
                        return@forEach
                    }
                    appByPackage.putIfAbsent(activity.packageName, app)
                    evidenceByPackage.getOrPut(activity.packageName) { mutableListOf("BROWSER_ROLE package_level=true") }
                        .add("BROWSER_ACTIVITY scheme=$scheme component=${activity.packageName}/${activity.name}")
                }
        }
        return packageCandidates(IntentKind.BROWSER, evidenceByPackage, appByPackage)
    }

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
                while (type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT &&
                    type != org.xmlpull.v1.XmlPullParser.START_TAG
                ) {
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

    /** Accessibility Settings merges services with ACTION_MAIN shortcut-target activities. */
    @Suppress("DEPRECATION")
    private fun accessibilityShortcutEntries(): List<ComponentCandidate> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_ACCESSIBILITY_SHORTCUT_TARGET)
        val queryFlags = PackageManager.MATCH_ALL or PackageManager.GET_META_DATA or
            PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        return runCatching { pm.queryIntentActivities(intent, queryFlags) }
            .getOrDefault(emptyList())
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                val app = activity.applicationInfo ?: return@mapNotNull null
                if (!activity.enabled || !app.enabled || !isAccessibilityShortcutTarget(activity)) {
                    return@mapNotNull null
                }
                val rule = ComponentRule(IntentKind.ACCESSIBILITY, activity.packageName, activity.name)
                if (!rule.isValid()) return@mapNotNull null
                ComponentCandidate(
                    rule = rule,
                    appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(activity.packageName),
                    activityLabel = runCatching { activity.loadLabel(pm).toString() }
                        .getOrDefault(activity.name.substringAfterLast('.')),
                    appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                    appType = app.listCleanerAppType(),
                    evidence = listOf(
                        "ACCESSIBILITY_SHORTCUT activity=${activity.packageName}/${activity.name}",
                        "metadata=$ACCESSIBILITY_SHORTCUT_META",
                    ),
                )
            }
    }

    private fun isAccessibilityShortcutTarget(activity: ActivityInfo): Boolean = runCatching {
        activity.loadXmlMetaData(pm, ACCESSIBILITY_SHORTCUT_META)?.use { parser ->
            var type = parser.eventType
            while (type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT &&
                type != org.xmlpull.v1.XmlPullParser.START_TAG
            ) {
                type = parser.next()
            }
            type == org.xmlpull.v1.XmlPullParser.START_TAG &&
                parser.name == ACCESSIBILITY_SHORTCUT_TAG
        } ?: false
    }.getOrDefault(false)

    /** Keep only providers that satisfy the public DocumentsProvider manifest contract. */
    @Suppress("DEPRECATION")
    private fun documentProviders(): List<ComponentCandidate> {
        val intent = Intent(DocumentsContract.PROVIDER_INTERFACE)
        return runCatching { pm.queryIntentContentProviders(intent, flags) }
            .getOrDefault(emptyList())
            .mapNotNull { resolved ->
                val provider = resolved.providerInfo ?: return@mapNotNull null
                val app = provider.applicationInfo ?: return@mapNotNull null
                if (!provider.enabled || !app.enabled || !provider.exported || !provider.grantUriPermissions) {
                    return@mapNotNull null
                }
                val managesDocuments = provider.readPermission == Manifest.permission.MANAGE_DOCUMENTS ||
                    provider.writePermission == Manifest.permission.MANAGE_DOCUMENTS
                if (!managesDocuments) return@mapNotNull null

                val rule = ComponentRule(IntentKind.DOCUMENT_PROVIDER, provider.packageName, provider.name)
                if (!rule.isValid()) return@mapNotNull null
                ComponentCandidate(
                    rule = rule,
                    appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(provider.packageName),
                    activityLabel = runCatching { provider.loadLabel(pm).toString() }
                        .getOrDefault(provider.name.substringAfterLast('.')),
                    appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                    appType = app.listCleanerAppType(),
                    evidence = buildList {
                        add("DOCUMENT_PROVIDER source=${DocumentsContract.PROVIDER_INTERFACE}")
                        add("exported=true grantUriPermissions=true permission=${Manifest.permission.MANAGE_DOCUMENTS}")
                        provider.authority?.takeIf { it.isNotBlank() }?.let { add("authority=$it") }
                    },
                )
            }
    }

    @Suppress("DEPRECATION")
    private fun systemServiceEntries(): List<ComponentCandidate> =
        SYSTEM_SERVICE_ENTRY_DEFINITIONS.flatMap { definition ->
            runCatching { pm.queryIntentServices(Intent(definition.action), flags) }
                .getOrDefault(emptyList())
                .mapNotNull { resolved ->
                    val service = resolved.serviceInfo ?: return@mapNotNull null
                    val app = service.applicationInfo ?: return@mapNotNull null
                    if (!service.enabled || !app.enabled || !definition.acceptsPermission(service.permission)) {
                        return@mapNotNull null
                    }
                    val rule = if (definition.kind.isPackageScopedEntry()) {
                        SyntheticEntryKeys.packageScopedRule(definition.kind, service.packageName)
                    } else {
                        ComponentRule(definition.kind, service.packageName, service.name)
                    }
                    if (!rule.isValid()) return@mapNotNull null
                    ComponentCandidate(
                        rule = rule,
                        appLabel = runCatching { app.loadLabel(pm).toString() }.getOrDefault(service.packageName),
                        activityLabel = if (definition.kind.isPackageScopedEntry()) {
                            runCatching { app.loadLabel(pm).toString() }.getOrDefault(service.packageName)
                        } else {
                            runCatching { service.loadLabel(pm).toString() }
                                .getOrDefault(service.name.substringAfterLast('.'))
                        },
                        appIcon = runCatching { app.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                        appType = app.listCleanerAppType(),
                        evidence = buildList {
                            add("SERVICE_ENTRY action=${definition.action}")
                            add("component=${service.packageName}/${service.name}")
                            add("exported=${service.exported}")
                            service.permission?.takeIf { it.isNotBlank() }?.let { add("permission=$it") }
                        },
                    )
                }
        }

    private companion object {
        const val ACCESSIBILITY_SHORTCUT_META = "android.accessibilityshortcut.target"
        const val ACCESSIBILITY_SHORTCUT_TAG = "accessibility-shortcut-target"
    }
}
