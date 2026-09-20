package com.yagay.ListCleaner.data

import android.util.Log
import com.yagay.ListCleaner.domain.normalizeBrowserHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Read-only discovery of web domains declared by installed apps.
 *
 * Android's `pm get-app-links --user cur` can print state for all packages when no package name
 * is supplied. This lets the manager discover App Link hosts before the user manually adds one.
 * Failure (including no root grant) is intentionally silent: configured/manual hosts still work.
 */
class BrowserLinkDiscovery {
    @Volatile private var cachedHosts: Set<String> = emptySet()
    @Volatile private var lastScanMillis: Long = 0L

    suspend fun discover(force: Boolean = false): Set<String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && cachedHosts.isNotEmpty() && now - lastScanMillis < CACHE_MILLIS) {
            return@withContext cachedHosts
        }

        val output = runCatching { runReadOnlyCommand() }
            .onFailure { Log.i(TAG, "Automatic App Link discovery unavailable: ${it.javaClass.simpleName}") }
            .getOrNull()
            ?: return@withContext cachedHosts

        val discovered = parseAppLinkHosts(output)
        if (discovered.isNotEmpty()) cachedHosts = discovered
        lastScanMillis = now
        cachedHosts
    }

    private fun runReadOnlyCommand(): String {
        val process = ProcessBuilder("su", "-c", "pm get-app-links --user cur")
            .redirectErrorStream(true)
            .start()
        val buffer = ByteArrayOutputStream()
        val reader = Thread({
            process.inputStream.use { input ->
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    if (total < MAX_OUTPUT_BYTES) {
                        val accepted = minOf(count, MAX_OUTPUT_BYTES - total)
                        buffer.write(chunk, 0, accepted)
                        total += accepted
                    }
                }
            }
        }, "listcleaner-app-links").apply {
            isDaemon = true
            start()
        }

        try {
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw IllegalStateException("app_link_discovery_timeout")
            }
            reader.join(1500)
            if (process.exitValue() != 0) throw IllegalStateException("app_link_discovery_denied")
            return buffer.toString(StandardCharsets.UTF_8.name())
        } finally {
            process.destroy()
            runCatching { process.inputStream.close() }
            runCatching { process.outputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }

    private companion object {
        const val TAG = "ListCleaner.AppLinks"
        const val CACHE_MILLIS = 5 * 60 * 1000L
        const val COMMAND_TIMEOUT_SECONDS = 20L
        const val MAX_OUTPUT_BYTES = 2 * 1024 * 1024
    }
}

internal fun parseAppLinkHosts(output: String, maxHosts: Int = 192): Set<String> {
    data class RankedHost(val host: String, val rank: Int)

    val ranked = linkedMapOf<String, Int>()
    var inDomainState = false
    var selectionState = 0 // 1 enabled, 2 disabled

    fun add(raw: String, rank: Int) {
        val host = normalizeBrowserHost(raw) ?: return
        if ('.' !in host) return
        val previous = ranked[host]
        if (previous == null || rank < previous) ranked[host] = rank
    }

    output.lineSequence().forEach { rawLine ->
        val trimmed = rawLine.trim()
        when {
            trimmed == "Domain verification state:" -> {
                inDomainState = true
                selectionState = 0
                return@forEach
            }
            trimmed == "Selection state:" -> {
                inDomainState = false
                selectionState = 0
                return@forEach
            }
            trimmed == "Enabled:" -> {
                inDomainState = false
                selectionState = 1
                return@forEach
            }
            trimmed == "Disabled:" -> {
                inDomainState = false
                selectionState = 2
                return@forEach
            }
            trimmed.startsWith("User ") || trimmed.startsWith("Verification link handling") ||
                trimmed.startsWith("ID:") || trimmed.startsWith("Signatures:") -> {
                if (trimmed.startsWith("User ")) {
                    inDomainState = false
                    selectionState = 0
                }
                return@forEach
            }
        }

        if (inDomainState) {
            val match = DOMAIN_STATE.matchEntire(trimmed) ?: return@forEach
            val state = match.groupValues[2].lowercase()
            val rank = when (state) {
                "verified", "approved" -> 0
                "none" -> 3
                "denied" -> 4
                else -> 2
            }
            add(match.groupValues[1], rank)
        } else if (selectionState != 0) {
            val host = trimmed.removePrefix("-").trim()
            if (host.isNotEmpty() && ':' !in host && ' ' !in host) {
                add(host, if (selectionState == 1) 1 else 4)
            }
        }
    }

    return ranked.entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(maxHosts.coerceAtLeast(0))
        .mapTo(linkedSetOf()) { it.key }
}

private val DOMAIN_STATE = Regex("""([A-Za-z0-9.-]+):\s+(\S+)""")
