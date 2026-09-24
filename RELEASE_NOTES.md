# 列表清理 / List Cleaner 1.6.18

版本码 / Version code: 43

## 中文

- Root 磁贴、快捷方式和小组件的持久保护状态现在与主规则一起进入 Runtime Probe 原子配置；`ComponentStateGuardModule` 与 `ComponentDiscoveryFilterModule` 优先使用同一份 system_server 进程内权威快照，不再依赖 RemotePreferences 是否及时刷新。
- Runtime Probe 的 BEGIN、CHUNK、COMMIT 全部先验证管理器 UID；非管理器调用不能中断正在进行的配置分块传输。
- Probe v2 成功后停止在 PackageManager / Resolver 查询热路径轮询 RemotePreferences，减少查询时的配置读取和 SHA-256 计算。
- Root 组件批量操作完成后只进行一次运行时同步，同时 digest 会包含 Root 保护集合，因此即使普通规则 revision 未变化也能正确更新 Hook。
- App Link 自动发现现在会缓存“成功但为空”的扫描结果，并对失败增加短时间退避，避免每次刷新重复启动 root shell 和 `dumpsys package`。
- 修正 `ComponentReconcileJobService` 的取消与替换 Job 生命周期，避免旧 Job 在被停止后错误结束新的 Job。
- 收紧组件重协调广播接收器为非导出。
- 修正 GitHub Actions：Release PR 校验现在真正有 `pull_request` 触发器；Debug PR 也会在 Gradle/wrapper 配置变化时编译。
- 重构高风险大文件：候选分组与历史候选逻辑移出 `MainViewModel`，优先级编辑移入独立 `PriorityEditorController`，Xposed 运行时快照与组件策略也拆成独立模型。
- 规则页和优先级页不再使用 `openPreset!!` / `browserHost!!`，改为基于当前分类捕获稳定作用域值，降低状态切换时的空指针风险。
- 删除已无运行时用途的 `TileConfig` / `TilePolicy` 和 forced-default transient 配置外壳；旧备份/配置中的这些字段仍会作为未知字段安全忽略。
- 本次修改涉及 Xposed 运行时合同，Hook compatibility 提升到 43。

---

## English

- Root tile, shortcut, and widget protection is now carried in the same atomic Runtime Probe configuration as resolver rules. `ComponentStateGuardModule` and `ComponentDiscoveryFilterModule` prefer the shared authoritative in-process system_server snapshot instead of depending on RemotePreferences cache freshness.
- Runtime Probe now verifies the manager UID before BEGIN, CHUNK, and COMMIT operations, preventing non-manager callers from disrupting an in-flight chunk transfer.
- After Probe v2 becomes authoritative, PackageManager/Resolver hot paths stop polling RemotePreferences, reducing configuration reads and SHA-256 work during resolver queries.
- Root component batches perform one runtime synchronization after mutation, and the runtime digest includes the protected component set so hook state updates even when the normal rule revision is unchanged.
- App Link discovery now caches successful empty scans and backs off briefly after failures, avoiding repeated root shell and `dumpsys package` work on every refresh.
- Fixed `ComponentReconcileJobService` cancellation/replacement lifecycle so a stopped stale job cannot finish or remove its replacement.
- Restricted the reconcile broadcast receiver from external export.
- Fixed GitHub Actions so release validation actually runs for pull requests and debug PR builds also cover Gradle/wrapper changes.
- Split high-risk large files: candidate grouping/history logic moved out of `MainViewModel`, priority editing moved into `PriorityEditorController`, and Xposed runtime snapshots/component policy now live in dedicated models.
- Removed `openPreset!!` / `browserHost!!` assertions from the rules and priority Compose screens by capturing stable scope values for the current category.
- Removed obsolete `TileConfig` / `TilePolicy` and forced-default transient compatibility shells; legacy JSON fields remain safely ignored as unknown fields.
- This release changes Xposed runtime behavior, so hook compatibility is bumped to 43.
