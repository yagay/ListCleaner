package com.yagay.ListCleaner.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.yagay.ListCleaner.LocaleText
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
    val blocked: String? = null
) {
    val id: String get() = "$user|${kind.name}|${component.flattenToString()}"
}

data class RootComponentScan(
    val items: List<RootComponent> = emptyList(),
    val warning: String = LocaleText.pick("尚未扫描", "Not scanned yet"),
    val observedAt: Long = 0
)

/** Only reads candidate metadata/settings. Root is requested only for explicit state changes. */
class RootComponentCatalog(private val context: Context) {
    private val pm = context.packageManager
    private val user = android.os.Process.myUid() / 100_000
    private val icons = android.util.LruCache<String, Bitmap>(128)
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
        val items = CleanupKind.entries.flatMap { kind ->
            try {
                query(kind).distinctBy { ComponentName(it.packageName, it.name) }.map { info ->
                    read(kind, info)
                }
            } catch (failure: Exception) {
                errors += LocaleText.rootMessage("${kind.title}扫描失败：${failure.javaClass.simpleName}").orEmpty()
                emptyList()
            }
        }.sortedWith(compareBy({ it.owner.lowercase() }, { it.label.lowercase() }, { it.id }))
        return RootComponentScan(items, errors.joinToString("\n"), System.currentTimeMillis())
    }

    private fun read(kind: CleanupKind, info: ComponentInfo): RootComponent {
        val component = ComponentName(info.packageName, info.name)
        val raw = runCatching { pm.getComponentEnabledSetting(component) }.getOrNull()
        val enabled = raw?.let { ComponentStatePolicy.enabled(it, info.enabled) }
        val appEnabled = runCatching {
            ComponentStatePolicy.enabled(pm.getApplicationEnabledSetting(info.packageName), info.applicationInfo.enabled)
        }.getOrNull()
        val blocked = when {
            !ComponentStatePolicy.valid(component.packageName, component.className, user) -> LocaleText.pick("组件标识不受支持", "Unsupported component identifier")
            info.packageName in setOf(context.packageName, "android", "com.android.systemui") ||
                info.applicationInfo.uid % 100_000 < 10_000 -> LocaleText.pick("核心系统/管理组件，仅展示", "Core system/management component; display only")
            raw == null || enabled == null || appEnabled == null -> LocaleText.pick("状态读取失败，请刷新", "Failed to read state; refresh and try again")
            !appEnabled -> LocaleText.pick("所属应用已停用；本功能不会启用整个应用", "The owning app is disabled; this feature will not enable the entire app")
            else -> null
        }
        return RootComponent(
            kind, component, user,
            runCatching { info.loadLabel(pm).toString() }.getOrDefault(component.shortClassName),
            runCatching { info.applicationInfo.loadLabel(pm).toString() }.getOrDefault(info.packageName),
            icons.get(info.packageName) ?: runCatching { info.applicationInfo.loadIcon(pm).toBitmap(96, 96) }
                .getOrNull()?.also { icons.put(info.packageName, it) },
            raw, enabled, appEnabled, blocked
        )
    }

    /** Re-discover before mutation: no arbitrary component strings from UI/imports/root output. */
    suspend fun change(target: RootComponent, enable: Boolean): String {
        require(target.user == user) { LocaleText.pick("用户身份已变化，请重新扫描", "The user profile changed; scan again") }
        val info = query(target.kind).firstOrNull {
            ComponentName(it.packageName, it.name) == target.component
        } ?: error(LocaleText.pick("组件已消失或不再属于此分类，请刷新", "The component disappeared or no longer belongs to this category; refresh"))
        val fresh = read(target.kind, info)
        check(fresh.blocked == null) { fresh.blocked ?: LocaleText.pick("无法操作", "Operation not allowed") }
        check(fresh.overrideState == target.overrideState && fresh.enabled == target.enabled) {
            LocaleText.pick("组件状态已被其他操作改变，请刷新后重试", "The component state changed elsewhere; refresh and try again")
        }
        if (fresh.enabled == enable) return LocaleText.pick("系统已处于目标状态，未执行命令", "The system is already in the requested state; no command was run")
        val script = ComponentStatePolicy.command(target.component.packageName, target.component.className, user, enable)
        lastOperation = "at=${System.currentTimeMillis()} component=${target.id} requestedEnabled=$enable status=started"
        val result = try {
            ComponentRootCommand.run(script)
        } catch (failure: Exception) {
            lastOperation += " error=${failure.javaClass.name}"
            throw IllegalStateException(
                LocaleText.pick(
                    "Root 命令未完成：${failure.javaClass.simpleName}；请刷新核对实际状态",
                    "Root command did not complete: ${failure.javaClass.simpleName}; refresh and verify the actual state"
                ),
                failure
            )
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
            LocaleText.pick(
                "操作未确认成功（退出码 ${result.exitCode}，系统状态 ${observed ?: "未知"}）。请核对 Root 授权并刷新；不会自动重试。",
                "The operation could not be confirmed (exit code ${result.exitCode}, system state ${observed ?: "unknown"}). Check Root permission and refresh; it will not retry automatically."
            )
        }
        return if (enable) {
            LocaleText.pick("已核验：组件已启用；不保证恢复原磁贴/小部件位置", "Verified: component enabled; original tile/widget placement is not guaranteed to be restored")
        } else {
            LocaleText.pick("已核验：组件已禁用；请重新打开目标选择器", "Verified: component disabled; reopen the target resolver")
        }
    }
}
