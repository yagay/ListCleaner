package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PackageIdentity
import com.yagay.ListCleaner.domain.SyntheticEntryKeys

/** Read-only, bounded active discovery; never starts a Chooser or sends content. */
internal class ProactiveShortcutDiscovery(private val context: Context) {
    data class Result(
        val candidates: List<ComponentCandidate> = emptyList(),
        val source: String,
        val status: String,
        val queriedPackages: Int = 0,
    )

    fun scan(userId: Int, sharePackages: Collection<String>): Result {
        if (userId !in 0..21474) return Result(source = "NONE", status = "invalid_user")
        val packages = sharePackages.asSequence()
            .filter { PackageIdentity.valid(it) && SAFE_PACKAGE.matches(it) }
            .distinct().sorted().take(MAX_PACKAGES).toList()
        if (packages.isEmpty()) return Result(source = "NONE", status = "no_share_packages")
        val known = packages.toSet()

        // LauncherApps is the public authoritative ShortcutService client, but needs
        // actual Shortcut Host permission. Never pretend to be a launcher to bypass it.
        val launcher = runCatching { context.getSystemService(LauncherApps::class.java) }.getOrNull()
        if (launcher != null && runCatching { launcher.hasShortcutHostPermission() }.getOrDefault(false)) {
            val result = runCatching {
                val user = UserHandle.getUserHandleForUid(userId * 100000)
                val query = LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
                launcher.getShortcuts(query, user).orEmpty()
                    .asSequence().filter { it.`package` in known &&
                        it.isEnabled && (it.isDynamic || it.isDeclaredInManifest) &&
                        !it.categories.isNullOrEmpty() }
                    .take(MAX_CANDIDATES)
                    .mapNotNull { shortcut ->
                        candidate(shortcut.`package`, shortcut.id,
                            shortcut.activity?.className,
                            shortcut.shortLabel?.toString().orEmpty(),
                            "LAUNCHER_APPS", userId)
                    }.toList()
            }
            if (result.isSuccess) return Result(result.getOrThrow(), "LAUNCHER_APPS",
                "candidates_not_final_chooser", packages.size)
        }

        // AOSP ShortcutService implements this read-only shell command. Check for
        // unsupported OEM commands, locked profiles and truncated output explicitly.
        val script = "for p in " + packages.joinToString(" ") +
            "; do printf '\\nLC_SHORTCUT_PACKAGE:%s\\n' \"\$p\"; " +
            "cmd shortcut get-shortcuts --user $userId --flags 9 \"\$p\"; done"
        val result = runCatching { ComponentRootCommand.runReadOnly(script) }
            .getOrElse { return Result(source = "ROOT_SHELL", status = "root_unavailable") }
        if (result.timedOut || result.exitCode != 0 ||
            "[output truncated]" in result.output || "[output incomplete]" in result.output) {
            return Result(source = "ROOT_SHELL", status = "query_failed", queriedPackages = packages.size)
        }
        if ("Unknown command" in result.output || "not running or locked" in result.output ||
            "Error:" in result.output) {
            return Result(source = "ROOT_SHELL", status = "unsupported_or_locked", queriedPackages = packages.size)
        }
        val scanned = ProactiveShortcutParser.parse(result.output, known)
            .mapNotNull { item ->
                val activity = item.activity?.let { raw ->
                    val flattened = if (raw.startsWith("ComponentInfo{"))
                        raw.removePrefix("ComponentInfo{").substringBefore('}') else raw
                    ComponentName.unflattenFromString(flattened)
                        ?.takeIf { it.packageName == item.packageName }?.className
                }
                candidate(item.packageName, item.shortcutId, activity,
                    item.label, "ROOT_SHELL", userId)
            }
        return Result(scanned, "ROOT_SHELL", "candidates_not_final_chooser", packages.size)
    }

    private fun candidate(
        packageName: String, id: String, activity: String?, label: String,
        source: String, userId: Int,
    ): ComponentCandidate? {
        if (!PackageIdentity.valid(packageName) || id.isBlank() || id.length > 300) return null
        val rule = ComponentRule(IntentKind.DIRECT_SHARE, packageName,
            SyntheticEntryKeys.directShareClass(activity, id))
        if (!rule.isValid()) return null
        val application = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()
        val appName = application?.let {
            runCatching { it.loadLabel(context.packageManager).toString() }.getOrNull()
        } ?: packageName
        return ComponentCandidate(
            rule = rule,
            appLabel = appName,
            activityLabel = label.ifBlank { appName },
            evidence = listOf(
                "ACTIVE_SHORTCUT_CANDIDATE source=$source userId=$userId",
                "shareTargetQualification=unverified chooserVisibility=unverified",
                "categories=present; only system Chooser can verify a Direct Share row",
            ),
        )
    }

    private companion object {
        const val MAX_PACKAGES = 48
        const val MAX_CANDIDATES = 512
        val SAFE_PACKAGE = Regex("[A-Za-z][A-Za-z0-9_.]*")
    }
}
