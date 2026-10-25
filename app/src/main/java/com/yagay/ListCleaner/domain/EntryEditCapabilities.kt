package com.yagay.ListCleaner.domain

/** Only expose editors with a corresponding runtime consumer. Existing backup values are retained. */
fun IntentKind.supportsPriorityEditing(): Boolean = isSelectableEntryKind() && this !in setOf(
    IntentKind.DOCUMENT_PROVIDER, IntentKind.PHOTO_SOURCE,
    IntentKind.NOTIFICATION_LISTENER, IntentKind.WALLPAPER, IntentKind.DREAM,
)

fun IntentKind.supportsTitleEditing(): Boolean = this in setOf(
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.OPEN, IntentKind.SEND_TO,
    IntentKind.BROWSER, IntentKind.DEEP_LINK, IntentKind.DIAL,
    IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO, IntentKind.RECORD_AUDIO,
    IntentKind.PROCESS_TEXT,
)
