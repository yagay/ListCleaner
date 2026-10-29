package com.yagay.ListCleaner.domain

/** Only expose editors with a corresponding runtime consumer. Existing backup values are retained. */
/**
 * Explicitly reviewed ordering routes. This list is intentionally opt-in: a new
 * entry kind must not expose an editor until its runtime has an ordering consumer.
 * PARTIAL surfaces (Sharesheet / launcher / role pickers) still depend on final OEM UI.
 */
enum class PriorityCapability { SUPPORTED, PARTIAL, UNSUPPORTED }

fun IntentKind.priorityCapability(): PriorityCapability = when (this) {
    SHARE, SHARE_MULTIPLE, SEND_TO, OPEN, BROWSER, DEEP_LINK, DIAL,
    GET_CONTENT, OPEN_DOCUMENT, CREATE_DOCUMENT,
    CAPTURE_IMAGE, CAPTURE_VIDEO, RECORD_AUDIO, PROCESS_TEXT,
    INPUT_METHOD -> PriorityCapability.SUPPORTED

    DIRECT_SHARE, SHORTCUT_ITEM, HOME, ASSISTANT, AUTOFILL,
    CREDENTIAL_PROVIDER, ACCESSIBILITY, VPN, PRINT, NFC_HCE,
    CALL_SCREENING -> PriorityCapability.PARTIAL

    DOCUMENT_PROVIDER, PHOTO_SOURCE, DOCUMENT_ROOT,
    NOTIFICATION_LISTENER, WALLPAPER, DREAM, LAUNCHER_SHORTCUT ->
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
