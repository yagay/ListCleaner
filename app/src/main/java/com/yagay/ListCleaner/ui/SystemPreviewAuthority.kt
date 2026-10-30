package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind

/**
 * One authority contract for every previewable surface. An API candidate must never be
 * promoted into a "visible/active system picker entry" without evidence from its owner.
 */
internal enum class PreviewAuthority {
    /** A platform picker owns its final roots, sources and UI after intent resolution. */
    PICKER_PRIVATE,
    /** PackageManager can return intent-capable activities, not the final resolver UI. */
    RESOLVER_CANDIDATES,
    /** Android exposes enabled entries directly via a manager API. */
    ENABLED_MANAGER,
    /** RoleController may combine package/activity/service eligibility privately. */
    ROLE_CANDIDATES,
    /** System managers decide after service declaration/manifest eligibility. */
    SERVICE_CANDIDATES,
    /** Provider registrations are not the sources that a picker ultimately shows. */
    PROVIDER_CANDIDATES,
    /** Dynamic shortcuts and direct targets are controlled by another caller/launcher. */
    SHORTCUT_PRIVATE,
    /** Launchers/SystemUI may apply private, per-host item filtering. */
    HOST_CANDIDATES,
}

/** All IntentKind entries are covered by a single exhaustive when. */
internal fun IntentKind.previewAuthority(): PreviewAuthority = when (this) {
    IntentKind.GET_CONTENT,
    IntentKind.OPEN_DOCUMENT,
    IntentKind.CREATE_DOCUMENT,
    IntentKind.DOCUMENT_ROOT -> PreviewAuthority.PICKER_PRIVATE

    IntentKind.PHOTO_SOURCE,
    IntentKind.DOCUMENT_PROVIDER -> PreviewAuthority.PROVIDER_CANDIDATES

    IntentKind.SHARE,
    IntentKind.SHARE_MULTIPLE,
    IntentKind.SEND_TO,
    IntentKind.OPEN,
    IntentKind.EDIT,
    IntentKind.PICK,
    IntentKind.DEEP_LINK,
    IntentKind.DIAL,
    IntentKind.CAPTURE_IMAGE,
    IntentKind.CAPTURE_VIDEO,
    IntentKind.RECORD_AUDIO,
    IntentKind.PROCESS_TEXT -> PreviewAuthority.RESOLVER_CANDIDATES

    IntentKind.BROWSER,
    IntentKind.HOME,
    IntentKind.ASSISTANT,
    IntentKind.CALL_SCREENING -> PreviewAuthority.ROLE_CANDIDATES

    IntentKind.INPUT_METHOD,
    IntentKind.ACCESSIBILITY -> PreviewAuthority.ENABLED_MANAGER

    IntentKind.PRINT,
    IntentKind.VPN,
    IntentKind.CREDENTIAL_PROVIDER,
    IntentKind.AUTOFILL,
    IntentKind.NFC_HCE,
    IntentKind.NOTIFICATION_LISTENER,
    IntentKind.WALLPAPER,
    IntentKind.DREAM -> PreviewAuthority.SERVICE_CANDIDATES

    IntentKind.DIRECT_SHARE,
    IntentKind.SHORTCUT_ITEM,
    IntentKind.LAUNCHER_SHORTCUT -> PreviewAuthority.SHORTCUT_PRIVATE
}

internal fun CleanupKind.previewAuthority(): PreviewAuthority = PreviewAuthority.HOST_CANDIDATES

/** Only Android's two enabled-manager APIs expose a real enabled (not merely installed) list. */
internal fun PreviewAuthority.hasPublicEnabledList(): Boolean =
    this == PreviewAuthority.ENABLED_MANAGER

/** Never count hidden/private picker roots as enabled based on intent handlers. */
internal fun PreviewAuthority.hasPublicFinalPickerList(): Boolean = false
