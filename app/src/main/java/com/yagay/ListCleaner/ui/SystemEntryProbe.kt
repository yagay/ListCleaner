package com.yagay.ListCleaner.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Process
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.PickerRuntimeSnapshot
import com.yagay.ListCleaner.domain.PickerRuntimeSnapshotCodec
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.data.ObservedEntryCache
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.SYSTEM_SERVICE_ENTRY_DEFINITIONS
import com.yagay.ListCleaner.domain.TileServiceEligibility
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshotCodec
import com.yagay.ListCleaner.domain.SystemServiceEntryDefinition
import com.yagay.ListCleaner.domain.isPackageScopedEntry
import com.yagay.ListCleaner.domain.webTargetKind
import org.xmlpull.v1.XmlPullParser

internal data class SystemProbeRequest(
    val entryKind: IntentKind? = null,
    val componentKind: CleanupKind? = null,
    val userId: Int,
    val scenario: SystemProbeScenario? = null,
    val focusPackage: String? = null,
    val focusClass: String? = null,
) {
    init { require((entryKind == null) != (componentKind == null)) }

    fun matches(item: SystemProbeItem): Boolean {
        if (focusPackage == null || item.packageName != focusPackage) return false
        if (entryKind?.isPackageScopedEntry() == true) return true
        return focusClass == null || item.className == focusClass
    }
}

/** One actual framework result: label/identity are never fabricated from saved rule state. */
internal data class SystemProbeItem(
    val label: String,
    val packageName: String,
    val className: String,
    val appLabel: String = label,
) {
    val component: String get() = "$packageName/$className"
}

/** Source roots observed earlier inside the real DocumentsUI process via LSPosed.
 * They are historical THIRD-PARTY roots, not a full/current visible source list.
 */
internal data class ObservedPickerRoot(
    val label: String,
    val packageName: String,
    val key: String,
    val capturedAtMillis: Long,
)

internal enum class ProbeEvidence {
    /** Directly returned by Android's enabled InputMethod / Accessibility manager. */
    ENABLED,
    /** Framework registration or capability; final UI may remove/merge/add entries. */
    REGISTERED,
    /** Only the system chooser/picker host can be identified, not its internal list. */
    PICKER_HOST,
    /** No ordinary-app API for the actual system/host-owned surface. */
    NOT_PUBLIC,
}

internal data class SystemProbeResult(
    val sourceRes: Int,
    val explanationRes: Int,
    val items: List<SystemProbeItem> = emptyList(),
    val supported: Boolean = true,
    val failure: Boolean = false,
    val evidence: ProbeEvidence = ProbeEvidence.REGISTERED,
    /** Diagnostic-only registrations never count as actual enabled/displayed entries. */
    val diagnosticCandidates: List<SystemProbeItem> = emptyList(),
    val pickerHost: SystemProbeItem? = null,
    val observedThirdPartyRoots: List<ObservedPickerRoot> = emptyList(),
    val matchingRootsSnapshot: PickerRuntimeSnapshot? = null,
    /** A timestamp correlation, not proof of on-screen rendering. */
    val pickerProbeStatus: PickerProbeStatus? = null,
    /** Framework Hook post-filter return, distinct from both PM candidates and actual UI. */
    val runtimeReturn: RuntimeInterfaceSnapshot? = null,
)

/**
 * Read-only authority dispatcher. A PackageManager result is allowed as a candidate inventory
 * but never reused as DocumentsUI / PhotoPicker / RoleController's final displayed collection.
 *
 * All requests are bound to this app's Android user. A profile's registry cannot be silently
 * represented by the personal user's API response.
 */
