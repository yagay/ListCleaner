package com.yagay.ListCleaner.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentIdentity
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.CustomOpenDefinition
import com.yagay.ListCleaner.domain.intentKind
import com.yagay.ListCleaner.domain.FilterPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant

/** Evidence-based discovery, not a claim to enumerate every installed intent filter. */
class IntentCatalog(private val context: Context) {
    private val mutableCandidates = MutableStateFlow<List<ComponentCandidate>>(emptyList())
    val candidates: StateFlow<List<ComponentCandidate>> = mutableCandidates.asStateFlow()

    suspend fun completeConfigured(items: List<ComponentCandidate>, selected: Set<ComponentRule>): List<ComponentCandidate> = withContext(Dispatchers.IO) {
        val known = items.map { it.rule.id }.toSet()
        items + selected.filter { it.id !in known }.map { rule ->
            val label = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(rule.packageName, 0).loadLabel(context.packageManager).toString()
            }.getOrDefault(rule.packageName)
            ComponentCandidate(
                rule,
                label,
                rule.className.substringAfterLast('.'),
                loadAppIcon(rule.packageName) { context.packageManager.getApplicationIcon(rule.packageName) },
                evidence = listOf(context.getString(R.string.catalog_configured_waiting)),
                unavailable = true
            )
        }
    }

    private val appIconCache = LruCache<String, Bitmap>(192)
    @Volatile private var cachedDefinitionFingerprint: String? = null
    @Volatile private var invalidated = true
    @Volatile private var cacheHitsSinceLastScan = 0L
    @Volatile var lastReport: String = "Not scanned"
        private set
    @Volatile var lastFileReport: String = "No real-file probe"
        private set
    @Volatile var scanWarning: String? = null
        private set

    private data class Probe(val intent: Intent, val broad: Boolean, val label: String)
    private data class QueryResult(val candidates: List<ComponentCandidate>, val raw: Int, val flags: Int = 0)

    /** Mark candidate discovery stale while keeping reusable app icons in memory. */
    fun invalidate(packageName: String? = null) {
        invalidated = true
        packageName?.let(appIconCache::remove)
    }

    suspend fun scan(
        customDefinitions: Map<OpenPreset, CustomOpenDefinition> = emptyMap(),
        force: Boolean = false
    ): List<ComponentCandidate> = withContext(Dispatchers.IO) {
        val fingerprint = customDefinitions.entries
            .sortedBy { it.key.ordinal }
            .joinToString("|") { (preset, definition) -> "$preset=$definition" }
        val cached = mutableCandidates.value
        if (!force && !invalidated && cached.isNotEmpty() && fingerprint == cachedDefinitionFingerprint) {
            cacheHitsSinceLastScan++
            return@withContext cached
        }

        val previousCacheHits = cacheHitsSinceLastScan
        cacheHitsSinceLastScan = 0L
        val found = mutableListOf<ComponentCandidate>()
        val known = mutableSetOf<String>()
        val report = StringBuilder(
            "startedAt=${Instant.now()}\nmanagerUid=${android.os.Process.myUid()}\n" +
                "customOpenTypes=${customDefinitions.size}\ncacheHitsSincePreviousScan=$previousCacheHits\n"
        )
        for (scheme in listOf("http", "https")) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
            runCatching {
                @Suppress("DEPRECATION")
                val menu = context.packageManager.queryIntentActivities(web, PackageManager.MATCH_DEFAULT_ONLY)
                @Suppress("DEPRECATION")
                val resolved = context.packageManager.resolveActivity(web, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
                report.appendLine("browserBaseline scheme=$scheme flags=0x${PackageManager.MATCH_DEFAULT_ONLY.toString(16)} count=${menu.size} resolved=${resolved?.packageName}/${resolved?.name}")
            }.onFailure { report.appendLine("browserBaseline scheme=$scheme error=${it.javaClass.simpleName}") }
        }
        var failures = 0
        scanWarning = null
        for (probe in probes(customDefinitions)) {
            currentCoroutineContext().ensureActive()
            try {
                val result = query(probe)
                val added = result.candidates.count { known.add(it.rule.id) }
                found += result.candidates
                report.appendLine("${probe.label} broad=${probe.broad} flags=0x${result.flags.toString(16)} raw=${result.raw} kept=${result.candidates.size} new=$added")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures++
                report.appendLine("${probe.label} ERROR=${failure.javaClass.name}")
            }
        }
        currentCoroutineContext().ensureActive()
        val result = merge(found)
        report.appendLine("finishedAt=${Instant.now()} unique=${result.size} failures=$failures")
        lastReport = report.toString()
        if (failures > 0) scanWarning = context.getString(R.string.catalog_partial_scan_failed, failures)
        mutableCandidates.value = result
        cachedDefinitionFingerprint = fingerprint
        invalidated = false
        result
    }

    suspend fun inspectFile(uri: Uri): List<ComponentCandidate> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            require(intent.intentKind() == IntentKind.OPEN) { context.getString(R.string.catalog_file_type_unconfirmed) }
            val label = "REAL_FILE scheme=${uri.scheme} mime=$mime"
            val result = query(Probe(intent, false, label), discovery = false)
            lastFileReport = buildString {
                appendLine("at=${Instant.now()} $label flags=0x${result.flags.toString(16)} raw=${result.raw} kept=${result.candidates.size}")
                appendLine("Diagnostic only; not merged into the management catalog. Maximum 5000 component details.")
                result.candidates.take(5_000).forEach {
                    appendLine("${it.rule.id} restricted=${it.restricted}")
                    it.evidence.forEach { reason -> appendLine("  $reason") }
                }
            }
            result.candidates
        } catch (failure: Exception) {
            lastFileReport = "at=${Instant.now()} scheme=${uri.scheme} error=${failure.javaClass.name}"
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun query(probe: Probe, discovery: Boolean = true): QueryResult {
        val kind = probe.intent.intentKind() ?: return QueryResult(emptyList(), 0)
        val flags = queryFlags(kind, discovery)
        check(flags and (PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS) == 0)
        val raw = context.packageManager.queryIntentActivities(probe.intent, flags)
        val candidates = raw.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val canonicalClass = ComponentIdentity.canonicalClassName(activity.packageName, activity.name, activity.targetActivity)
            val rule = ComponentRule(kind, activity.packageName, canonicalClass)
            if (!rule.isValid()) return@mapNotNull null
            val managerUid = android.os.Process.myUid()
            val targetUid = activity.applicationInfo?.uid ?: -1
            val restricted = FilterPolicy.catalogRestricted(activity.exported, targetUid, managerUid)
            val facts = buildList {
                add("SYSTEM_STATE activityEnabled=${activity.enabled} appEnabled=${activity.applicationInfo?.enabled}")
                add("exported=${activity.exported} targetUid=$targetUid managerUid=$managerUid")
                if (activity.targetActivity?.isNotBlank() == true) {
                    add("activityAlias=${activity.name} targetActivity=${activity.targetActivity} canonical=$canonicalClass")
                }
                if (restricted) add("RESTRICTED non-exported foreign component; excluded from ordinary catalog")
                activity.permission?.takeIf { it.isNotBlank() }?.let { permission ->
                    val granted = runCatching {
                        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                    }.getOrNull()
                    add("permission=$permission managerGranted=${granted ?: "unknown"}; source-app permission not inferred")
                }
            }
            ComponentCandidate(
                rule,
                runCatching { activity.applicationInfo.loadLabel(context.packageManager).toString() }.getOrDefault(activity.packageName),
                runCatching { info.loadLabel(context.packageManager).toString() }.getOrDefault(activity.name.substringAfterLast('.')),
                loadAppIcon(activity.packageName) {
                    runCatching { activity.applicationInfo.loadIcon(context.packageManager) }.getOrNull()
                        ?: context.packageManager.defaultActivityIcon
                },
                evidence = listOf(probe.label + " flags=0x${flags.toString(16)}") + facts,
                restricted = restricted,
                broadMatch = probe.broad
            )
        }
        return QueryResult(candidates, raw.size, flags)
    }

    private fun loadAppIcon(packageName: String, loader: () -> Drawable): Bitmap? {
        appIconCache.get(packageName)?.let { return it }
        return runCatching { loader().toBitmap(width = 96, height = 96) }.getOrNull()
            ?.also { appIconCache.put(packageName, it) }
    }

    private fun probes(customDefinitions: Map<OpenPreset, CustomOpenDefinition>): List<Probe> = buildList {
        for ((mime, file) in FILE_TYPES) {
            for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                add(Probe(Intent(action).setType(mime), false, "${action.substringAfterLast('.')} mime=$mime"))
            }
            for (scheme in listOf("content", "file", "https")) {
                val uri = when (scheme) {
                    "content" -> "content://com.yagay.ListCleaner.placeholder/$file"
                    "file" -> "file:///storage/emulated/0/Download/$file"
                    else -> "https://example.com/$file"
                }
                add(Probe(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime), false, "VIEW scheme=$scheme mime=$mime sample=$file"))
            }
        }

        customDefinitions.toSortedMap(compareBy { it.ordinal }).forEach { (preset, definition) ->
            val fallbackExt = definition.extensions.firstOrNull() ?: "custom"
            definition.mimeTypes.take(24).forEachIndexed { index, mime ->
                val sample = "custom-$index.$fallbackExt"
                for (scheme in listOf("content", "file")) {
                    val uri = if (scheme == "content") {
                        "content://com.yagay.ListCleaner.placeholder/$sample"
                    } else {
                        "file:///storage/emulated/0/Download/$sample"
                    }
                    add(Probe(
                        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime),
                        false,
                        "CUSTOM preset=${preset.name} VIEW scheme=$scheme mime=$mime sample=$sample"
                    ))
                }
            }
            definition.extensions.take(48).forEach { ext ->
                val sample = "custom.$ext"
                val uri = "content://com.yagay.ListCleaner.placeholder/$sample"
                add(Probe(
                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "application/octet-stream"),
                    false,
                    "CUSTOM preset=${preset.name} VIEW scheme=content mime=application/octet-stream sample=$sample"
                ))
            }
        }

        for (scheme in listOf("http", "https")) {
            add(Probe(
                Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
                false,
                "BROWSER scheme=$scheme"
            ))
        }
        listOf(
            "magnet" to "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
            "geo" to "geo:0,0?q=London",
            "mailto" to "mailto:test@example.com",
            "tel" to "tel:123456789",
            "sms" to "sms:123456789",
            "smsto" to "smsto:123456789"
        ).forEach { (scheme, value) ->
            add(Probe(Intent(Intent.ACTION_VIEW, Uri.parse(value)), false, "VIEW scheme=$scheme"))
        }
        add(Probe(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), false, "PROCESS_TEXT mime=text/plain"))
        for (mime in listOf("*/*", "image/*", "video/*", "audio/*")) {
            for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                add(Probe(Intent(action).setType(mime), true, "BROAD ${action.substringAfterLast('.')} mime=$mime"))
            }
            for (scheme in listOf("content", "file")) {
                val uri = if (scheme == "content") "content://com.yagay.ListCleaner.placeholder/item" else "file:///item"
                add(Probe(
                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime),
                    true,
                    "BROAD VIEW scheme=$scheme mime=$mime"
                ))
            }
        }
    }

    companion object {
        fun queryFlags(kind: IntentKind, discovery: Boolean): Int =
            (if (discovery) PackageManager.MATCH_ALL else 0) or
                (if (kind == IntentKind.PROCESS_TEXT) 0 else PackageManager.MATCH_DEFAULT_ONLY)

        fun merge(items: List<ComponentCandidate>): List<ComponentCandidate> =
            items.groupBy { it.rule.id }.values.map { matches ->
                val first = matches.firstOrNull { it.isCatalogCandidate } ?: matches.first()
                first.copy(
                    evidence = matches.flatMap { it.evidence }.distinct()
                        .sortedBy { !it.startsWith("REAL_FILE ") }.take(32),
                    restricted = matches.all { it.restricted },
                    unavailable = matches.all { it.unavailable },
                    broadMatch = matches.all { it.broadMatch }
                )
            }.sortedWith(compareBy({ it.rule.kind.ordinal }, { it.appLabel.lowercase() }, { it.rule.id }))

        private val FILE_TYPES = listOf(
            "text/plain" to "sample.txt", "text/html" to "sample.html",
            "image/jpeg" to "sample.jpg", "image/png" to "sample.png",
            "video/mp4" to "sample.mp4", "audio/mpeg" to "sample.mp3",
            "application/pdf" to "sample.pdf", "application/epub+zip" to "sample.epub",
            "application/vnd.android.package-archive" to "sample.apk", "application/x-bittorrent" to "sample.torrent",
            "text/markdown" to "sample.md", "text/csv" to "sample.csv", "application/json" to "sample.json",
            "application/xml" to "sample.xml", "image/svg+xml" to "sample.svg", "image/gif" to "sample.gif",
            "application/zip" to "sample.zip", "application/vnd.rar" to "sample.rar",
            "application/msword" to "sample.doc", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "sample.docx",
            "application/vnd.ms-excel" to "sample.xls", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "sample.xlsx",
            "application/vnd.ms-powerpoint" to "sample.ppt", "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "sample.pptx"
        )
    }
}
