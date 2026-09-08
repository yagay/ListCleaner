package com.yagay.ListCleaner.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.ListCleanerApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Creates a bounded, read-only diagnostic package. Root failures are recorded, not thrown. */
object DiagnosticCollector {
    private const val COMMAND_TIMEOUT_SECONDS = 20L
    private const val MAX_TEXT_BYTES = 8 * 1024 * 1024
    private const val MAX_LSPOSED_FILES = 12
    private const val MAX_LSPOSED_FILE_BYTES = 4 * 1024 * 1024

    suspend fun collect(context: Context, state: MainState, components: com.yagay.ListCleaner.data.RootComponentScan? = null, componentOperation: String = "not_observed"): File = runInterruptible(Dispatchers.IO) {
        val output = File.createTempFile("ListCleaner-diagnostic-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
                zip.addText("README.zh-CN.txt", readmeZh())
                zip.addText("README.en.txt", readmeEn())
                zip.addText("app/module-state.txt", moduleState(state))
                zip.addText("app/rules.txt", rules(state))
                zip.addText("app/root-components.txt", buildString {
                    appendLine("source=last_system_scan_not_live_ui storedBlacklist=false")
                    appendLine("observedAtMillis=${components?.observedAt ?: 0}")
                    appendLine("warning=${components?.warning ?: "not_scanned"}")
                    appendLine("count=${components?.items?.size ?: 0} limit=2000")
                    components?.items?.take(2000)?.forEach {
                        appendLine("${it.id} override=${it.overrideState} enabled=${it.enabled} appEnabled=${it.applicationEnabled} blocked=${it.blocked}")
                    }
                    appendLine("lastOperation:")
                    appendLine(componentOperation)
                })
                zip.addText("device/system-info.txt", systemInfo())
                val app = context.applicationContext as ListCleanerApp
                zip.addText("app/scan-probes.txt", app.catalog.lastReport)
                zip.addText("app/real-file-probe.txt", app.catalog.lastFileReport)
                zip.addText("app/catalog-status.txt", "source=current_session_scan\npersistentCatalog=false\nscanWarning=" + app.catalog.scanWarning)
                val candidateEvidence = DiagnosticBuffer(MAX_TEXT_BYTES)
                fun appendCandidateLine(line: String) {
                    val bytes = (line + "\n").toByteArray(StandardCharsets.UTF_8)
                    candidateEvidence.append(bytes, bytes.size)
                }
                appendCandidateLine("UI snapshot; sample matches are not proof of real-file launchability.")
                state.candidates.forEach { candidate ->
                    appendCandidateLine("${candidate.rule.id} restricted=${candidate.restricted} broad=${candidate.broadMatch} unavailable=${candidate.unavailable}" +
                        " catalogEligible=${catalogVisible(candidate, candidate.rule in state.selected, state.uiFilter)}")
                    candidate.evidence.forEach { appendCandidateLine("  $it") }
                }
                zip.addText("app/scan-candidates.txt", "truncated=${candidateEvidence.truncated()}\n" +
                    candidateEvidence.snapshot().toString(StandardCharsets.UTF_8))
                zip.addCapture("logcat/buffer-state.txt", root("logcat -g -b all", 128 * 1024))
                zip.addCapture("root/root-status.txt", root("id; getenforce; command -v su; echo KERNEL=$(uname -a)"))
                zip.addCapture("logcat/ListCleaner.txt", root(
                    "logcat -d -v threadtime -b all ListCleaner:V ListCleaner.Diagnostic:V " +
                    "AndroidRuntime:E PackageManager:V PackageManagerService:V ActivityTaskManager:I " +
                        "LSPosedFramework:V LSPosedService:V ResolverActivity:V ChooserActivity:V " +
                        "ResolverListAdapter:V ResolverListController:V '*:S'"
                ))
                zip.addCapture("logcat/activity-events.txt", root(
                    "logcat -d -v threadtime -b events am_proc_start:I am_proc_died:I am_crash:I " +
                        "am_anr:I wm_create_activity:I wm_new_intent:I wm_resume_activity:I '*:S'"
                ))
                zip.addCapture("logcat/crash.txt", root("logcat -b crash -d -v threadtime"))
                zip.addCapture("device/processes.txt", root("ps -A -o USER,PID,PPID,NAME", 1024 * 1024))
                zip.addCapture("app/installed-package.txt", root("dumpsys package com.yagay.ListCleaner", 2 * 1024 * 1024))
                zip.addCapture("app/resolver-package.txt", root("dumpsys package com.android.intentresolver", 2 * 1024 * 1024))
                zip.addCapture("app/systemui-package.txt", root("dumpsys package com.android.systemui", 2 * 1024 * 1024))
                zip.addCapture("root/lsposed-modules.txt", root(
                    "for d in /data/adb/modules/*; do [ -d \"\$d\" ] || continue; " +
                        "echo ===\$(basename \"\$d\")===; " +
                        "sed -n '1,40p' \"\$d/module.prop\" 2>/dev/null; done"
                ))
                addRecentLsposedLogs(zip)
                zip.addText("collection-finished.txt", "finishedAt=${Instant.now()}\n")
            }
            output
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
    }

