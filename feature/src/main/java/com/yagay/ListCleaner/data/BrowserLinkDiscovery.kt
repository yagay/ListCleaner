package com.yagay.ListCleaner.data

import android.util.Log
import com.yagay.ListCleaner.domain.normalizeBrowserHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Read-only discovery of installed Android App Links.
 *
 * The first source is `pm get-app-links --user cur`, which tells us which package owns each
 * declared/verified domain. The second source is the package resolver table from `dumpsys package`.
 * Resolver output contains the actual VIEW+BROWSABLE http(s) IntentFilters, including authorities
 * and path matchers, so discovery does not depend on a guessed URL such as https://host/.
 *
 * Both sources are read-only. If root or an OEM command is unavailable, callers can still fall back
 * to PackageManager probing and manually configured hosts.
 */
class BrowserLinkDiscovery {
    @Volatile private var cached = BrowserLinkDiscoveryResult()
    @Volatile private var cacheValid = false
    @Volatile private var lastSuccessfulScanMillis: Long = 0L
    @Volatile private var lastAttemptMillis: Long = 0L

    suspend fun discover(force: Boolean = false): Set<String> = discoverDetailed(force).hosts

    suspend fun discoverDetailed(force: Boolean = false): BrowserLinkDiscoveryResult =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && cacheValid && now - lastSuccessfulScanMillis < CACHE_MILLIS) {
                return@withContext cached
            }
            if (!force && now - lastAttemptMillis < FAILURE_BACKOFF_MILLIS) {
                return@withContext cached
            }
            lastAttemptMillis = now

            val appLinksOutput = runCatching { runReadOnlyCommand("pm get-app-links --user cur") }
                .onFailure {
                    Log.i(TAG, "Automatic App Link discovery unavailable: ${it.javaClass.simpleName}")
                }
                .getOrNull()
                ?: return@withContext cached

            val declarations = parseAppLinkDeclarations(appLinksOutput)
            var result = BrowserLinkDiscoveryResult(
                hosts = declarations.hosts,
                packagesByHost = declarations.packagesByHost
            )

            val packages = declarations.packages
            if (packages.isNotEmpty()) {
                val resolverOutput = runCatching { runResolverDump(packages) }
                    .onFailure {
                        Log.i(TAG, "Resolver-table discovery unavailable: ${it.javaClass.simpleName}")
                    }
                    .getOrNull()
                if (!resolverOutput.isNullOrBlank()) {
                    val handlers = parseDeclaredWebHandlers(resolverOutput)
                    if (handlers.isNotEmpty()) {
                        val handlersByHost = handlers
                            .groupBy { it.host }
                            .mapValues { (_, values) ->
                                values.mapTo(linkedSetOf()) {
                                    DeclaredDeepLinkHandler(it.packageName, it.className)
                                }
                            }
                        val resolverPackages = handlers
                            .groupBy { it.host }
                            .mapValues { (_, values) ->
                                values.mapTo(linkedSetOf()) { it.packageName }
                            }
                        result = BrowserLinkDiscoveryResult(
                            hosts = (result.hosts + handlersByHost.keys).toSortedSet(),
                            packagesByHost = mergeHostPackages(result.packagesByHost, resolverPackages),
                            declaredHandlersByHost = handlersByHost
                        )
                    }
                }
            }

            cached = result
            cacheValid = true
            lastSuccessfulScanMillis = System.currentTimeMillis()
            cached
        }

    private fun runResolverDump(packages: Set<String>): String {
        val safePackages = packages
            .asSequence()
            .filter(PACKAGE_NAME::matches)
            .take(MAX_PACKAGES)
            .toList()
        if (safePackages.isEmpty()) return ""

        val script = buildString {
            append("for p in")
            safePackages.forEach { append(' ').append(it) }
            append("; do printf '\\n")
            append(PACKAGE_MARKER)
            append("%s\\n' \"\$p\"; dumpsys package \"\$p\" 2>/dev/null | sed '/^$/q'; done")
        }
        return runReadOnlyCommand(script, RESOLVER_OUTPUT_BYTES, RESOLVER_TIMEOUT_SECONDS)
    }

    private fun runReadOnlyCommand(
        command: String,
        maxOutputBytes: Int = MAX_OUTPUT_BYTES,
        timeoutSeconds: Long = COMMAND_TIMEOUT_SECONDS
    ): String {
        val process = ProcessBuilder("su", "-c", command)
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
                    if (total < maxOutputBytes) {
                        val accepted = minOf(count, maxOutputBytes - total)
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
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
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
        const val FAILURE_BACKOFF_MILLIS = 45 * 1000L
        const val COMMAND_TIMEOUT_SECONDS = 20L
        const val RESOLVER_TIMEOUT_SECONDS = 35L
        const val MAX_OUTPUT_BYTES = 2 * 1024 * 1024
        const val RESOLVER_OUTPUT_BYTES = 4 * 1024 * 1024
        const val MAX_PACKAGES = 160
        const val PACKAGE_MARKER = "@@LISTCLEANER_PACKAGE@@"
        val PACKAGE_NAME = Regex("""[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+""")
    }
}

data class BrowserLinkDiscoveryResult(
    val hosts: Set<String> = emptySet(),
    val packagesByHost: Map<String, Set<String>> = emptyMap(),
    val declaredHandlersByHost: Map<String, Set<DeclaredDeepLinkHandler>> = emptyMap()
)

data class DeclaredDeepLinkHandler(
    val packageName: String,
    val className: String
)

