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

    private fun read(command: String): String {
        val result = ComponentRootCommand.runReadOnly(command)
        check(result.exitCode == 0 && !result.timedOut &&
            !result.output.contains("[output truncated]") &&
            !result.output.contains("[output incomplete]")) { "profile_query_failed" }
        return result.output
    }

    @Suppress("DEPRECATION")
    fun packageInfo(packageName: String): PackageInfo? = packages.getOrPut(packageName) {
        if (!PackageIdentity.valid(packageName)) return@getOrPut null
        runCatching {
            val paths = read("pm path --user $userId $packageName").lineSequence()
                .filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.toList()
            val apk = paths.firstOrNull { it.endsWith("/base.apk") } ?: paths.firstOrNull()
                ?: return@runCatching null
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
            context.packageManager.getPackageArchiveInfo(apk, flags)?.also { info ->
                info.applicationInfo?.apply { sourceDir = apk; publicSourceDir = apk }
            }
        }.getOrNull()
    }

    fun query(intent: Intent, flags: Int, command: String = "query-activities"): List<ResolveInfo> {
        require(command in setOf("query-activities", "query-services", "query-receivers", "query-content-providers"))
        val args = buildList {
            intent.action?.let { add("-a " + quote(it)) }
            intent.dataString?.let { add("-d " + quote(it)) }
            intent.type?.let { add("-t " + quote(it)) }
            intent.categories?.forEach { add("-c " + quote(it)) }
            intent.`package`?.let { add("-p " + quote(it)) }
            intent.component?.let { add("-n " + quote(it.flattenToString())) }
        }.joinToString(" ")
        val output = read("cmd package $command --brief --components --query-flags $flags --user $userId $args")
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
