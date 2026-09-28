package com.yagay.ListCleaner.domain

/** Manager-only synthetic PackageManager query used to expose entries observed inside system_server. */
object ObservedEntryProtocol {
    const val ACTION = "com.yagay.ListCleaner.action.OBSERVED_SHORTCUT_ENTRIES"
    const val PACKAGE = "android"
    const val META_KIND = "com.yagay.ListCleaner.meta.ENTRY_KIND"
    const val META_ACTIVITY = "com.yagay.ListCleaner.meta.ENTRY_ACTIVITY"
    const val META_ID = "com.yagay.ListCleaner.meta.ENTRY_ID"
}
