package com.yagay.ListCleaner.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.appwidget.AppWidgetManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ApplicationInfo
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
import com.yagay.ListCleaner.domain.ControlsProviderEligibility
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
    /** Captured in a recognized system settings UID, not proof of its actual rendered rows. */
    val hostReturn: RuntimeInterfaceSnapshot? = null,
    /** Last same-user return from another caller; unverified unless explicitly attributed. */
    val unattributedReturn: RuntimeInterfaceSnapshot? = null,
    /** Android-enabled state, independent of catalog registration. */
    val enabledItems: List<SystemProbeItem> = emptyList(),
    /** Previously observed ShortcutService/launcher items, never a live host list. */
    val historicalItems: List<SystemProbeItem> = emptyList(),
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
                val (local, host, other) = readRuntimeReturns(context, kind)
                val result = initial.copy(
                    runtimeReturn = local,
                    hostReturn = host,
                    unattributedReturn = other,
                )
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

    private data class RuntimeObservations(
        val own: RuntimeInterfaceSnapshot?,
        val host: RuntimeInterfaceSnapshot?,
        val other: RuntimeInterfaceSnapshot?,
    )

    private fun readRuntimeReturns(
        context: Context, kind: IntentKind,
    ): RuntimeObservations = runCatching {
        val app = context.applicationContext as? ListCleanerApp
            ?: return@runCatching RuntimeObservations(null, null, null)
        val prefs = app.currentSession()?.service
            ?.getRemotePreferences(RuleRepository.REMOTE_PREFS)
            ?: return@runCatching RuntimeObservations(null, null, null)
        val ownUid = Process.myUid()
        val ourUser = AndroidUid.userId(ownUid)
        fun decode(key: String) = RuntimeInterfaceSnapshotCodec.decode(
            prefs.getString(key, null), kind
        )?.takeIf { it.callerUserId == ourUser }
        val local = decode(RuntimeInterfaceSnapshotCodec.keyForCaller(kind, ownUid))
        val last = decode(RuntimeInterfaceSnapshotCodec.key(kind))
        // Do not silently substitute a random application's manager call.
        // A Settings UID observation is separately labeled, even though
        // a shared UID does not prove which Activity was rendered.
        val host = last?.takeIf {
            it.callerUid != ownUid && it.callerUid >= 0 &&
                isRecognizedSettingsUid(context, it.callerUid)
        }
        RuntimeObservations(
            own = listOfNotNull(local, last?.takeIf { it.callerUid == ownUid })
                .maxByOrNull { it.capturedAtMillis },
            host = host,
            other = last?.takeIf { it.callerUid != ownUid && it !== host },
        )
    }.getOrDefault(RuntimeObservations(null, null, null))

    @Suppress("DEPRECATION")
    private fun isRecognizedSettingsUid(context: Context, uid: Int): Boolean {
        val pm = context.packageManager
        return runCatching {
            pm.getPackagesForUid(uid).orEmpty().any { pkg ->
                // The package AND its installed system flag are needed: a third
                // party can choose a misleading package name.
                pkg.contains("settings", ignoreCase = true) &&
                    (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }
        }.getOrDefault(false)
    }

    private fun unique(items: List<SystemProbeItem>): List<SystemProbeItem> =
        items.distinctBy(SystemProbeItem::component)

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
    private fun matchingRootsFromDocumentsUi(
        context: Context, kind: IntentKind? = null,
    ): PickerRuntimeSnapshot? =
        runCatching {
            val application = context.applicationContext as? ListCleanerApp
                ?: return@runCatching null
            val preferences = application.currentSession()?.service
                ?.getRemotePreferences(RuleRepository.REMOTE_PREFS)
                ?: return@runCatching null
            val key = kind?.takeIf { candidate -> candidate in setOf(
                IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
                IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_ROOT
            ) }?.name?.let(PickerRuntimeSnapshotCodec::keyForRequest)
                ?: PickerRuntimeSnapshotCodec.REMOTE_KEY
            // Per-action slots are authoritative for Open/Save. Never fall back
            // to a different action's generic snapshot and present it as history.
            val encoded = preferences.getString(key, null)
            val snapshot = PickerRuntimeSnapshotCodec.decode(encoded) ?: return@runCatching null
            if (snapshot.userId != AndroidUid.userId(Process.myUid())) null else snapshot
        }.getOrNull()

    private fun previouslyObservedDocumentRoots(context: Context): List<ObservedPickerRoot> =
        ObservedEntryCache(context).snapshot().asSequence()
            .filter { it.kind == IntentKind.DOCUMENT_ROOT.name && it.userId == AndroidUid.userId(Process.myUid()) }
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
            .toList()

    private fun probeEntry(
        context: Context, kind: IntentKind, scenario: SystemProbeScenario,
    ): SystemProbeResult {
        val pm = context.packageManager
        return when (kind.previewAuthority()) {
            PreviewAuthority.PICKER_PRIVATE -> {
                val observed = previouslyObservedDocumentRoots(context)
                // Preserve unknown-action snapshots as historical evidence; the
                // verifier and UI must not misrepresent them as a matched request.
                // Keep other actions and UNKNOWN available only as history; the
                // verifier performs strict request attribution for Open and Save.
                val matching = matchingRootsFromDocumentsUi(context, kind)
                if (kind == IntentKind.DOCUMENT_ROOT) {
                    SystemProbeResult(
                        R.string.system_probe_source_picker,
                        R.string.system_probe_picker_internal,
                        supported = true,
                        evidence = ProbeEvidence.PICKER_HOST,
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
                val roleResult = if (kind.previewAuthority() == PreviewAuthority.ROLE_CANDIDATES)
                    roleHolders(context, kind) else null
                if (roleResult != null) return roleResult
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
            PreviewAuthority.SHORTCUT_PRIVATE -> {
                // Direct Share and dynamic shortcut targets are owned by
                // ShortcutService/the calling launcher. We can show only
                // previously observed targets, not a fabricated live panel.
                val history = ObservedEntryCache(context).snapshot()
                    .asSequence().filter { it.kind == kind.name && it.userId == AndroidUid.userId(Process.myUid()) }
                    .map { record ->
                        SystemProbeItem(
                            label = record.label.ifBlank { record.packageName },
                            packageName = record.packageName,
                            className = record.syntheticClass,
                            appLabel = appLabel(pm, record.packageName, record.packageName),
                        )
                    }.toList()
                noPublicList(R.string.system_probe_shortcut_final_private)
                    .copy(historicalItems = unique(history))
            }
            PreviewAuthority.HOST_CANDIDATES -> noPublicList()
        }
    }

    /**
     * RoleManager returns actual holders, unlike PackageManager's eligible candidates.
     * If the role cannot be queried, retain the existing candidate-only fallback.
     */
    private fun roleHolders(context: Context, kind: IntentKind): SystemProbeResult? {
        val role = when (kind) {
            IntentKind.HOME -> "android.app.role.HOME"
            IntentKind.BROWSER -> "android.app.role.BROWSER"
            IntentKind.ASSISTANT -> "android.app.role.ASSISTANT"
            IntentKind.CALL_SCREENING -> "android.app.role.CALL_SCREENING"
            else -> return null
        }
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        return runCatching {
            if (!manager.isRoleAvailable(role)) return@runCatching null
            // RoleManager.getRoleHolders is not present in every compile SDK stub.
            // Resolve it only at runtime; the caller falls back to candidates if absent.
            val method = manager.javaClass.getMethod("getRoleHolders", String::class.java)
            val holders = (method.invoke(manager, role) as? List<*>)
                ?.filterIsInstance<String>() ?: return@runCatching null
            val pm = context.packageManager
            val entries = holders.distinct().map { pkg: String ->
                SystemProbeItem(
                    label = appLabel(pm, pkg, pkg),
                    packageName = pkg,
                    className = "@role/" + kind.name,
                    appLabel = appLabel(pm, pkg, pkg),
                )
            }
            // AOSP RoleController distinguishes currently-held roles from
            // eligible activities. Preserve both, with explicit provenance.
            val candidates = if (kind == IntentKind.CALL_SCREENING) emptyList()
                else runCatching {
                    val query = SystemEffectPreview.queryIntent(
                        context, kind, defaultProbeScenario(kind)
                    )
                    pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
                        .mapNotNull { resolvedItem(pm, it) }
                }.getOrDefault(emptyList())
            SystemProbeResult(
                R.string.system_probe_source_services,
                R.string.system_probe_role_notice,
                items = unique(entries),
                enabledItems = unique(entries),
                diagnosticCandidates = unique(candidates),
                evidence = ProbeEvidence.ENABLED,
            )
        }.getOrNull()
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
                    enabledItems = unique(asEntries(manager.enabledInputMethodList)),
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
                    R.string.system_probe_source_services,
                    R.string.system_probe_service_notice,
                    // This is the same installed-service API intercepted by the
                    // Accessibility list-cleaning Hook. Enabled services are different:
                    // they describe active settings, not the cleaned Settings catalog.
                    items = unique(asEntries(manager.installedAccessibilityServiceList)),
                    diagnosticCandidates = unique(asEntries(manager.installedAccessibilityServiceList)),
                    enabledItems = unique(asEntries(manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    ) ?: emptyList())),
                    evidence = ProbeEvidence.REGISTERED,
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
            // These are registered providers, not a picker-host result or a final
            // visible RootsFragment list. Show them openly as candidates.
            evidence = ProbeEvidence.REGISTERED, items = unique(entries),
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
            CleanupKind.TILE, CleanupKind.CONTROLS ->
                pm.queryIntentServices(
                    SystemEffectPreview.markedQueryIntent(
                        Intent(kind.action)
                    ), PackageManager.GET_META_DATA,
                ).mapNotNull { resolved ->
                    val service = resolved.serviceInfo ?: return@mapNotNull null
                    if (kind == CleanupKind.TILE && !TileServiceEligibility.active(
                        service.permission, service.exported,
                        service.enabled, service.applicationInfo?.enabled == true,
                    )) return@mapNotNull null
                    if (kind == CleanupKind.CONTROLS &&
                        !ControlsProviderEligibility.accepts(service.permission, service.exported))
                        return@mapNotNull null
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
                CleanupKind.CONTROLS -> R.string.surface_controls_probe_notice
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