internal object SystemEntryProbe {
    fun query(context: Context, request: SystemProbeRequest): SystemProbeResult {
        if (request.userId != AndroidUid.userId(Process.myUid())) {
            return SystemProbeResult(
                R.string.system_probe_source_none, R.string.system_probe_other_user,
                supported = false, evidence = ProbeEvidence.NOT_PUBLIC,
            )
        }
        return try {
            request.entryKind?.let { kind ->
                val scenario = request.scenario ?: defaultProbeScenario(kind)
                val initial = probeEntry(context, kind, scenario)
                val result = initial.copy(runtimeReturn = readRuntimeReturn(context, kind))
                if (kind !in setOf(
                        IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
                        IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_ROOT,
                    )
                ) return result
                return result.copy(
                    pickerProbeStatus = PickerProbeVerifier.evaluate(
                        PickerProbeSessionStore(context).read(),
                        result.matchingRootsSnapshot, kind, scenario, request.userId,
                        System.currentTimeMillis(),
                    )
                )
            }
            probeComponent(context, requireNotNull(request.componentKind))
        } catch (_: SecurityException) {
            SystemProbeResult(
                R.string.system_probe_source_none, R.string.system_probe_restricted,
                supported = false, failure = true, evidence = ProbeEvidence.NOT_PUBLIC,
            )
        } catch (_: Exception) {
            SystemProbeResult(
                R.string.system_probe_source_none, R.string.system_probe_failed,
                supported = false, failure = true, evidence = ProbeEvidence.NOT_PUBLIC,
            )
        }
    }

    private fun readRuntimeReturn(context: Context, kind: IntentKind): RuntimeInterfaceSnapshot? {
        if (kind !in setOf(
                IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT,
                IntentKind.CALL_SCREENING, IntentKind.DIRECT_SHARE,
                IntentKind.ACCESSIBILITY, IntentKind.PRINT, IntentKind.CREDENTIAL_PROVIDER
            )
        ) return null
        return runCatching {
            val app = context.applicationContext as? ListCleanerApp
                ?: return@runCatching null
            val prefs = app.currentSession()?.service
                ?.getRemotePreferences(RuleRepository.REMOTE_PREFS)
                ?: return@runCatching null
            val result = RuntimeInterfaceSnapshotCodec.decode(
                prefs.getString(RuntimeInterfaceSnapshotCodec.key(kind), null), kind
            )
            if (result?.callerUserId == AndroidUid.userId(Process.myUid())) result
            else null
        }.getOrNull()
    }

    private fun unique(items: List<SystemProbeItem>): List<SystemProbeItem> =
        items.distinctBy(SystemProbeItem::component).take(1500)

    private fun candidates(
        source: Int,
        description: Int,
        items: List<SystemProbeItem>,
    ) = SystemProbeResult(
        source, description, items = unique(items), evidence = ProbeEvidence.REGISTERED,
    )

    private fun noPublicList(description: Int = R.string.system_probe_no_public_registry) =
        SystemProbeResult(
            R.string.system_probe_source_none, description,
            supported = false, evidence = ProbeEvidence.NOT_PUBLIC,
        )

    /** Read the real DocumentsUI Hook result for the current Android user, if available. */
    private fun matchingRootsFromDocumentsUi(context: Context): PickerRuntimeSnapshot? =
        runCatching {
            val application = context.applicationContext as? ListCleanerApp
                ?: return@runCatching null
            val preferences = application.currentSession()?.service
                ?.getRemotePreferences(RuleRepository.REMOTE_PREFS)
                ?: return@runCatching null
            val snapshot = PickerRuntimeSnapshotCodec.decode(
                preferences.getString(PickerRuntimeSnapshotCodec.REMOTE_KEY, null)
            ) ?: return@runCatching null
            if (snapshot.userId != AndroidUid.userId(Process.myUid())) null else snapshot
        }.getOrNull()

    private fun previouslyObservedDocumentRoots(context: Context): List<ObservedPickerRoot> =
        ObservedEntryCache(context).snapshot().asSequence()
            .filter { it.kind == IntentKind.DOCUMENT_ROOT.name }
            .map { record ->
                ObservedPickerRoot(
                    label = record.label.ifBlank { record.packageName },
                    packageName = record.packageName,
                    key = record.syntheticClass,
                    capturedAtMillis = record.observedAt,
                )
            }
            .distinctBy { it.packageName to it.key }
            .sortedByDescending { it.capturedAtMillis }
            .take(120)
            .toList()

