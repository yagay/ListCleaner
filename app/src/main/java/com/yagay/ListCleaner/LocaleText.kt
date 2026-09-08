package com.yagay.ListCleaner

/** Locale helper for runtime-generated messages that contain dynamic values. */
internal object LocaleText {
    fun pick(chinese: String, english: String): String = AppLanguage.pick(chinese, english)

    fun moduleMessage(text: String?): String? {
        if (text == null) return null
        return AppLanguage.translate(text, listOf(
            "等待核实运行模块与配置" to "Waiting to verify the running module and configuration",
            "等待连接" to "Waiting for connection",
            "连接已断开；暂停扫描，保留当前列表" to "Connection lost; scanning paused and the current list is retained",
            "未连接 LSPosed；暂停扫描，保留当前列表" to "LSPosed is not connected; scanning is paused and the current list is retained",
            "远程配置过大" to "Remote configuration is too large",
            "远程旧规则损坏，请先从 JSON 备份恢复" to "Legacy remote rules are corrupted; restore from a JSON backup first",
            "本地配置缺失，远程配置仍存在（" to "Local configuration is missing while remote configuration still exists (",
            " 条规则）。请先选择恢复或重置；尚未覆盖远程配置。" to " rules). Choose restore or reset first; the remote configuration has not been overwritten.",
            "远程配置无法读取或校验（" to "Remote configuration could not be read or validated (",
            "）。未覆盖原配置；可导入备份，或确认重置。" to "). The original configuration was not overwritten; import a backup or confirm reset.",
            "框架需要 API 102；暂停同步和扫描" to "Framework API 102 is required; synchronization and scanning are paused",
            "配置超过传输上限，请减少规则后重试；未写入远程" to "Configuration exceeds the transfer limit. Reduce the number of rules and try again; nothing was written remotely",
            "暂停配置写入失败" to "Failed to write the pause configuration",
            "暂停配置已提交，尚未确认所有运行目标已应用" to "Pause configuration submitted; not all running targets have confirmed it yet",
            "旧模块或异常运行状态：" to "Old module or abnormal runtime state: ",
            "；请尝试热更新，旧版本不支持时完整重启手机。" to "; try hot update, or fully restart the phone if the old version does not support it.",
            "已提交兼容的暂停配置，但未确认生效；扫描仍暂停。" to "A compatible pause configuration was submitted but not confirmed; scanning remains paused.",
            "暂停同步和扫描。" to "Synchronization and scanning are paused.",
            "未检测到 system 中的模块；请检查作用域并重启，暂不扫描" to "The module was not detected in system; check the scope and restart. Scanning is paused for now",
            "本地配置已保存，等待系统 Hook 确认新配置" to "Local configuration saved; waiting for the system Hook to confirm the new configuration",
            "远程配置写入失败" to "Failed to write the remote configuration",
            "连接已变化，请重试" to "The connection changed; try again",
            "配置已写入，但系统 Hook 未确认接收；暂停扫描，请重试或重启" to "Configuration was written, but the system Hook did not acknowledge it. Scanning is paused; try again or restart",
            "配置在确认期间发生变化，正在重新同步" to "Configuration changed during acknowledgement; synchronizing again",
            "系统 Hook 已确认配置；可在状态页查看过滤、应用可见性和排序的实际命中次数" to "The system Hook confirmed the configuration. View actual filtering, app-visibility, and ordering hit counts on the status page",
            "运行状态验证失败，保留当前列表" to "Runtime verification failed; the current list is retained",
            "已恢复本地配置，等待系统确认" to "Local configuration restored; waiting for system confirmation",
            "本地已重置并选择暂停，等待系统确认" to "Local configuration reset and pause selected; waiting for system confirmation"
        ))
    }

    fun rootMessage(text: String?): String? {
        if (text == null) return null
        return AppLanguage.translate(text, listOf(
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
        ))
    }
}
