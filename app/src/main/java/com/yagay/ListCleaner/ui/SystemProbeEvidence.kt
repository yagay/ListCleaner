package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind

/**
 * What the caller can actually observe is not always what a framework/OEM UI renders.
 * One contract for all supported rule and Root-component surfaces; never use the mere
 * existence of a PackageManager result as proof of final visibility or successful filtering.
 */
internal enum class ProbeEvidence(val labelRes: Int) {
    /** No public API exposes the final interactive list or shortcut rank. */
    NATIVE_UI_ONLY(R.string.system_probe_evidence_native_only),
    /** Raw activity resolutions for the supplied Intent; not chooser's actual presentation. */
    RESOLVER_CANDIDATES(R.string.system_probe_evidence_resolver),
    /** System manager's installed-entry API, prior to UI-specific eligibility. */
    MANAGER_REGISTRY(R.string.system_probe_evidence_manager),
    /** Declared services matching an Android binding contract, not a Settings/Role list. */
    SERVICE_REGISTRY(R.string.system_probe_evidence_services),
    /** Declared DocumentsProvider/cloud-provider contracts, not visible picker roots. */
    PROVIDER_REGISTRY(R.string.system_probe_evidence_providers),
    /** Role-controller eligibility, defaults and system selection can differ. */
    ROLE_CANDIDATES(R.string.system_probe_evidence_roles),
    /** LauncherApps/AppWidgetManager entries, not launcher's rendered surface. */
    LAUNCHER_REGISTRY(R.string.system_probe_evidence_launcher),
}

internal fun previewEvidence(kind: IntentKind): ProbeEvidence = when (kind) {
    // ACTION_GET_CONTENT can be handled by a framework picker, which then loads sources
    // using its own privileged APIs. Its visible items are NOT queryIntentActivities().
    IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
    IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE,
    IntentKind.DIRECT_SHARE, IntentKind.SHORTCUT_ITEM, IntentKind.LAUNCHER_SHORTCUT ->
        ProbeEvidence.NATIVE_UI_ONLY

    IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY ->
        ProbeEvidence.MANAGER_REGISTRY

    IntentKind.DOCUMENT_PROVIDER -> ProbeEvidence.PROVIDER_REGISTRY

    IntentKind.HOME, IntentKind.ASSISTANT, IntentKind.BROWSER,
    IntentKind.CALL_SCREENING -> ProbeEvidence.ROLE_CANDIDATES

    IntentKind.VPN, IntentKind.CREDENTIAL_PROVIDER, IntentKind.AUTOFILL,
    IntentKind.PRINT, IntentKind.NFC_HCE -> ProbeEvidence.SERVICE_REGISTRY

    IntentKind.NOTIFICATION_LISTENER, IntentKind.WALLPAPER, IntentKind.DREAM ->
        ProbeEvidence.SERVICE_REGISTRY

    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.SEND_TO,
    IntentKind.OPEN, IntentKind.DEEP_LINK, IntentKind.DIAL,
    IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO, IntentKind.RECORD_AUDIO,
    IntentKind.PROCESS_TEXT -> ProbeEvidence.RESOLVER_CANDIDATES
}

internal fun previewEvidence(kind: CleanupKind): ProbeEvidence = when (kind) {
    CleanupKind.LAUNCHER, CleanupKind.SHORTCUT, CleanupKind.WIDGET ->
        ProbeEvidence.LAUNCHER_REGISTRY
    CleanupKind.TILE -> ProbeEvidence.SERVICE_REGISTRY
}