    private fun probeEntry(
        context: Context, kind: IntentKind, scenario: SystemProbeScenario,
    ): SystemProbeResult {
        val pm = context.packageManager
        return when (kind.previewAuthority()) {
            PreviewAuthority.PICKER_PRIVATE -> {
                val observed = previouslyObservedDocumentRoots(context)
                val matching = matchingRootsFromDocumentsUi(context)
                if (kind == IntentKind.DOCUMENT_ROOT) {
                    SystemProbeResult(
                        R.string.system_probe_source_picker,
                        R.string.system_probe_picker_internal,
                        supported = false,
                        evidence = ProbeEvidence.NOT_PUBLIC,
                        observedThirdPartyRoots = observed,
                        matchingRootsSnapshot = matching,
                    )
                } else {
                    val intent = SystemEffectPreview.queryIntent(context, kind, scenario)
                    val host = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        ?.activityInfo?.let { activity ->
                            SystemProbeItem(
                                activity.loadLabel(pm)?.toString().orEmpty().ifBlank { activity.name },
                                activity.packageName, activity.name,
                                appLabel(pm, activity.packageName, activity.name),
                            )
                        }
                    // This is NOT a list of files/roots shown by DocumentsUI/PhotoPicker.
                    val registrations = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        .mapNotNull { resolvedItem(pm, it) }
                    SystemProbeResult(
                        R.string.system_probe_source_picker, R.string.system_probe_picker_internal,
                        evidence = ProbeEvidence.PICKER_HOST,
                        pickerHost = host,
                        diagnosticCandidates = unique(registrations),
                        observedThirdPartyRoots = observed,
                        matchingRootsSnapshot = matching,
                    )
                }
            }
            PreviewAuthority.RESOLVER_CANDIDATES,
            PreviewAuthority.ROLE_CANDIDATES -> {
                if (kind == IntentKind.CALL_SCREENING) return registeredServices(context, kind)
                val intent = SystemEffectPreview.queryIntent(context, kind, scenario)
                val flags = if (kind in setOf(IntentKind.PROCESS_TEXT, IntentKind.HOME, IntentKind.ASSISTANT))
                    0 else PackageManager.MATCH_DEFAULT_ONLY
                val registrations = pm.queryIntentActivities(intent, flags).mapNotNull { resolve ->
                    if (kind == IntentKind.BROWSER && resolve.webTargetKind() != IntentKind.BROWSER)
                        return@mapNotNull null
                    resolvedItem(pm, resolve)
                }
                candidates(
                    R.string.system_probe_source_activities,
                    if (kind.previewAuthority() == PreviewAuthority.ROLE_CANDIDATES)
                        R.string.system_probe_role_notice else R.string.system_probe_pm_notice,
                    registrations,
                )
            }
            PreviewAuthority.ENABLED_MANAGER -> enabledManager(context, kind)
            PreviewAuthority.SERVICE_CANDIDATES -> registeredServices(context, kind)
            PreviewAuthority.PROVIDER_CANDIDATES -> registeredProviders(context, kind)
            PreviewAuthority.SHORTCUT_PRIVATE -> noPublicList(R.string.system_probe_shortcut_final_private)
            PreviewAuthority.HOST_CANDIDATES -> noPublicList()
        }
    }