    private fun addRecentLsposedLogs(zip: ZipOutputStream) {
        val evidence = DiagnosticEvidence()
        val listing = root("find /data/adb/lspd/log -type f -mmin -1440 -name '*.log' -exec stat -c '%Y %n' {} \\;", maxBytes = 512 * 1024)
        zip.addCapture("lsposed/listing.txt", listing)
        listing.bytes.toString(StandardCharsets.UTF_8).lineSequence().mapNotNull { line ->
            val fields = line.split(' ', limit = 2)
            val modified = fields.firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
            val path = fields.getOrNull(1) ?: return@mapNotNull null
            if (!path.startsWith("/data/adb/lspd/log/") || '\r' in path) return@mapNotNull null
            modified to path
        }.sortedByDescending { it.first }.take(MAX_LSPOSED_FILES).forEachIndexed { index, (_, path) ->
            val safeName = path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            val source = "lsposed/${index.toString().padStart(2, '0')}-$safeName"
            val capture = root("cat -- '${path.replace("'", "'\\''")}'", MAX_LSPOSED_FILE_BYTES)
            zip.addCapture(source, capture)
            capture.bytes.toString(StandardCharsets.UTF_8).lineSequence().forEach { line -> evidence.accept(source, line) }
        }
        zip.addText("analysis/module-evidence.txt", evidence.report())
    }

