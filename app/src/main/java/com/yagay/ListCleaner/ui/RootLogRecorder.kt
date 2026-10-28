package com.yagay.ListCleaner.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Manually controlled, bounded all-user logcat capture for diagnostics. */
internal object RootLogRecorder {
    private const val FOLDER = "multiuser-logcat"
    private fun folder(context: Context) = File(context.filesDir, FOLDER).apply { mkdirs() }
    private fun pidFile(context: Context) = File(folder(context), "recorder.pid")
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun root(command: String): Boolean {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true).start()
        val completed = process.waitFor(8, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        return completed && process.exitValue() == 0
    }

    suspend fun start(context: Context): Boolean = withContext(Dispatchers.IO) {
        val dir = folder(context)
        if (isRecording(context)) return@withContext true
        // Rotate at 10 MiB, retaining at most three generations.
        val target = File(dir, "all-users.log")
        val pid = pidFile(context)
        root("nohup logcat -b all -v threadtime,uid -f ${quote(target.absolutePath)} -r 10240 -n 3 " +
            ">/dev/null 2>&1 & echo \$! > ${quote(pid.absolutePath)}")
    }

    suspend fun stop(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pid = pidFile(context)
        if (!pid.exists()) return@withContext true
        val value = pid.readText().trim().toIntOrNull()
        if (value == null || value <= 0) return@withContext false
        val ok = root("kill $value 2>/dev/null; chmod 644 ${quote(File(folder(context), "all-users.log").absolutePath)}* 2>/dev/null; rm -f ${quote(pid.absolutePath)}")
        ok
    }

    fun isRecording(context: Context): Boolean {
        val pid = pidFile(context).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
            ?: return false
        return pid > 0 && runCatching { root("kill -0 $pid 2>/dev/null") }.getOrDefault(false)
    }

    fun recordings(context: Context): List<File> =
        folder(context).listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("all-users.log") }
            .sortedBy { it.name }
}
