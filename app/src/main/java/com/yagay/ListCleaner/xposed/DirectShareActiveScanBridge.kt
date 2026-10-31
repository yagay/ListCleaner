package com.yagay.ListCleaner.xposed

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ShortcutInfo
import com.yagay.ListCleaner.domain.DirectShareType
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.ObservedEntryRecord
import com.yagay.ListCleaner.domain.ObservedEntryCacheCodec
import com.yagay.ListCleaner.domain.DirectShareScanTransfer
import com.yagay.ListCleaner.domain.SyntheticEntryKeys
import java.lang.reflect.Method
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Read-only Direct Share scan initiated by an authenticated manager Runtime Probe.
 * Runs only in system_server, outside the PackageManager Binder hook's thread.
 * Reads the *unfiltered* ShortcutServiceInternal list, not the Chooser result
 * modified by our normal filtering hooks. Never starts an app or share sheet.
 */
internal object DirectShareActiveScanBridge {
    const val STATUS_PREFIX = "direct_share_scan_status_user_"
    // AOSP registers this framework class in LocalServices; it is not in com.android.server.pm.
    internal const val SHORTCUT_SERVICE_INTERNAL_CLASS = "android.content.pm.ShortcutServiceInternal"
    private const val MAX_RESULTS_PER_QUERY = 2048
    private const val MAX_DISTINCT_RECORDS = ObservedEntryCacheCodec.MAX_ENTRIES
    private const val STATUS_LIMIT = 200
    private val busy = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LC-ActiveDirectShare").apply { isDaemon = true }
    }
    @Volatile private var loader: ClassLoader? = null
    @Volatile private var logger: ((String) -> Unit)? = null

    /** Immutable, token-scoped result in system_server, never RemotePreferences writes. */
    private data class ScanState(
        val userId: Int,
        val token: String,
        val status: String,
        val records: Int = 0,
        val parts: List<String> = emptyList(),
        val error: String = "",
        val startedAt: Long = System.currentTimeMillis(),
    )
    data class ReadResult(
        val status: String,
        val records: Int,
        val chunks: Int,
        val payload: String,
        val error: String,
    )
    @Volatile private var state: ScanState? = null

    /** Read only for the previously authenticated manager UID + digest + token. */
    fun read(userId: Int, token: String, chunkIndex: Int): ReadResult? {
        val current = state ?: return null
        if (current.userId != userId || current.token != token ||
            System.currentTimeMillis() - current.startedAt !in 0..120_000L ||
            chunkIndex !in -1 until current.parts.size) return null
        return ReadResult(
            current.status, current.records, current.parts.size,
            if (chunkIndex >= 0) current.parts[chunkIndex] else "",
            current.error,
        )
    }

    fun initialize(
        classLoader: ClassLoader,
        record: (String) -> Unit,
    ) {
        loader = classLoader
        logger = record
        record("DIRECT_SCAN_BRIDGE_READY")
    }

    /** Only called after the Runtime Protocol has verified the manager UID. */
    fun request(userId: Int, token: String): Boolean {
        if (userId !in 0..21474 || token.length !in 1..64 ||
            token.any { !it.isLetterOrDigit() && it != '-' && it != '_' } ||
            loader == null || !busy.compareAndSet(false, true)
        ) return false
        state = ScanState(userId, token, "running")
        executor.execute {
            try {
                scan(userId, token)
            } catch (error: Throwable) {
                val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
                val reason = cause.message.orEmpty().take(100).replace('|', '_')
                log("DIRECT_SCAN_FAILED user=" + userId + " error=" + cause.javaClass.simpleName + " reason=" + reason)
                state = ScanState(userId, token, "failed",
                    error = cause.javaClass.simpleName + ":" + reason)
            } finally {
                busy.set(false)
            }
        }
        return true
    }

    private fun scan(userId: Int, token: String) {
        val classLoader = loader ?: error("classloader_missing")
        val service = obtainService(classLoader) ?: error("shortcut_local_service_unavailable")
        val method = findGetShareTargets(service.javaClass)
            ?: error("getShareTargets_signature_unavailable")
        method.isAccessible = true

        val byKey = linkedMapOf<String, ObservedEntryRecord>()
        var successes = 0
        var failures = 0
        // Exact MIME requests plus wildcard families for manifests using
        // broad or subtype-specific share-target declarations.
        for ((family, mime) in MIME_QUERIES) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SEND)
                addDataType(mime)
            }
            try {
                val result = method.invoke(service, "android", filter, userId)
                val values = result as? List<*>
                    ?: error("unexpected_result_type")
                if (values.size > MAX_RESULTS_PER_QUERY) error("response_limit_exceeded")
                successes++
                for (value in values) {
                    val shortcut = ReflectionAccess.invokeNoArg(value, "getShortcutInfo") as? ShortcutInfo
                        ?: continue
                    if (!shortcut.isEnabled) continue
                    val pkg = shortcut.`package`?.takeIf {
                        it.isNotBlank() && it.length <= 255
                    } ?: continue
                    val id = shortcut.id?.takeIf { it.isNotBlank() && it.length <= 300 }
                        ?: continue
                    val target = ReflectionAccess.invokeNoArg(value, "getTargetComponent") as? ComponentName
                    val key = SyntheticEntryKeys.directShareClass(
                        target?.className ?: shortcut.activity?.className, id
                    )
                    val now = System.currentTimeMillis()
                    val candidate = ObservedEntryRecord(
                        kind = IntentKind.DIRECT_SHARE.name,
                        packageName = pkg,
                        syntheticClass = key,
                        activityClass = target?.className ?: shortcut.activity?.className,
                        label = shortcut.shortLabel?.toString().orEmpty().take(160),
                        observedAt = now,
                        userId = userId,
                        directShareFamilies = setOf(family.name),
                    ).validatedOrNull() ?: continue
                    val previous = byKey[candidate.key]
                    byKey[candidate.key] = candidate.copy(
                        directShareFamilies = previous?.directShareFamilies.orEmpty() +
                            candidate.directShareFamilies
                    )
                    if (byKey.size > MAX_DISTINCT_RECORDS) error("scan_record_limit")
                }
            } catch (error: Exception) {
                failures++
                val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
                log("DIRECT_SCAN_QUERY_FAILED user=$userId mime=$mime error=${cause.javaClass.simpleName} reason=${cause.message.orEmpty().take(100)}")
            }
        }
        // Failed or zero queries do not invalidate previous observations or cleanup rules.
        if (successes == 0) error("all_queries_failed")
        val encoded = ObservedEntryCacheCodec.encode(byKey.values)
        val parts = DirectShareScanTransfer.split(encoded)
        state = ScanState(userId, token, "ok", byKey.size, parts)
        log("DIRECT_SCAN_OK user=" + userId + " targets=" + byKey.size +
            " queries=" + successes + " failures=" + failures + " chunks=" + parts.size)
    }

    /** Selects the AOSP local service signature, ignoring incompatible OEM overloads. */
    internal fun findGetShareTargets(type: Class<*>): Method? =
        ReflectionAccess.hierarchyMethods(type).firstOrNull { method ->
            method.name == "getShareTargets" && method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == IntentFilter::class.java &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                List::class.java.isAssignableFrom(method.returnType)
        }

    private fun obtainService(classLoader: ClassLoader): Any? = runCatching {
        val local = Class.forName("com.android.server.LocalServices", false, classLoader)
        val internal = Class.forName(SHORTCUT_SERVICE_INTERNAL_CLASS, false, classLoader)
        local.getDeclaredMethod("getService", Class::class.java).apply {
            isAccessible = true
        }.invoke(null, internal)
    }.onFailure {
        log("DIRECT_SCAN_SERVICE_UNAVAILABLE error=${it.javaClass.simpleName}")
    }.getOrNull()

    private fun log(value: String) { logger?.invoke(value) }

    private val MIME_QUERIES: List<Pair<DirectShareType, String>> = listOf(
        DirectShareType.TEXT to "text/plain",
        DirectShareType.TEXT to "text/*",
        DirectShareType.IMAGE to "image/*",
        DirectShareType.IMAGE to "image/jpeg",
        DirectShareType.IMAGE to "image/png",
        DirectShareType.VIDEO to "video/*",
        DirectShareType.VIDEO to "video/mp4",
        DirectShareType.AUDIO to "audio/*",
        DirectShareType.AUDIO to "audio/mpeg",
        DirectShareType.FILE to "application/*",
        DirectShareType.FILE to "application/pdf",
        DirectShareType.FILE to "application/octet-stream",
    )
}
