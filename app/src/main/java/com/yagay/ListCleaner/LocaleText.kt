package com.yagay.ListCleaner

import java.util.Locale

/** Locale helper for runtime-generated messages that contain dynamic values. */
internal object LocaleText {
    private fun isChinese(): Boolean = Locale.getDefault().language.equals("zh", ignoreCase = true)

    fun pick(chinese: String, english: String): String = if (isChinese()) chinese else english

    /**
     * Runtime Root/component messages often contain component names, counters, exit codes, or exception text,
     * so they cannot be covered reliably by exact UI string lookup alone.
     */
    fun rootMessage(text: String?): String? {
        if (text == null || isChinese()) return text
        var result = text
        val replacements = listOf(
            "正在请求 Root 并核验系统状态…" to "Requesting Root permission and verifying system state…",
            "正在请求 Root 并反选组件…" to "Requesting Root permission and inverting components…",
            "用户身份已变化，请重新扫描" to "The user profile changed; scan again",
            "组件已消失或不再属于此分类，请刷新" to "The component disappeared or no longer belongs to this category; refresh",
            "组件状态已被其他操作改变，请刷新后重试" to "The component state changed elsewhere; refresh and try again",
            "系统已处于目标状态，未执行命令" to "The system is already in the requested state; no command was run",
            "Root 命令未完成：" to "Root command did not complete: ",
            "；请刷新核对实际状态" to "; refresh and verify the actual state",
            "操作未确认成功（退出码 " to "The operation could not be confirmed (exit code ",
            "，系统状态 " to ", system state ",
            "）。请核对 Root 授权并刷新；不会自动重试。" to "). Check Root permission and refresh; it will not retry automatically.",
            "已核验：组件已启用；不保证恢复原磁贴/小部件位置" to "Verified: component enabled; original tile/widget placement is not guaranteed to be restored",
            "已核验：组件已禁用；请重新打开目标选择器" to "Verified: component disabled; reopen the target resolver",
            "已核验：" to "Verified: ",
            " 个组件已启用；请重新打开目标选择器" to " components enabled; reopen the target resolver",
            " 个组件已禁用；请重新打开目标选择器" to " components disabled; reopen the target resolver",
            " 个组件已反选；请重新打开目标选择器" to " components inverted; reopen the target resolver",
            "正在反选 " to "Inverting ",
            "正在启用 " to "Enabling ",
            "正在禁用 " to "Disabling ",
            "已完成 " to "Completed ",
            "，操作已停止：" to "; operation stopped: ",
            "，反选已停止：" to "; inversion stopped: ",
            "。失败项请核对系统状态；剩余项未执行，已完成项不回滚。" to ". Verify the failed item in system state. Remaining items were not run, and completed changes are not rolled back.",
            "。已完成项不回滚。" to ". Completed changes are not rolled back.",
            "操作后扫描失败，请刷新；不使用旧状态" to "Post-operation scan failed; refresh. Stale state will not be used",
            "扫描失败" to "Scan failed",
            "磁贴扫描失败：" to "Tile scan failed: ",
            "快捷方式扫描失败：" to "Shortcut scan failed: ",
            "小部件扫描失败：" to "Widget scan failed: ",
            "组件标识不受支持" to "Unsupported component identifier",
            "核心系统/管理组件，仅展示" to "Core system/management component; display only",
            "状态读取失败，请刷新" to "Failed to read state; refresh and try again",
            "所属应用已停用；本功能不会启用整个应用" to "The owning app is disabled; this feature will not enable the entire app",
            "无法操作" to "Operation not allowed",
            "未知" to "Unknown",
            "操作失败" to "Operation failed"
        )
        for ((source, target) in replacements.sortedByDescending { it.first.length }) {
            result = result.replace(source, target)
        }
        return result
    }
}
