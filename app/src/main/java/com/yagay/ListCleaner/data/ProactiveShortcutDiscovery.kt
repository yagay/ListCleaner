package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import android.content.pm.PackageManager
import android.os.UserHandle
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.PackageIdentity
import com.yagay.ListCleaner.domain.SyntheticEntryKeys

/** Read-only, bounded active discovery; never starts a Chooser or sends content. */
internal class UnifiedShortcutEngine(private val context: Context) {
    /** Published shortcut snapshot; never confuses ShortcutInfo with components. */
    fun scanPublished(): ShortcutDiscoveryResult {
        val manager = context.getSystemService(LauncherApps::class.java)
            ?: return ShortcutDiscoveryResult(ShortcutDiscoveryStatus.SERVICE_UNAVAILABLE)
        if (!runCatching { manager.hasShortcutHostPermission() }.getOrDefault(false)) {
            return ShortcutDiscoveryResult(
                ShortcutDiscoveryStatus.HOST_PERMISSION_REQUIRED,
                detail = "Shortcut host permission is not granted"
            )
        }
        return try {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
            )
            val userId = Process.myUid() / 100000
            val records = manager.getShortcuts(query, Process.myUserHandle()).orEmpty()
                .map { shortcut ->
                    PublishedShortcut(
                        userId = userId,
                        packageName = shortcut.`package`,
                        shortcutId = shortcut.id,
                        label = shortcut.shortLabel?.toString().orEmpty(),
                        enabled = shortcut.isEnabled,
                        manifest = shortcut.isDeclaredInManifest,
                        dynamic = shortcut.isDynamic,
                        pinned = shortcut.isPinned,
                        cached = shortcut.isCached
                    )
                }.distinctBy { Triple(it.userId, it.packageName, it.shortcutId) }
            ShortcutDiscoveryResult(ShortcutDiscoveryStatus.AVAILABLE, records)
        } catch (error: SecurityException) {
            ShortcutDiscoveryResult(
                ShortcutDiscoveryStatus.HOST_PERMISSION_REQUIRED,
                detail = error.javaClass.simpleName
            )
        } catch (error: Exception) {
            Log.w("ShortcutDiscovery", "Read-only shortcut query failed", error)
            ShortcutDiscoveryResult(
                ShortcutDiscoveryStatus.QUERY_FAILED,
                detail = error.javaClass.simpleName
            )
        }
    }


    data class Result(
        val candidates: List<ComponentCandidate> = emptyList(),
        val source: String,
        val status: String,
        val queriedPackages: Int = 0,
    )

    /** One cache/diagnostic contract shared by every shortcut consumer. */
    private data class Cache(
        val at: Long,
        val kinds: Set<IntentKind>,
        val packages: Set<String>,
        val result: Result
    )
    private val cachedCandidates = mutableMapOf<Int, Cache>()

    data class ScanSnapshot(val current: Result, val usable: Result?)

    @Synchronized
    fun scanCached(
        userId: Int,
        sharePackages: Collection<String>,
        kinds: Set<IntentKind>,
        cacheMillis: Long,
        force: Boolean
    ): ScanSnapshot {
        val names = sharePackages.toSet()
        val previous = cachedCandidates[userId]?.takeIf {
            it.kinds == kinds && it.packages == names
        }
        val now = System.currentTimeMillis()
        if (!force && previous != null && now - previous.at in 0..cacheMillis) {
            return ScanSnapshot(previous.result, previous.result)
        }
        val fresh = scan(userId, sharePackages, kinds)
        if (fresh.status == "candidates_not_final_chooser") {
            cachedCandidates[userId] = Cache(now, kinds, names, fresh)
            return ScanSnapshot(fresh, fresh)
        }
        // Failure or host permission denial is not evidence that saved rules vanished.
        return ScanSnapshot(fresh, previous?.result)
    }


    fun scan(
        userId: Int,
        sharePackages: Collection<String>,
        kinds: Set<IntentKind> = setOf(IntentKind.DIRECT_SHARE),
    ): Result {
        val selectedKinds = kinds.intersect(setOf(IntentKind.DIRECT_SHARE, IntentKind.SHORTCUT_ITEM))
        if (selectedKinds.isEmpty()) return Result(source = "NONE", status = "disabled")
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
                    .flatMap { shortcut ->
                        selectedKinds.asSequence().mapNotNull { kind ->
                            candidate(shortcut.`package`, shortcut.id,
                                shortcut.activity?.className,
                                shortcut.shortLabel?.toString().orEmpty(),
                                "LAUNCHER_APPS", userId, kind)
                        }
                    }.take(MAX_CANDIDATES).toList()
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
            .flatMap { item ->
                val activity = item.activity?.let { raw ->
                    val flattened = if (raw.startsWith("ComponentInfo{"))
                        raw.removePrefix("ComponentInfo{").substringBefore('}') else raw
                    ComponentName.unflattenFromString(flattened)
                        ?.takeIf { it.packageName == item.packageName }?.className
                }
                selectedKinds.mapNotNull { kind ->
                    candidate(item.packageName, item.shortcutId, activity,
                        item.label, "ROOT_SHELL", userId, kind)
                }
            }.take(MAX_CANDIDATES)
        return Result(scanned, "ROOT_SHELL", "candidates_not_final_chooser", packages.size)
    }

    private fun candidate(
        packageName: String, id: String, activity: String?, label: String,
        source: String, userId: Int, kind: IntentKind,
    ): ComponentCandidate? {
        if (!PackageIdentity.valid(packageName) || id.isBlank() || id.length > 300) return null
        val identity = when (kind) {
            IntentKind.DIRECT_SHARE -> SyntheticEntryKeys.directShareClass(activity, id)
            IntentKind.SHORTCUT_ITEM -> SyntheticEntryKeys.shortcutItemClass(activity, id)
            else -> return null
        }
        val rule = ComponentRule(kind, packageName, identity)
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

    companion object {
        @Volatile private var shared: UnifiedShortcutEngine? = null
        fun get(context: Context): UnifiedShortcutEngine =
            shared ?: synchronized(this) {
                shared ?: UnifiedShortcutEngine(context.applicationContext).also { shared = it }
            }

        const val MAX_PACKAGES = 48
        const val MAX_CANDIDATES = 512
        val SAFE_PACKAGE = Regex("[A-Za-z][A-Za-z0-9_.]*")
    }
}
