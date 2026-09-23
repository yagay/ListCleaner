# 列表清理 / List Cleaner 1.6.16

版本码 / Version code: 41

## 中文

- 修复 Vector / 部分 libxposed API 102 实现下 RemotePreferences 可能长期停留在旧快照，导致 system_server 读取不到最新 `config_v1`、规则数量变成 0、Runtime ACK 失败的问题。
- Runtime Probe 现在携带管理端期望的配置 digest。官方 LSPosed 正常情况下继续使用原有缓存 RemotePreferences + 变更监听快速路径；只有 Hook 当前 digest 与管理端不一致时才执行一次 fresh RemotePreferences 读取。
- 增加安全兼容回退：管理端写入 `config_v1` 时在同一事务中同步镜像既有 legacy 配置键；只有 system_server 确认 probe 调用 UID 确实属于 `com.yagay.ListCleaner` 后，才允许使用镜像配置恢复并回执，避免放宽原有身份校验。
- fresh 配置仍不可见时不再用空 legacy 数据覆盖最后一份有效 Hook 快照，而是保留旧配置并拒绝 ACK，等待下一次同步，避免规则意外清零。
- 诊断日志新增 expected/actual digest、cached/fresh 配置可见性、fresh 是否为同一实例、verified mirror 恢复以及 ACK 跳过原因，便于区分框架缓存问题与真实 Hook 失败。
- 本次修改涉及 Xposed 运行时和 Runtime Probe 合同，因此 Hook compatibility 提升到 41。安装更新后需要让 system_server 加载新的 Hook 代数；旧 Hook 不会被误判为兼容。

---

## English

- Fixed a Vector / libxposed API 102 compatibility issue where RemotePreferences could remain on a stale snapshot, causing system_server to miss the latest `config_v1`, see zero rules, and fail the runtime ACK handshake.
- Runtime Probe now carries the config digest expected by the manager. Official LSPosed keeps the existing cached RemotePreferences + change-listener fast path; a one-shot fresh RemotePreferences read is used only when the hook digest does not match the manager.
- Added a safe compatibility fallback: the manager mirrors legacy config keys in the same transaction as `config_v1`. The system hook may recover from that mirror only after verifying that the probe caller UID actually belongs to `com.yagay.ListCleaner`, preserving the existing identity boundary.
- If neither cached nor fresh atomic config is visible, the hook no longer overwrites the last known-good snapshot with empty legacy data. It keeps the existing rules and withholds ACK so the manager can retry.
- Diagnostics now record expected/actual digests, cached/fresh config visibility, whether the fresh read returned the same instance, verified-mirror recovery, and ACK-skip reasons.
- Because this release changes Xposed runtime behavior and the Runtime Probe contract, hook compatibility is bumped to 41. After updating, system_server must load the new hook generation; older hooks are no longer treated as compatible.
