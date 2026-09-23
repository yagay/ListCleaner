# 列表清理 / List Cleaner 1.6.17

版本码 / Version code: 42

## 中文

- 新增 Runtime Probe v2：运行中的规则更新不再依赖 RemotePreferences 是否及时刷新。管理端会在需要时把真实的 `ModuleConfig` 分块传入已运行的 `system_server` Hook；RemotePreferences 继续只负责持久化、冷启动恢复和官方 LSPosed 的快速同步路径。
- `system_server` 会先验证 Binder 调用 UID 确实属于 `com.yagay.ListCleaner`，再重组配置、重新计算 SHA-256、校验 `managerAppId` 并完整解析配置。只有实际收到并应用的配置 digest 与期望值一致时才返回 Runtime ACK。
- 删除 1.6.16 的 legacy mirror digest 回退，不再允许用管理端传入的 expected digest 给旧配置“标记成功”；运行时推送失败时继续保留最后一份有效规则。
- Probe v2 使用分块传输，并加入 transfer ID、调用 UID 绑定、大小/分块数量限制、10 秒超时、完整性与 digest 校验，避免 Binder 大事务和半套配置被应用。
- 一旦 Probe v2 成功应用配置，当前进程内的 Hook 快照成为运行时权威来源；即使 Vector / 部分 libxposed API 102 实现继续返回 stale RemotePreferences，也不会再把新规则回滚成旧规则。
- Resolver 过滤和优先排序优先使用 `system_server` 在 `ResolveInfo` 副本中附带的 include/rank/digest 元数据，因此过滤、排序和自定义标题使用同一份运行时规则，避免 system_server 已更新而 Resolver 仍停留在旧配置。
- 官方 LSPosed 仍保留原有低开销路径：如果 RemotePreferences 变更监听已经让 Hook 获得最新配置，首次 Runtime ACK 即成功，不需要额外传输。
- 本次修改涉及 Xposed 运行时和 Runtime Probe 合同，Hook compatibility 提升到 42。升级后需要让 system_server / 相关 Hook 进程加载新的 Hook 代数。

---

## English

- Added Runtime Probe v2 so live rule updates no longer depend on when a framework refreshes RemotePreferences. When needed, the manager transfers the actual serialized `ModuleConfig` in chunks to the already-running `system_server` hook. RemotePreferences remains the persistence/cold-start store and the fast path when official LSPosed propagates changes normally.
- `system_server` now verifies that the Binder caller UID belongs to `com.yagay.ListCleaner`, reassembles the payload, recomputes SHA-256, validates `managerAppId`, and fully decodes the configuration. Runtime ACK is returned only for the digest of the configuration that was actually received and applied.
- Removed the 1.6.16 legacy-mirror digest fallback. An expected digest supplied by the manager can no longer make stale legacy data appear successfully applied; failed transfers keep the last known-good rules.
- Probe v2 uses chunked transport with transfer IDs, caller-UID ownership, size/chunk limits, a 10-second timeout, completeness checks, and digest verification to avoid oversized Binder transactions and partial configuration application.
- After a verified Probe v2 update, the in-process hook snapshot becomes authoritative for the running process. Stale RemotePreferences returned by Vector or another libxposed API 102 implementation cannot roll the live policy back.
- Resolver filtering and priority ordering now prefer include/rank/digest metadata attached to copied `ResolveInfo` results by `system_server`, keeping filtering, ordering, and custom titles on the same live policy instead of maintaining an independently stale Resolver snapshot.
- Official LSPosed keeps the low-overhead path: if its RemotePreferences listener has already delivered the latest configuration, the first Runtime ACK succeeds and no configuration transfer is needed.
- This release changes Xposed runtime behavior and the Runtime Probe contract, so hook compatibility is bumped to 42. After updating, system_server and the relevant hooked processes must load the new hook generation.
