# 列表清理 / List Cleaner 1.6.34

版本码 / Version code: 59

## 中文

- 高级系统入口改为“系统最终权威列表优先”：PackageManager、Manifest 和 AppOps 的候选只作为发现证据，未经最终系统列表确认的条目不再混入正常列表。
- VPN 改为 Hook Android Settings 的最终 `VpnSettings.getVpnApps()` 列表，不再把原始 AppOps 结果当作最终 VPN 列表；同时只 Hook 最终重载，避免嵌套重载覆盖原始快照。
- `SystemManagerEntryFilterModule` 现在始终先观察 Accessibility、输入法、打印、Credential 等系统管理器的原始结果，再判断是否允许过滤；系统调用被安全策略放行时也不会丢失真实候选证据。
- Assistant、Home、Browser、Call Screening 的 RoleController 原始资格包列表会回传到管理器并作为 package-level 权威快照，减少管理端自行重建资格列表带来的差异。
- Android 16 Combined Provider 的 Autofill / Credential 列表改为整类权威快照，同时兼容旧 `DefaultAutofillPicker` 的组件列表，用同一包级规则过滤。
- NFC Payment 的 `CATEGORY_PAYMENT` 列表改为整类快照，已从系统支付列表消失的服务不会继续作为当前候选保留。
- observed-entry 本地缓存新增“权威快照替换”语义：Role、VPN、Autofill、Credential、NFC 的远端删除会同步清除本地旧条目；Shortcut / Direct Share 仍保留增量历史。
- Runtime Audit 不再把单纯 `MODULE_LOADED` 当作 Hook 已就绪；现在区分模块加载、Hook 安装、权威查询和实际过滤，并识别新的 Settings authority 路径。
- 发布流程文档与当前 Actions 对齐：正式 Release 只允许手动启动；清理旧 Telegram push 标记。
- 本次修改改变 Xposed/Settings/Role/NFC 运行时合同，Hook compatibility 提升到 59。

---

## English

- Advanced system-entry discovery now prefers each Android subsystem's final authoritative list. PackageManager, manifest, and AppOps results remain discovery evidence but no longer enter the normal list without final-authority confirmation.
- VPN filtering now hooks Android Settings' final `VpnSettings.getVpnApps()` result instead of treating raw AppOps packages as the final VPN list. Only the terminal overload is hooked so nested overloads cannot overwrite the pristine authority snapshot with an already filtered result.
- `SystemManagerEntryFilterModule` now observes the original Accessibility, input-method, print, and Credential manager results before deciding whether a caller may be filtered, preserving real candidate evidence even when system calls are intentionally left untouched.
- Assistant, Home, Browser, and Call Screening now mirror RoleController's unfiltered qualifying-package lists back to the manager as package-level authority snapshots instead of relying only on manager-side reconstruction.
- Android 16 Combined Provider Autofill/Credential results are stored as complete authority snapshots, while the legacy `DefaultAutofillPicker` component list remains compatible with the same package-level rules.
- NFC Payment `CATEGORY_PAYMENT` observations are now complete snapshots so services removed from the real payment list no longer remain current candidates.
- The manager's observed-entry cache now reconciles complete remote authority snapshots: removed Role, VPN, Autofill, Credential, and NFC entries are deleted locally, while Shortcut and Direct Share keep incremental history.
- Runtime Audit no longer treats `MODULE_LOADED` alone as proof that a hook is ready. Module load, hook installation, authority queries, and actual filtering are tracked separately, including the new Settings authority paths.
- Release documentation now matches the current Actions policy: official Releases are manual-only, and the obsolete Telegram push marker was removed.
- This release changes Xposed/Settings/Role/NFC runtime behavior, so hook compatibility is bumped to 59.
