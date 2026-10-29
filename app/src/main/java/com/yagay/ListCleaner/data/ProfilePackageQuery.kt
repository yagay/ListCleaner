package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.yagay.ListCleaner.domain.PackageIdentity

/** Target-user shell resolution plus immutable APK metadata; never borrows another user's overrides. */
internal class ProfilePackageQuery(private val context: Context, val userId: Int) {
    init { require(userId in 0..21474) }
    private val packages = mutableMapOf<String, PackageInfo?>()
    private val pathsByPackage = mutableMapOf<String, List<String>>()

    private fun read(command: String): String {
        val result = ComponentRootCommand.runReadOnly(command)
        check(result.exitCode == 0 && !result.timedOut &&
            !result.output.contains("[output truncated]") &&
            !result.output.contains("[output incomplete]")) { "profile_query_failed exit=${result.exitCode} timeout=${result.timedOut} output=${result.output.take(150)}" }
        return result.output
    }

    private val prefetched = mutableMapOf<String, String>()

    private fun queryCommand(intent: Intent, flags: Int, command: String): String {
        val args = buildList {
            intent.action?.let { add("-a " + quote(it)) }
            intent.dataString?.let { add("-d " + quote(it)) }
            intent.type?.let { add("-t " + quote(it)) }
            intent.categories?.forEach { add("-c " + quote(it)) }
            intent.`package`?.let { add("-p " + quote(it)) }
            intent.component?.let { add("-n " + quote(it.flattenToString())) }
        }.joinToString(" ")
        return "cmd package $command --brief --components --query-flags $flags --user $userId $args"
    }

    /** Batch independent probes in one root invocation, falling back per probe on failure. */
    fun prefetch(queries: List<Pair<Intent, Int>>) = prefetchCommands(
        queries.map { Triple(it.first, it.second, "query-activities") }
    )

    fun prefetchCommands(queries: List<Triple<Intent, Int, String>>) {
        val commands = queries.map { queryCommand(it.first, it.second, it.third) }
            .distinct().filterNot { it in prefetched }
        if (commands.isEmpty()) return
        // Limit batch size to keep RootCommand's bounded output and timeout predictable.
        commands.chunked(12).forEach { batch ->
            val script = buildString {
                batch.forEachIndexed { index, command ->
                    append("printf '\\n__LC_QUERY_START_${index}__\\n'; ")
                    append(command)
                    append("; printf '\\n__LC_QUERY_END_${index}__\\n'; ")
                }
            }
            val output = runCatching { read(script) }.getOrNull() ?: return@forEach
            batch.forEachIndexed { index, command ->
                val start = "__LC_QUERY_START_${index}__"
                val end = "__LC_QUERY_END_${index}__"
                val body = output.substringAfter(start, "").substringBefore(end, "")
                if (start in output && end in output && !body.contains("Error:") &&
                    !body.contains("Exception") && !body.contains("Unknown command")) {
                    prefetched[command] = body
                }
            }
        }
        prefetchPackagePaths()
    }

    /** Resolve full base and split paths in batches; never use base-only shortcuts. */
    private fun prefetchPackagePaths() {
        val names = prefetched.values.asSequence().flatMap { it.lineSequence() }
            .mapNotNull { ComponentName.unflattenFromString(it.trim())?.packageName }
            .filter(PackageIdentity::valid).distinct().filterNot { it in pathsByPackage }.toList()
        names.chunked(12).forEach { batch ->
            val script = buildString {
                batch.forEachIndexed { index, name ->
                    append("printf '\\n__LC_PATH_START_${index}__\\n'; ")
                    append("pm path --user $userId ${quote(name)}")
                    append("; printf '\\n__LC_PATH_END_${index}__\\n'; ")
                }
            }
            val output = runCatching { read(script) }.getOrNull() ?: return@forEach
            batch.forEachIndexed { index, name ->
                val start = "__LC_PATH_START_${index}__"
                val end = "__LC_PATH_END_${index}__"
                if (start !in output || end !in output) return@forEachIndexed
                val paths = output.substringAfter(start).substringBefore(end)
                    .lineSequence().filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.filter { it.startsWith("/") }.toList()
                if (paths.isNotEmpty()) pathsByPackage[name] = paths
            }
        }
    }
    @Suppress("DEPRECATION")
    fun packageInfo(packageName: String): PackageInfo? = packages.getOrPut(packageName) {
        if (!PackageIdentity.valid(packageName)) return@getOrPut null
        runCatching {
            val cachedPaths = pathsByPackage[packageName].orEmpty()
            val paths = if (cachedPaths.isNotEmpty()) cachedPaths else
                read("pm path --user $userId ${quote(packageName)}").lineSequence()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }.toList()
            val apk = paths.firstOrNull { it.endsWith("/base.apk") } ?: paths.firstOrNull()
                ?: return@runCatching null
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
            // Parsing only base.apk drops components declared by installed feature splits.
            // Android's package parser accepts an APK cluster directory on supported builds;
            // retain the base-APK fallback on OEM versions that do not expose cluster parsing.
            val archivePath = if (paths.size > 1) java.io.File(apk).parent else null
            val parsed = archivePath?.let { directory ->
                runCatching { context.packageManager.getPackageArchiveInfo(directory, flags) }.getOrNull()
            } ?: context.packageManager.getPackageArchiveInfo(apk, flags)
            parsed?.also { info ->
                info.applicationInfo?.apply {
                    sourceDir = apk
                    publicSourceDir = apk
                    splitSourceDirs = paths.filter { it != apk }.toTypedArray()
                    splitPublicSourceDirs = splitSourceDirs
                }
            }
        }.getOrNull()
    }

    fun query(intent: Intent, flags: Int, command: String = "query-activities"): List<ResolveInfo> {
        require(command in setOf("query-activities", "query-services", "query-receivers", "query-content-providers"))
        val key = queryCommand(intent, flags, command)
        val output = prefetched[key] ?: read(key)
        check(!output.contains("Unknown command") && !output.contains("Error:") &&
            !output.contains("Exception")) { "profile_query_unsupported" }
        return output.lineSequence().mapNotNull { line ->
            val component = ComponentName.unflattenFromString(line.trim()) ?: return@mapNotNull null
            if (!PackageIdentity.valid(component.packageName)) return@mapNotNull null
            val pkg = packageInfo(component.packageName) ?: return@mapNotNull null
            ResolveInfo().apply {
                when (command) {
                    "query-services" -> serviceInfo = pkg.services?.firstOrNull { it.name == component.className }
                    "query-receivers" -> activityInfo = pkg.receivers?.firstOrNull { it.name == component.className }
                    "query-content-providers" -> providerInfo = pkg.providers?.firstOrNull { it.name == component.className }
                    else -> activityInfo = pkg.activities?.firstOrNull { it.name == component.className }
                }
            }.takeIf { it.activityInfo != null || it.serviceInfo != null || it.providerInfo != null }
        }.toList()
    }

    private fun quote(value: String): String {
        require(value.none { it == '\u0000' || it == '\n' || it == '\r' })
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