    private fun enabledManager(context: Context, kind: IntentKind): SystemProbeResult {
        val pm = context.packageManager
        return when (kind) {
            IntentKind.INPUT_METHOD -> {
                val manager = context.getSystemService(InputMethodManager::class.java)
                    ?: return noPublicList()
                fun asEntries(infos: List<android.view.inputmethod.InputMethodInfo>): List<SystemProbeItem> =
                    infos.mapNotNull { info ->
                        val service = info.serviceInfo ?: return@mapNotNull null
                        SystemProbeItem(
                            info.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                            service.packageName, service.name,
                            appLabel(pm, service.packageName, service.name),
                        )
                    }
                SystemProbeResult(
                    R.string.system_probe_source_enabled_input,
                    R.string.system_probe_enabled_input_notice,
                    items = unique(asEntries(manager.enabledInputMethodList)),
                    diagnosticCandidates = unique(asEntries(manager.inputMethodList)),
                    evidence = ProbeEvidence.ENABLED,
                )
            }
            IntentKind.ACCESSIBILITY -> {
                val manager = context.getSystemService(AccessibilityManager::class.java)
                    ?: return noPublicList()
                fun asEntries(infos: List<AccessibilityServiceInfo>): List<SystemProbeItem> =
                    infos.mapNotNull { info ->
                        val service = info.resolveInfo?.serviceInfo ?: return@mapNotNull null
                        SystemProbeItem(
                            info.resolveInfo?.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                            service.packageName, service.name,
                            appLabel(pm, service.packageName, service.name),
                        )
                    }
                SystemProbeResult(
                    R.string.system_probe_source_enabled_accessibility,
                    R.string.system_probe_enabled_accessibility_notice,
                    items = unique(asEntries(manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    ) ?: emptyList())),
                    diagnosticCandidates = unique(asEntries(manager.installedAccessibilityServiceList)),
                    evidence = ProbeEvidence.ENABLED,
                )
            }
            else -> noPublicList()
        }
    }

    @Suppress("DEPRECATION")
    private fun registeredProviders(context: Context, kind: IntentKind): SystemProbeResult {
        val pm = context.packageManager
        val action = if (kind == IntentKind.DOCUMENT_PROVIDER)
            DocumentsContract.PROVIDER_INTERFACE else "android.content.action.CLOUD_MEDIA_PROVIDER"
        val entries = pm.queryIntentContentProviders(
            SystemEffectPreview.markedQueryIntent(Intent(action)),
            PackageManager.GET_META_DATA or PackageManager.MATCH_ALL,
        ).mapNotNull { resolved ->
            val info = resolved.providerInfo ?: return@mapNotNull null
            if (!info.enabled || info.applicationInfo?.enabled != true) return@mapNotNull null
            if (kind == IntentKind.PHOTO_SOURCE &&
                info.readPermission != "com.android.providers.media.permission.MANAGE_CLOUD_MEDIA_PROVIDERS"
            ) return@mapNotNull null
            SystemProbeItem(
                resolved.loadLabel(pm)?.toString().orEmpty().ifBlank { info.name },
                info.packageName, info.name, appLabel(pm, info.packageName, info.name),
            )
        }
        // Provider declarations are useful diagnostics, not current DocumentsUI roots or
        // the active cloud provider in the PhotoPicker.
        return SystemProbeResult(
            R.string.system_probe_source_providers, R.string.system_probe_provider_notice,
            evidence = ProbeEvidence.PICKER_HOST, diagnosticCandidates = unique(entries),
            // Storage providers share DocumentsUI root observations with document pickers.
            // These observations remain separate from registration candidates and are
            // not an assertion that the provider appears in the current picker.
            matchingRootsSnapshot = if (kind == IntentKind.DOCUMENT_PROVIDER)
                matchingRootsFromDocumentsUi(context) else null,
            observedThirdPartyRoots = if (kind == IntentKind.DOCUMENT_PROVIDER)
                previouslyObservedDocumentRoots(context) else emptyList(),
        )
    }

    @Suppress("DEPRECATION")
    private fun registeredServices(context: Context, kind: IntentKind): SystemProbeResult {
        val pm = context.packageManager
        val definitions = SYSTEM_SERVICE_ENTRY_DEFINITIONS.filter { it.kind == kind }
        val entries = definitions.flatMap { definition ->
            pm.queryIntentServices(
                SystemEffectPreview.markedQueryIntent(Intent(definition.action)),
                PackageManager.GET_META_DATA or PackageManager.MATCH_ALL,
            ).mapNotNull { resolved ->
                val service = resolved.serviceInfo ?: return@mapNotNull null
                if (!service.enabled || service.applicationInfo?.enabled != true ||
                    !definition.acceptsPermission(service.permission)
                ) return@mapNotNull null
                if (kind == IntentKind.NFC_HCE &&
                    !staticPaymentAidGroup(pm, service, definition)
                ) return@mapNotNull null
                SystemProbeItem(
                    resolved.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                    service.packageName, service.name,
                    appLabel(pm, service.packageName, service.name),
                )
            }
        }
        return candidates(
            R.string.system_probe_source_services,
            if (kind == IntentKind.NFC_HCE)
                R.string.system_probe_nfc_notice else R.string.system_probe_service_notice,
            entries,
        )
    }

    private fun staticPaymentAidGroup(
        pm: PackageManager, service: ServiceInfo, definition: SystemServiceEntryDefinition,
    ): Boolean = runCatching {
        val meta = if (definition.action == "android.nfc.cardemulation.action.HOST_APDU_SERVICE")
            "android.nfc.cardemulation.host_apdu_service"
        else "android.nfc.cardemulation.off_host_apdu_service"
        service.loadXmlMetaData(pm, meta)?.use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "aid-group") {
                    val category = parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "category"
                    ) ?: parser.getAttributeValue(null, "category")
                    if (category == "payment") return@use true
                }
                parser.next()
            }
            false
        } ?: false
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun probeComponent(context: Context, kind: CleanupKind): SystemProbeResult {
        val pm = context.packageManager
        val entries = when (kind) {
            CleanupKind.LAUNCHER, CleanupKind.SHORTCUT -> {
                val launcher = context.getSystemService(LauncherApps::class.java)
                    ?: return noPublicList()
                val user = Process.myUserHandle()
                val activities = if (kind == CleanupKind.LAUNCHER)
                    launcher.getActivityList(null, user)
                else launcher.getShortcutConfigActivityList(null, user)
                activities.map { info ->
                    val component = info.componentName
                    SystemProbeItem(
                        info.label?.toString().orEmpty().ifBlank { component.className },
                        component.packageName, component.className,
                        appLabel(pm, component.packageName, component.className),
                    )
                }
            }
            CleanupKind.WIDGET -> {
                val widget = context.getSystemService(AppWidgetManager::class.java)
                    ?: return noPublicList()
                widget.installedProviders.map { provider ->
                    val component = provider.provider
                    SystemProbeItem(
                        provider.label.orEmpty().ifBlank { component.className },
                        component.packageName, component.className,
                        appLabel(pm, component.packageName, component.className),
                    )
                }
            }
            CleanupKind.TILE ->
                pm.queryIntentServices(
                    SystemEffectPreview.markedQueryIntent(
                        Intent("android.service.quicksettings.action.QS_TILE")
                    ), PackageManager.GET_META_DATA,
                ).mapNotNull { resolved ->
                    val service = resolved.serviceInfo ?: return@mapNotNull null
                    if (!TileServiceEligibility.active(
                        service.permission, service.exported,
                        service.enabled, service.applicationInfo?.enabled == true,
                    )) return@mapNotNull null
                    SystemProbeItem(
                        resolved.loadLabel(pm)?.toString().orEmpty().ifBlank { service.name },
                        service.packageName, service.name,
                        appLabel(pm, service.packageName, service.name),
                    )
                }
        }
        return candidates(
            if (kind == CleanupKind.WIDGET) R.string.system_probe_source_widgets
            else if (kind == CleanupKind.LAUNCHER || kind == CleanupKind.SHORTCUT)
                R.string.system_probe_source_launcher else R.string.system_probe_source_services,
            when (kind) {
                CleanupKind.TILE -> R.string.system_probe_tile_notice
                CleanupKind.WIDGET -> R.string.system_probe_widget_notice
                CleanupKind.LAUNCHER -> R.string.system_probe_manager_notice
                CleanupKind.SHORTCUT -> R.string.system_probe_shortcut_notice
            },
            entries,
        )
    }

    private fun appLabel(pm: PackageManager, pkg: String, fallback: String): String = runCatching {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrDefault(fallback)

    private fun resolvedItem(pm: PackageManager, info: ResolveInfo): SystemProbeItem? {
        val activity = info.activityInfo ?: return null
        if (!activity.enabled || activity.applicationInfo?.enabled != true) return null
        return SystemProbeItem(
            info.loadLabel(pm)?.toString().orEmpty().ifBlank { activity.name },
            activity.packageName, activity.name,
            appLabel(pm, activity.packageName, activity.name),
        )
    }
}