internal data class ParsedAppLinkDeclarations(
    val hosts: Set<String>,
    val packagesByHost: Map<String, Set<String>>,
    val packages: Set<String>
)

internal data class DeclaredWebHandler(
    val host: String,
    val packageName: String,
    val className: String
)

internal fun parseAppLinkHosts(output: String, maxHosts: Int = 192): Set<String> =
    parseAppLinkDeclarations(output, maxHosts).hosts

internal fun parseAppLinkDeclarations(
    output: String,
    maxHosts: Int = 192
): ParsedAppLinkDeclarations {
    val ranked = linkedMapOf<String, Int>()
    val packagesByHost = linkedMapOf<String, MutableSet<String>>()
    val packages = linkedSetOf<String>()
    var currentPackage: String? = null
    var inDomainState = false
    var selectionState = 0 // 1 enabled, 2 disabled

    fun add(raw: String, rank: Int) {
        val host = normalizeBrowserHost(raw) ?: return
        if ('.' !in host) return
        val previous = ranked[host]
        if (previous == null || rank < previous) ranked[host] = rank
        currentPackage?.let { packageName ->
            packagesByHost.getOrPut(host) { linkedSetOf() }.add(packageName)
        }
    }

    output.lineSequence().forEach { rawLine ->
        val trimmed = rawLine.trim()
        val packageHeader = rawLine.isNotBlank() &&
            !rawLine.first().isWhitespace() &&
            trimmed.endsWith(':') &&
            APP_LINK_PACKAGE_HEADER.matches(trimmed.removeSuffix(":"))
        if (packageHeader) {
            currentPackage = trimmed.removeSuffix(":")
            packages += currentPackage!!
            inDomainState = false
            selectionState = 0
            return@forEach
        }

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

    val orderedHosts = ranked.entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(maxHosts.coerceAtLeast(0))
        .map { it.key }
    val allowed = orderedHosts.toSet()
    return ParsedAppLinkDeclarations(
        hosts = orderedHosts.toCollection(linkedSetOf()),
        packagesByHost = packagesByHost
            .filterKeys { it in allowed }
            .mapValues { (_, value) -> value.toSet() },
        packages = packages
    )
}

/**
 * Parse the Activity Resolver Table printed by dumpsys package.
 *
 * Unlike URL probing, this sees path-restricted filters directly. A filter becomes a Deep Link
 * handler only when it explicitly declares VIEW + BROWSABLE + http(s) + an authority.
 */
internal fun parseDeclaredWebHandlers(output: String): Set<DeclaredWebHandler> {
    data class FilterState(
        val packageName: String,
        val className: String,
        var viewAction: Boolean = false,
        var browsable: Boolean = false,
        val schemes: MutableSet<String> = linkedSetOf(),
        val authorities: MutableSet<String> = linkedSetOf()
    )

    val result = linkedSetOf<DeclaredWebHandler>()
    var packageMarker: String? = null
    var current: FilterState? = null

    fun flush() {
        val filter = current ?: return
        if (filter.viewAction && filter.browsable &&
            filter.schemes.any { it.equals("http", true) || it.equals("https", true) }) {
            filter.authorities.forEach { rawHost ->
                val host = normalizeResolverAuthority(rawHost) ?: return@forEach
                result += DeclaredWebHandler(host, filter.packageName, filter.className)
            }
        }
        current = null
    }

    output.lineSequence().forEach { line ->
        if (line.startsWith("@@LISTCLEANER_PACKAGE@@")) {
            flush()
            packageMarker = line.substringAfter("@@LISTCLEANER_PACKAGE@@").trim()
                .takeIf(APP_LINK_PACKAGE_HEADER::matches)
            return@forEach
        }

        val component = RESOLVER_COMPONENT.matchEntire(line)
        if (component != null) {
            flush()
            val packageName = component.groupValues[1]
            val rawClass = component.groupValues[2]
            val className = if (rawClass.startsWith('.')) packageName + rawClass else rawClass
            if (packageMarker == null || packageMarker == packageName) {
                current = FilterState(packageName, className)
            }
            return@forEach
        }

        val filter = current ?: return@forEach
        val trimmed = line.trim()
        when {
            trimmed == "Action: \"android.intent.action.VIEW\"" -> filter.viewAction = true
            trimmed == "Category: \"android.intent.category.BROWSABLE\"" -> filter.browsable = true
            trimmed.startsWith("Scheme: \"") ->
                QUOTED_VALUE.find(trimmed)?.groupValues?.getOrNull(1)?.let(filter.schemes::add)
            trimmed.startsWith("Authority: \"") ->
                QUOTED_VALUE.find(trimmed)?.groupValues?.getOrNull(1)?.let(filter.authorities::add)
        }
    }
    flush()
    return result
}

private fun normalizeResolverAuthority(value: String): String? {
    val clean = value.trim().removePrefix("*.")
    return normalizeBrowserHost(clean)
}

private fun mergeHostPackages(
    first: Map<String, Set<String>>,
    second: Map<String, Set<String>>
): Map<String, Set<String>> =
    (first.keys + second.keys).associateWith { host ->
        first[host].orEmpty() + second[host].orEmpty()
    }

private val DOMAIN_STATE = Regex("""([A-Za-z0-9.-]+):\s+(\S+)""")
private val APP_LINK_PACKAGE_HEADER = Regex("""[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+""")
private val RESOLVER_COMPONENT =
    Regex("""^\s*[0-9a-fA-F]+\s+([A-Za-z0-9._]+)/(\S+)\s+filter\s+\S+.*$""")
private val QUOTED_VALUE = Regex("""\"([^\"]+)\"""")
