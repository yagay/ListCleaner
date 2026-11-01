package com.yagay.ListCleaner.domain

/**
 * Only framework/Resolver captures cross this IPC boundary. It never transports rule changes.
 * The manager owns the private storage; hook processes must not edit RemotePreferences.
 */
object RuntimeCaptureContract {
    const val AUTHORITY = "com.yagay.ListCleaner.runtime-capture"
    const val METHOD_PUBLISH = "publish_capture_v1"
    const val PREFS = "runtime_interface_captures_v1"
    const val KEY_SNAPSHOT = "snapshot"
    const val KEY_ORDERED = "ordered"
    const val MAX_IPC_CHARS = RuntimeInterfaceSnapshotCodec.MAX_CHARS
}
