package com.yagay.ListCleaner.domain

/** Four stable top-level groups keep the UI compact while entry kinds can keep growing. */
enum class EntryGroup {
    SHARE,
    OPEN,
    DESKTOP,
    ADVANCED,
}

fun IntentKind.entryGroup(): EntryGroup = when (this) {
    IntentKind.SHARE,
    IntentKind.SHARE_MULTIPLE,
    IntentKind.DIRECT_SHARE -> EntryGroup.SHARE

    IntentKind.SEND_TO,
    IntentKind.OPEN,
    IntentKind.BROWSER,
    IntentKind.DEEP_LINK,
    IntentKind.DIAL,
    IntentKind.GET_CONTENT,
    IntentKind.OPEN_DOCUMENT,
    IntentKind.CREATE_DOCUMENT,
    IntentKind.CAPTURE_IMAGE,
    IntentKind.CAPTURE_VIDEO,
    IntentKind.RECORD_AUDIO,
    IntentKind.PROCESS_TEXT,
    IntentKind.ASSISTANT -> EntryGroup.OPEN

    IntentKind.HOME,
    IntentKind.LAUNCHER_SHORTCUT,
    IntentKind.SHORTCUT_ITEM -> EntryGroup.DESKTOP

    IntentKind.DOCUMENT_PROVIDER,
    IntentKind.INPUT_METHOD,
    IntentKind.AUTOFILL,
    IntentKind.CREDENTIAL_PROVIDER,
    IntentKind.NOTIFICATION_LISTENER,
    IntentKind.ACCESSIBILITY,
    IntentKind.VPN,
    IntentKind.PRINT,
    IntentKind.WALLPAPER,
    IntentKind.DREAM,
    IntentKind.NFC_HCE,
    IntentKind.CALL_SCREENING -> EntryGroup.ADVANCED
}

/** Legacy app-level shortcut surface is deserializable but no longer selectable or executable. */
fun IntentKind.isSelectableEntryKind(): Boolean = this != IntentKind.LAUNCHER_SHORTCUT

fun EntryGroup.kinds(): List<IntentKind> =
    IntentKind.entries.filter { it.isSelectableEntryKind() && it.entryGroup() == this }
