package com.yagay.ListCleaner.domain

import com.yagay.ListCleaner.domain.IntentKind.*

/**
 * Explicitly reviewed ordering routes. This list is intentionally opt-in: a new
 * entry kind must not expose an editor until its runtime has an ordering consumer.
 * PARTIAL surfaces (Sharesheet / launcher / role pickers / Dream) depend on the final OEM UI.
 * DOCUMENT_ROOT is backed by the DocumentsUI RootsLoader/cache/sidebar hooks;
 * DREAM is backed by an optional Settings DreamBackend result hook.
 * Editing support never implies that a vendor picker honors the final order.
 * Existing priority values remain serialized even if editing is unsupported.
 */
enum class PriorityCapability { SUPPORTED, PARTIAL, UNSUPPORTED }

fun IntentKind.priorityCapability(): PriorityCapability = when (this) {
    SHARE, SHARE_MULTIPLE, SEND_TO, OPEN, BROWSER, DEEP_LINK, DIAL,
    GET_CONTENT, OPEN_DOCUMENT, CREATE_DOCUMENT,
    CAPTURE_IMAGE, CAPTURE_VIDEO, RECORD_AUDIO, PROCESS_TEXT,
    INPUT_METHOD, DOCUMENT_ROOT -> PriorityCapability.SUPPORTED

    DIRECT_SHARE, SHORTCUT_ITEM, HOME, ASSISTANT, AUTOFILL,
    ACCESSIBILITY, VPN, PRINT, NFC_HCE,
    CALL_SCREENING, DREAM -> PriorityCapability.PARTIAL

    DOCUMENT_PROVIDER, PHOTO_SOURCE,
    NOTIFICATION_LISTENER, WALLPAPER, LAUNCHER_SHORTCUT, CREDENTIAL_PROVIDER ->
        PriorityCapability.UNSUPPORTED
}

fun IntentKind.supportsPriorityEditing(): Boolean =
    priorityCapability() != PriorityCapability.UNSUPPORTED

fun IntentKind.supportsTitleEditing(): Boolean = this in setOf(
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.OPEN, IntentKind.SEND_TO,
    IntentKind.BROWSER, IntentKind.DEEP_LINK, IntentKind.DIAL,
    IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO, IntentKind.RECORD_AUDIO,
    IntentKind.PROCESS_TEXT,
)
