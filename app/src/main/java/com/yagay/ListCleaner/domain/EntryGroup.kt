package com.yagay.ListCleaner.domain

/**
 * Presentation-only categories for existing entry kinds.
 * Keep IntentKind and ComponentRule identities unchanged for backups and LSPosed.
 */
enum class EntryGroup {
    SHARE,
    OPEN,
    FILES,
    DESKTOP,
    INPUT,
    SYSTEM,
    PERSONALIZATION,
}

fun IntentKind.entryGroup(): EntryGroup = when (this) {
    IntentKind.SHARE,
    IntentKind.SHARE_MULTIPLE,
    IntentKind.DIRECT_SHARE,
    IntentKind.SEND_TO -> EntryGroup.SHARE

    IntentKind.OPEN,
    IntentKind.BROWSER,
    IntentKind.DEEP_LINK,
    IntentKind.DIAL,
    IntentKind.EDIT -> EntryGroup.OPEN

    IntentKind.GET_CONTENT,
    IntentKind.PICK,
    IntentKind.OPEN_DOCUMENT,
    IntentKind.CREATE_DOCUMENT,
    IntentKind.CAPTURE_IMAGE,
    IntentKind.CAPTURE_VIDEO,
    IntentKind.RECORD_AUDIO,
    IntentKind.DOCUMENT_PROVIDER,
    IntentKind.DOCUMENT_ROOT,
    IntentKind.PHOTO_SOURCE -> EntryGroup.FILES

    IntentKind.HOME,
    IntentKind.ASSISTANT,
    IntentKind.LAUNCHER_SHORTCUT,
    IntentKind.SHORTCUT_ITEM -> EntryGroup.DESKTOP

    IntentKind.PROCESS_TEXT,
    IntentKind.INPUT_METHOD,
    IntentKind.AUTOFILL,
    IntentKind.CREDENTIAL_PROVIDER -> EntryGroup.INPUT

    IntentKind.NOTIFICATION_LISTENER,
    IntentKind.ACCESSIBILITY,
    IntentKind.VPN,
    IntentKind.PRINT,
    IntentKind.NFC_HCE,
    IntentKind.CALL_SCREENING -> EntryGroup.SYSTEM

    IntentKind.WALLPAPER,
    IntentKind.DREAM -> EntryGroup.PERSONALIZATION
}

/**
 * Preserve the safety note previously shown for all "Advanced" entry kinds.
 * A category is only a UI grouping: moving a kind must not silently drop its warning.
 */
fun IntentKind.requiresSystemEntrySafetyNote(): Boolean = when (this) {
    IntentKind.HOME,
    IntentKind.DOCUMENT_PROVIDER,
    IntentKind.DOCUMENT_ROOT,
    IntentKind.PHOTO_SOURCE,
    IntentKind.INPUT_METHOD,
    IntentKind.AUTOFILL,
    IntentKind.NOTIFICATION_LISTENER,
    IntentKind.ACCESSIBILITY,
    IntentKind.VPN,
    IntentKind.PRINT,
    IntentKind.WALLPAPER,
    IntentKind.DREAM,
    IntentKind.NFC_HCE,
    IntentKind.CALL_SCREENING -> true
    else -> false
}

/** Retired kinds remain deserializable but have no active cleanup UI or Hook. */
fun IntentKind.isSelectableEntryKind(): Boolean =
    this != IntentKind.LAUNCHER_SHORTCUT && this != IntentKind.CREDENTIAL_PROVIDER

fun EntryGroup.kinds(): List<IntentKind> =
    IntentKind.entries.filter { it.isSelectableEntryKind() && it.entryGroup() == this }
