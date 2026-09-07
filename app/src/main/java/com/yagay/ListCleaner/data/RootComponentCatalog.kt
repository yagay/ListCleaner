package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.domain.ComponentStatePolicy
import kotlinx.coroutines.delay

enum class CleanupKind(val title: String, val action: String) {
    TILE("磁贴", "android.service.quicksettings.action.QS_TILE"),
    SHORTCUT("快捷方式", "android.intent.action.CREATE_SHORTCUT"),
    WIDGET("小部件", "android.appwidget.action.APPWIDGET_UPDATE")
}

data class RootComponent(
    val kind: CleanupKind, val component: ComponentName, val user: Int,
    val label: String, val owner: String, val icon: Bitmap?,
    val overrideState: Int?, val enabled: Boolean?, val applicationEnabled: Boolean?,
    val blocked: String? = null,
    val modifiedByListCleaner: Boolean = false
) {
    val id: String get() = "$user|${kind.name}|${component.flattenToString()}"
}

data class RootComponentScan(
    val items: List<RootComponent> = emptyList(),
    val warning: String = "尚未扫描",
    val observedAt: Long = 0
)

/** Only reads candidate metadata/settings. Root is requested only for explicit state changes. */
class RootComponentCatalog(private val context: Context) {
    private val pm = context.packageManager
    private val user = android.os.Process.myUid() / 100_000
    private val icons = android.util.LruCache<String, Bitmap>(128)
    private val historyPrefs = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
    private val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or PackageManager.MATCH_ALL or
        PackageManager.GET_META_DATA
    @Volatile var lastOperation = "No component operation"
        private set

    fun requireRoot() {
        lastOperation = "at=${System.currentTimeMillis()} status=checking_root"
        try {
            ComponentRootCommand.requireRoot()
            lastOperation = "at=${System.currentTimeMillis()} status=root_granted"
        } catch (failure: ComponentRootCommand.RootAccessException) {
            lastOperation = "at=${System.currentTimeMillis()} status=root_unavailable message=${failure.message}"
            throw failure
        }
    }

    fun clearModificationHistory() {
        historyPrefs.edit().remove(KEY_MODIFIED_IDS).apply()
    }

    private fun modifiedIds(): Set<String> = historyPrefs.getStringSet(KEY_MODIFIED_IDS, emptySet()).orEmpty().toSet()

    private fun markModified(id: String) {
        val next = modifiedIds().toMutableSet().apply {
            add(id)
            if (size > MAX_HISTORY) {
                // Component ids are not time ordered in SharedPreferences. Keep a bounded deterministic subset.
                val trimmed = sorted().takeLast(MAX_HISTORY).toSet()
                clear(); addAll(trimmed)
            }
        }
        historyPrefs.edit().putStringSet(KEY_MODIFIED_IDS, next).apply()
    }

    @Suppress("DEPRECATION")
    private fun query(kind: CleanupKind): List<ComponentInfo> = when (kind) {
        CleanupKind.TILE -> pm.queryIntentServices(Intent(kind.action), flags).mapNotNull { it.serviceInfo }
            .filter { it.permission == "android.permission.BIND_QUICK_SETTINGS_TILE" && it.exported }
        CleanupKind.SHORTCUT -> pm.queryIntentActivities(Intent(kind.action), flags).mapNotNull { it.activityInfo }
            .filter { it.exported }
        CleanupKind.WIDGET -> pm.queryBroadcastReceivers(Intent(kind.action), flags).mapNotNull { it.activityInfo }
            .filter { (it.metaData?.getInt("android.appwidget.provider", 0) ?: 0) != 0 }
    }

    @Suppress("DEPRECATION")
    fun scan(): RootComponentScan {
        icons.evictAll()
        val errors = mutableListOf<String>()
        val modified = modifiedIds()
        val items = CleanupKind.entries.flatMap { kind ->
            try {
                query(kind).distinctBy { ComponentName(it.packageName, it.name) }.map { info ->
                    read(kind, info, modified)
                }
            } catch (failure: Exception) {
                errors += "${kind.title}扫描失败：${failure.javaClass.simpleName}"
                emptyList()
            }
        }.sortedWith(compareBy({ it.owner.lowercase() }, { it.label.lowercase() }, { it.id }))
        return RootComponentScan(items, errors.joinToString("\n"), System.currentTimeMillis())
    }