    private fun moduleState(state: MainState): String = buildString {
        appendLine("generatedAt=${Instant.now()}"); appendLine("appVersion=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("managerUid=${android.os.Process.myUid()}"); appendLine("serviceConnected=${state.module.connected}")
        appendLine("libxposedApi=${state.module.apiVersion ?: "unknown"}"); appendLine("scopeKnown=${state.module.scopeKnown}")
        appendLine("grantedScope=${state.module.grantedScope.sorted().joinToString()}"); appendLine("missingScope=${state.module.missingScope.sorted().joinToString()}")
        appendLine("displayMode=${state.displayMode.name}"); appendLine("diagnosticMode=${state.diagnosticMode}"); appendLine("syncStatus=${state.syncStatus}")
        appendLine("systemConfigAcknowledged=${state.runtime.ready}"); appendLine("statusSource=last_observed_no_sync_before_export")
        appendLine("runtimeObservedAtMillis=${state.runtime.observedAtMillis}"); appendLine("runtimeObservationAgeMillis=${System.currentTimeMillis() - state.runtime.observedAtMillis}")
        appendLine("configDigest=${state.runtime.digest}"); appendLine("queryHits=${state.runtime.queryHits}"); appendLine("visibilityHits=${state.runtime.visibilityHits}")
        appendLine("orderingHits=${state.runtime.orderingHits}"); appendLine("recoveryDecisionRequired=${state.runtime.needsDecision}"); appendLine("runtimeMessage=${state.runtime.message}")
        appendLine("uiFilter=${state.uiFilter} category=${state.filter}"); appendLine("searchActive=${state.query.isNotBlank()} candidates=${state.candidates.size} visibleGroups=${state.groups.size}")
        state.module.runningTargets.forEach { appendLine("target=${it.processName}|${it.state}|version=${it.version}") }
        state.module.detection.hosts.forEach { appendLine("host=${it.packageName}|${it.className}|${it.processName}|${it.scenarios.sorted().joinToString()}") }
        state.module.error?.let { appendLine("moduleError=$it") }
    }

    private fun rules(state: MainState): String = buildString {
        appendLine("selectedCount=${state.selected.size}"); state.selected.sortedBy { it.id }.forEach { appendLine(it.id) }; appendLine(); appendLine("priorities:")
        state.priorities.apps.entries.sortedBy { it.key.ordinal }.forEach { (kind, packages) -> appendLine("${kind.name}=${packages.joinToString()}") }
        appendLine("openTypeRules:"); state.openTypesExplicit.rules.entries.sortedBy { it.key.ordinal }.forEach { (preset, ids) -> appendLine("${preset.name}=${ids.sorted().joinToString()}") }
        appendLine("openTypePriorities:"); state.openTypesExplicit.priorities.entries.sortedBy { it.key.ordinal }.forEach { (preset, packages) -> appendLine("${preset.name}=${packages.joinToString()}") }
        appendLine("customOpenTypes:"); state.openTypesExplicit.customDefinitions.entries.sortedBy { it.key.ordinal }.forEach { (preset, definition) -> appendLine("${preset.name}|title=${definition.title}|mimes=${definition.mimeTypes.sorted().joinToString()}|extensions=${definition.extensions.sorted().joinToString()}") }
    }

    private fun systemInfo(): String = buildString {
        appendLine("generatedAt=${Instant.now()}"); appendLine("timezone=${ZoneId.systemDefault()}"); appendLine("uptimeMs=${SystemClock.elapsedRealtime()}")
        appendLine("manufacturer=${Build.MANUFACTURER}"); appendLine("brand=${Build.BRAND}"); appendLine("model=${Build.MODEL}"); appendLine("device=${Build.DEVICE}")
        appendLine("product=${Build.PRODUCT}"); appendLine("sdk=${Build.VERSION.SDK_INT}"); appendLine("release=${Build.VERSION.RELEASE}"); appendLine("securityPatch=${Build.VERSION.SECURITY_PATCH}")
        appendLine("incremental=${Build.VERSION.INCREMENTAL}"); appendLine("fingerprint=${Build.FINGERPRINT}"); appendLine("supportedAbis=${Build.SUPPORTED_ABIS.joinToString()}")
    }

    private fun readmeZh(): String = """
        ListCleaner 诊断包
        此诊断包以只读方式生成，不会执行改变系统状态的命令。

        内容：
        - app/：模块状态、LSPosed 作用域、运行目标、当前规则和分类 OPEN 配置
        - device/：Android 与构建信息
        - logcat/：模块、框架、选择器日志，Activity 事件、崩溃和日志缓冲区状态
        - lsposed/：24 小时内修改的最新 12 个 LSPosed 日志，每个最多保留 4 MiB
        - root/：Root 状态和已安装模块元数据

        logcat 和 LSPosed 日志可能包含包名、路径或用户活动，分享前请检查。
        ListCleaner 运行时消息只记录文件扩展名和识别类型，不记录完整文件名或 URI。
        大型数据流会保留开头和最新结尾，并明确标记被省略的字节。
        每个条目的存储有上限（通常为 8 MiB）；已经被覆盖的启动日志无法恢复。
        模块还会通过 API 102 log(int, String, String) 向框架日志写入生命周期和限频诊断消息。
        模块不会记录完整 Intent 或 extras。
    """.trimIndent()

    private fun readmeEn(): String = """
        ListCleaner diagnostic package
        This diagnostic package is generated read-only. No command changes system state.

        Contents:
        - app/: module state, LSPosed scope, running targets, active rules and typed OPEN configuration
        - device/: Android and build information
        - logcat/: module, framework and resolver logs, activity events, crash and buffer state
        - lsposed/: newest 12 LSPosed log files modified within 24 hours, each retaining up to 4 MiB
        - root/: Root status and installed module metadata

        Logcat and LSPosed logs may contain package names, paths, or user activity. Review them before sharing.
        Runtime ListCleaner messages log file extensions and recognized types, not full file names or URIs.
        Large streams retain their beginning and latest end, with an explicit omitted-byte marker.
        Storage is bounded per entry (normally 8 MiB); startup logs already overwritten cannot be recovered.
        The module also writes lifecycle and rate-limited diagnostic messages to the framework log through
        API 102 log(int, String, String). The module does not log full Intents or extras.
    """.trimIndent()

    private fun root(command: String, maxBytes: Int = MAX_TEXT_BYTES): Capture {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Collection cancelled")
        return capture(listOf("su", "-c", command), maxBytes)
    }

    private fun capture(command: List<String>, maxBytes: Int): Capture {
        val buffer = DiagnosticBuffer(maxBytes); val startedAt = Instant.now().toString(); val readFailure = AtomicReference<String?>(null); var process: Process? = null
        return try {
            val running = ProcessBuilder(command).redirectErrorStream(true).start(); process = running
            val reader = Thread({ try { running.inputStream.use { input -> val chunk = ByteArray(16 * 1024); while (true) { val count = input.read(chunk); if (count < 0) break; buffer.append(chunk, count) } } } catch (failure: Exception) { readFailure.set(failure.toString()) } }, "diagnostic-drain").apply { isDaemon = true; start() }
            val completed = running.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS); if (!completed) { running.destroyForcibly(); running.waitFor(2, TimeUnit.SECONDS) }; reader.join(2_000)
            Capture(buffer.snapshot(), if (completed) running.exitValue() else -1, !completed, buffer.truncated(), startedAt, Instant.now().toString(), buffer.totalBytes(), reader.isAlive || readFailure.get() != null, readFailure.get())
        } catch (interrupted: InterruptedException) { throw interrupted } catch (failure: Exception) { Capture(failure.stackTraceToString().toByteArray(), -1, false, false, startedAt, Instant.now().toString(), buffer.totalBytes(), true) } finally { runCatching { process?.destroyForcibly() }; runCatching { process?.inputStream?.close() }; runCatching { process?.outputStream?.close() }; runCatching { process?.errorStream?.close() } }
    }

    private fun ZipOutputStream.addCapture(name: String, capture: Capture) {
        val header = buildString { appendLine("exitCode=${capture.exitCode}"); appendLine("timedOut=${capture.timedOut}"); appendLine("truncated=${capture.truncated}"); appendLine("startedAt=${capture.startedAt}"); appendLine("finishedAt=${capture.finishedAt}"); appendLine("observedBytes=${capture.observedBytes}"); appendLine("incompleteRead=${capture.incompleteRead}"); appendLine("readError=${capture.readError ?: "none"}"); appendLine() }.toByteArray()
        putNextEntry(ZipEntry(name)); write(header); write(capture.bytes); closeEntry()
    }

    private fun ZipOutputStream.addText(name: String, content: String) { putNextEntry(ZipEntry(name)); write(content.toByteArray(StandardCharsets.UTF_8)); closeEntry() }

    private data class Capture(val bytes: ByteArray, val exitCode: Int, val timedOut: Boolean, val truncated: Boolean, val startedAt: String, val finishedAt: String, val observedBytes: Long, val incompleteRead: Boolean, val readError: String? = null)
}
