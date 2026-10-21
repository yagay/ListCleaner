package com.yagay.ListCleaner.xposed

import android.content.SharedPreferences
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshotCodec
import java.util.concurrent.Executors

/**
 * Writes only package identifiers returned from actual hooked framework methods, never
 * shortcut IDs/contact names, document filenames or URIs. A dedicated worker avoids
 * blocking SystemServer / RoleController binder calls.
 */
internal class RuntimeInterfaceCaptureWriter(
    private val preferences: SharedPreferences,
    private val record: (String) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ListCleaner-InterfaceCapture").apply { isDaemon = true }
    }

    fun publish(
        kind: IntentKind,
        source: String,
        userId: Int,
        packages: Collection<String>,
        callerPackage: String = "",
    ) {
        val snapshot = RuntimeInterfaceSnapshot(
            kind = kind.name, source = source, callerUserId = userId,
            callerPackage = callerPackage,
            capturedAtMillis = System.currentTimeMillis(),
            packages = packages.distinct().take(RuntimeInterfaceSnapshotCodec.MAX_PACKAGES),
        )
        val encoded = RuntimeInterfaceSnapshotCodec.encode(snapshot)
        if (encoded.length > RuntimeInterfaceSnapshotCodec.MAX_CHARS) return
        executor.execute {
            runCatching {
                preferences.edit()
                    .putString(RuntimeInterfaceSnapshotCodec.key(kind), encoded)
                    .commit()
            }.onFailure {
                record("RUNTIME_RETURN_CAPTURE_FAILED kind=$kind error=${it.javaClass.name}")
            }
        }
    }
}
