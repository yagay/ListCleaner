package com.yagay.ListCleaner.xposed

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeCaptureContract
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshotCodec
import java.util.concurrent.Executors

/**
 * Captures only component and package identifiers. Never write Hook RemotePreferences:
 * the LSPosed remote store is read-only inside hooked processes on supported builds.
 * Transport on a worker through an authenticated manager-owned provider instead.
 */
internal class RuntimeInterfaceCaptureWriter(
    private val record: (String) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ListCleaner-InterfaceCapture").apply { isDaemon = true }
    }
    private var lastKey = ""
    private var lastContent = ""
    private var lastMillis = 0L

    fun publish(
        kind: IntentKind,
        source: String,
        userId: Int,
        packages: Collection<String>,
        callerPackage: String = "",
        components: Collection<String> = emptyList(),
        componentLabels: Map<String, String> = emptyMap(),
        callerUid: Int = -1,
        requestAction: String = "",
        requestMimeType: String = "",
        requestHost: String = "",
        requestToken: String = "",
        resolverOrdered: Boolean = false,
        context: Context? = null,
    ) {
        val uniquePackages = packages.distinct()
        val uniqueComponents = components.distinct()
        val snapshot = RuntimeInterfaceSnapshot(
            kind = kind.name, source = source, callerUserId = userId,
            callerPackage = callerPackage,
            callerUid = callerUid, requestAction = requestAction,
            requestMimeType = requestMimeType, requestHost = requestHost,
            requestToken = requestToken,
            capturedAtMillis = System.currentTimeMillis(),
            packages = uniquePackages.take(RuntimeInterfaceSnapshotCodec.MAX_PACKAGES),
            components = uniqueComponents.take(RuntimeInterfaceSnapshotCodec.MAX_PACKAGES),
            componentLabels = componentLabels.filterKeys {
                it in uniqueComponents.take(RuntimeInterfaceSnapshotCodec.MAX_PACKAGES)
            },
            totalPackages = uniquePackages.size, totalComponents = uniqueComponents.size,
            truncated = uniquePackages.size > RuntimeInterfaceSnapshotCodec.MAX_PACKAGES ||
                uniqueComponents.size > RuntimeInterfaceSnapshotCodec.MAX_PACKAGES,
        )
        // Bind to immutable identifiers before enqueue: never retain a Resolver Activity.
        executor.execute {
            runCatching {
                val encoded = RuntimeInterfaceSnapshotCodec.encode(snapshot)
                if (encoded.isBlank()) {
                    record("RUNTIME_RETURN_CAPTURE_SKIPPED kind=$kind reason=unencodable")
                    return@execute
                }
                val key = "$kind/$source/$userId/$callerUid/$requestAction/$requestMimeType/$requestHost"
                // RoleController/ShortcutService return package-only lists.
                // Component-only deduplication incorrectly drops a changed package list.
                val digest = buildString {
                    append(snapshot.packages.size)
                    append(':')
                    snapshot.packages.forEach {
                        append(it.length).append(':').append(it)
                    }
                    append('|').append(snapshot.components.size).append(':')
                    snapshot.components.forEach {
                        append(it.length).append(':').append(it)
                        snapshot.componentLabels[it]?.let { label ->
                            append('@').append(label.length).append(':').append(label)
                        }
                    }
                }
                val now = System.currentTimeMillis()
                if (key == lastKey && digest == lastContent && now - lastMillis in 0L..1200L) {
                    return@execute
                }
                val ctx = context?.applicationContext ?: context ?: findFrameworkContext()
                if (ctx == null) {
                    record("RUNTIME_RETURN_CAPTURE_FAILED kind=$kind error=no_framework_context")
                    return@execute
                }
                val extras = Bundle().apply {
                    putString("kind", kind.name)
                    putString(RuntimeCaptureContract.KEY_SNAPSHOT, encoded)
                    putBoolean(RuntimeCaptureContract.KEY_ORDERED, resolverOrdered)
                }
                val result = ctx.contentResolver.call(
                    Uri.parse("content://${RuntimeCaptureContract.AUTHORITY}"),
                    RuntimeCaptureContract.METHOD_PUBLISH, null, extras
                )
                if (result?.getBoolean("accepted", false) != true) {
                    record("RUNTIME_RETURN_CAPTURE_FAILED kind=$kind error=receiver_rejected")
                    return@execute
                }
                lastKey = key
                lastContent = digest
                lastMillis = now
                record("RUNTIME_RETURN_CAPTURE_DELIVERED kind=$kind source=$source count=${snapshot.totalComponents}")
            }.onFailure {
                record("RUNTIME_RETURN_CAPTURE_FAILED kind=$kind error=${it.javaClass.name}")
            }
        }
    }

    /** Works in both the system_server hook and the standalone IntentResolver process. */
    private fun findFrameworkContext(): Context? = runCatching {
        val threadClass = Class.forName("android.app.ActivityThread")
        val thread = threadClass.getDeclaredMethod("currentActivityThread")
            .apply { isAccessible = true }.invoke(null)
        val app = threadClass.getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }.invoke(null) as? Context
        app ?: thread?.let {
            threadClass.getDeclaredMethod("getSystemContext")
                .apply { isAccessible = true }.invoke(it) as? Context
        }
    }.getOrNull()
}