    private fun read(kind: CleanupKind, info: ComponentInfo, modified: Set<String> = modifiedIds()): RootComponent {
        val component = ComponentName(info.packageName, info.name)
        val raw = runCatching { pm.getComponentEnabledSetting(component) }.getOrNull()
        val enabled = raw?.let { ComponentStatePolicy.enabled(it, info.enabled) }
        val appEnabled = runCatching {
            ComponentStatePolicy.enabled(pm.getApplicationEnabledSetting(info.packageName), info.applicationInfo.enabled)
        }.getOrNull()
        val blocked = when {
            !ComponentStatePolicy.valid(component.packageName, component.className, user) -> "组件标识不受支持"
            info.packageName in setOf(context.packageName, "android", "com.android.systemui") ||
                info.applicationInfo.uid % 100_000 < 10_000 -> "核心系统/管理组件，仅展示"
            raw == null || enabled == null || appEnabled == null -> "状态读取失败，请刷新"
            !appEnabled -> "所属应用已停用；本功能不会启用整个应用"
            else -> null
        }
        val id = "$user|${kind.name}|${component.flattenToString()}"
        return RootComponent(
            kind, component, user,
            runCatching { info.loadLabel(pm).toString() }.getOrDefault(component.shortClassName),
            runCatching { info.applicationInfo.loadLabel(pm).toString() }.getOrDefault(info.packageName),
            icons.get(info.packageName) ?: runCatching { info.applicationInfo.loadIcon(pm).toBitmap(96, 96) }
                .getOrNull()?.also { icons.put(info.packageName, it) },
            raw, enabled, appEnabled, blocked, id in modified
        )
    }

    /** Re-discover before mutation: no arbitrary component strings from UI/imports/root output. */
    suspend fun change(target: RootComponent, enable: Boolean): String {
        require(target.user == user) { "用户身份已变化，请重新扫描" }
        val info = query(target.kind).firstOrNull {
            ComponentName(it.packageName, it.name) == target.component
        } ?: error("组件已消失或不再属于此分类，请刷新")
        val fresh = read(target.kind, info)
        check(fresh.blocked == null) { fresh.blocked ?: "无法操作" }
        check(fresh.overrideState == target.overrideState && fresh.enabled == target.enabled) {
            "组件状态已被其他操作改变，请刷新后重试"
        }
        if (fresh.enabled == enable) return "系统已处于目标状态，未执行命令"
        val script = ComponentStatePolicy.command(target.component.packageName, target.component.className, user, enable)
        lastOperation = "at=${System.currentTimeMillis()} component=${target.id} requestedEnabled=$enable status=started"
        val result = try {
            ComponentRootCommand.run(script)
        } catch (failure: Exception) {
            lastOperation += " error=${failure.javaClass.name}"
            throw IllegalStateException("Root 命令未完成：${failure.javaClass.simpleName}；请刷新核对实际状态", failure)
        }
        val expected = if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        var observed: Int? = null
        repeat(5) {
            if (observed != expected) {
                observed = runCatching { pm.getComponentEnabledSetting(target.component) }.getOrNull()
                if (observed != expected) delay(100)
            }
        }
        lastOperation = "at=${System.currentTimeMillis()} component=${target.id} requestedEnabled=$enable " +
            "exit=${result.exitCode} timeout=${result.timedOut} observed=$observed\n${result.output}"
        android.util.Log.i("ListCleaner", "COMPONENT_STATE ${target.id} exit=${result.exitCode} observed=$observed")
        check(!result.timedOut && result.exitCode == 0 && observed == expected) {
            "操作未确认成功（退出码 ${result.exitCode}，系统状态 ${observed ?: "未知"}）。请核对 Root 授权并刷新；不会自动重试。"
        }
        markModified(target.id)
        return if (enable) "已核验：组件已启用；不保证恢复原磁贴/小部件位置" else "已核验：组件已禁用；请重新打开目标选择器"
    }

    private companion object {
        const val HISTORY_PREFS = "root_component_history"
        const val KEY_MODIFIED_IDS = "modified_ids"
        const val MAX_HISTORY = 4_000
    }
}
