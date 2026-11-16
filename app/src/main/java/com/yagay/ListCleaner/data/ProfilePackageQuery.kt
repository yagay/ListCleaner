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
    private val issues = linkedMapOf<String, Int>()
    private var queryRows = 0
    private var resolvedRows = 0
    private var prefetchRetries = 0

    private fun noteIssue(reason: String) {
        issues[reason] = (issues[reason] ?: 0) + 1
    }

    val failureCount: Int get() = issues.values.sum()
    fun diagnosticSummary(): String =
        "profileUser=$userId rawComponents=$queryRows parsedComponents=$resolvedRows " +
            "prefetchFallbacks=$prefetchRetries failedSources=$failureCount " +
            "reasons=${issues.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "none" }}"

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
        commands.chunked(12).forEach { batch ->
            val output = runCatching {
                read(ProfileShellBatch.script(batch, "LC_QUERY"))
            }.getOrNull()
            batch.forEachIndexed { index, command ->
                val body = output?.let { ProfileShellBatch.entry(it, index, "LC_QUERY") }
                if (body != null) prefetched[command] = body
                else prefetchRetries++ // The direct query will retry and report the real failure.
            }
        }
        prefetchPackagePaths()
    }

    /** Resolve full base and split paths in batches; never use base-only shortcuts. */
    private fun prefetchPackagePaths() {
        prefetchPathsForPackages(prefetched.values.asSequence().flatMap { it.lineSequence() }
            .mapNotNull { ComponentName.unflattenFromString(it.trim())?.packageName }.toList())
    }

    /** Reused by Root Components, which already discovered package names with cmd package. */
    fun prefetchPathsForPackages(packageNames: Collection<String>) {
        val names = packageNames.asSequence().filter(PackageIdentity::valid)
            .distinct().filterNot { it in pathsByPackage }.toList()
        names.chunked(12).forEach { batch ->
            val commands = batch.map { name -> "pm path --user $userId ${quote(name)}" }
            val output = runCatching {
                read(ProfileShellBatch.script(commands, "LC_PATH"))
            }.getOrNull()
            batch.forEachIndexed { index, name ->
                val body = output?.let { ProfileShellBatch.entry(it, index, "LC_PATH") }
                if (body == null) {
                    prefetchRetries++
                    return@forEachIndexed
                }
                val paths = body.lineSequence().map { it.trim() }
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.startsWith("/") }.toList()
                if (paths.isNotEmpty()) pathsByPackage[name] = paths
            }
        }
    }

    @Suppress("DEPRECATION")
    fun packageInfo(packageName: String): PackageInfo? = packages.getOrPut(packageName) {
        if (!PackageIdentity.valid(packageName)) return@getOrPut null
        val parsed = runCatching {
            val cachedPaths = pathsByPackage[packageName].orEmpty()
            val paths = if (cachedPaths.isNotEmpty()) cachedPaths else
                read("pm path --user $userId ${quote(packageName)}").lineSequence()
                    .map { it.trim() }.filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.startsWith("/") }.toList()
            val apk = paths.firstOrNull { it.endsWith("/base.apk") } ?: paths.firstOrNull()
                ?: return@runCatching null
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
            val directory = if (paths.size > 1) java.io.File(apk).parent else null
            val info = directory?.let {
                runCatching { context.packageManager.getPackageArchiveInfo(it, flags) }.getOrNull()
            } ?: context.packageManager.getPackageArchiveInfo(apk, flags)
            info?.also { resolved ->
                resolved.applicationInfo?.apply {
                    sourceDir = apk
                    publicSourceDir = apk
                    splitSourceDirs = paths.filter { it != apk }.toTypedArray()
                    splitPublicSourceDirs = splitSourceDirs
                }
            }
        }.onFailure { noteIssue("package_path_or_archive_exception") }.getOrNull()
        if (parsed == null) noteIssue("package_archive_unavailable")
        parsed
    }

    fun query(intent: Intent, flags: Int, command: String = "query-activities"): List<ResolveInfo> {
        require(command in setOf("query-activities", "query-services", "query-receivers", "query-content-providers"))
        val key = queryCommand(intent, flags, command)
        val output = try {
            prefetched[key] ?: read(key)
        } catch (failure: Exception) {
            noteIssue("query_shell_failed")
            throw failure
        }
        check(ProfileShellBatch.clean(output)) {
            noteIssue("query_shell_error")
            "profile_query_unsupported user=$userId command=$command output=${output.take(180)}"
        }
        return output.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("No activities found") ||
                line.startsWith("No services found") || line.startsWith("No receivers found")
            ) return@mapNotNull null
            val component = ComponentName.unflattenFromString(line)
            if (component == null) {
                noteIssue("unrecognized_component_output")
                return@mapNotNull null
            }
            queryRows++
            if (!PackageIdentity.valid(component.packageName)) {
                noteIssue("invalid_component_package")
                return@mapNotNull null
            }
            val pkg = packageInfo(component.packageName) ?: return@mapNotNull null
            val info = ResolveInfo().apply {
                when (command) {
                    "query-services" -> serviceInfo = pkg.services?.firstOrNull { it.name == component.className }
                    "query-receivers" -> activityInfo = pkg.receivers?.firstOrNull { it.name == component.className }
                    "query-content-providers" -> providerInfo = pkg.providers?.firstOrNull { it.name == component.className }
                    else -> activityInfo = pkg.activities?.firstOrNull { it.name == component.className }
                }
            }
            if (info.activityInfo == null && info.serviceInfo == null && info.providerInfo == null) {
                noteIssue("component_missing_in_archive")
                null
            } else {
                resolvedRows++
                info
            }
        }.toList()
    }

    private fun quote(value: String): String {
        require(value.none { it == '\u0000' || it == '\n' || it == '\r' })
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
