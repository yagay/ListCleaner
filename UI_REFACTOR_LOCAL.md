# List Cleaner · Material 3 扁平化列表规范（main）

本文件描述当前已合并到 main 的界面约束，取代旧 test3 卡片式方案；视觉重构不得改变规则、Root、Hook、多空间及备份的业务语义。

## 统一设计系统

- `MaterialDesignSystem.kt` 中的 `MaterialUiTokens` 统一边距、行高、间距、图标及触控区，随外观偏好响应式更新；图标触控区至少 48dp。
- `FlatListDesign.kt` 中的 `FlatRowSurface` / `FlatCategoryRow` 负责平面行背景、细分隔线、分类与设置行。`UiCard.kt` 保留历史 API 名 `UiCard`，实际改用 `Surface + RectangleShape`，不应再恢复多套独立 Card 圆角与阴影。
- `MaterialPageColumn`、`MaterialSectionCard` 与 `MaterialSettingsRow` 统一普通页面和设置页面的水平对齐；`MaterialActionColors` 统一操作、选中筛选和底部导航的颜色。
- `WorkspaceAppRow` / `MaterialAppRowBody` 是规则、排序与 Root 共用的应用行；`WorkspaceDetailRow`、`WorkspaceDetailHeading`、`UiCardDetailRow` 是组件详情的共同结构。选择、预览、Root 禁用、锁定及排序等业务由各功能控制器分别提供。
- `WorkspaceFlatOverflowMenu`、`WorkspaceDetailActionsMenu` 和 `FlatWorkspaceDropdownMenu` 共用扁平化三点菜单。不要重新引入 MIUIX、旧长按浮层或第二套菜单渲染器。

## 导航、列表和高亮

- 底部导航为**清理、排序、设置**三项；组件清理位于清理页内部，`ClassicCleanupTabs` 在规则/组件间切换。注意清理模式切换的细线与**底部导航不另加选中横线**是不同层级。
- 分类和过滤的已选状态使用主题高亮。`ContinuousApplicationSurface` 仅在应用展开时高亮；`ContinuousDetailSurface` 在组件选中时高亮，未选组件保持常规背景。
- 应用选择标记共用 `WorkspaceAppSelectionCheckbox`：未选为加号、半选为减号、已选为勾号；保留三态无障碍语义与完整触摸区。
- 应用及组件行**长按打开右侧三点菜单**，菜单提供符合当前对象权限的锁定、解锁及其他操作；锁标识和选择标识各自独立，组件详情尾部三点菜单与应用行对齐。
- 可用 Android 多空间大于一个时，顶部显示当前空间名称与展开箭头；选择空间后刷新相应空间数据，不复用其他空间的规则或 Root 状态。

## 操作提交与权限

- 规则页 `PendingSelectionDraft` 和排序页 `PendingPriorityOrder` 先暂存更改；`PendingChangesBar` 提供放弃、应用和再次确认。不得把行内点击重新改为立即持久化。
- 排序中的**拖动手柄**独立处理 `detectDragGesturesAfterLongPress`、边缘滚动和放下后的草稿更新；长按应用行本身仍打开菜单，展开状态下的上移/下移继续有效。
- Root 组件禁用须遵循权限检查、批量确认、系统回读与持久保护策略。锁与 Root 禁用不同：锁定会让全选/反选跳过该项目，且锁定子项无法直接勾选，需先解锁。
- 实际系统预览应区分验证过的生效候选、诊断候选和历史条目；Hook/权限/OEM 不可验证时安全降级，不能以扫描总表伪称系统实际列表。

## 设置和文档维护

- 设置首页分为**外观与主题、列表与显示、扫描与刷新、清理与兼容、备份与恢复、高级与诊断**，另有**关于**页。前者放运行状态与作用域，后者放诊断导出与技术数据。
- `SettingsAbout` 显示版本、开发者和 GitHub / Releases / Issues / Telegram 链接。外观偏好不应写入 LSPosed 规则配置；恢复默认外观不能清空规则、排序、锁或 Root 状态。
- v13 多空间备份可选择来源空间并明确映射目标。Root 禁用组件记录仅作为可选参考，导入不得调用 Root 恢复真实禁用状态。

## 验收

- 检查：`python3 tools/check-material3-controls.py`、`python3 tools/check-bilingual-parity.py`，以及 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`（环境具备 SDK 时）。
- 实机验收：浅/深色、小屏、大字体、分类/过滤居中、展开与选中高亮、三点菜单/长按、排序手柄、确认应用、多空间、Root 权限与返回栈。
- GitHub Debug 工作流监听 `main` 与 `test3` 的相关源码/文档变更。**正式 Release 只能手动启动**；文档提交不发布 APK、不同步 LSPosed、不推送 Telegram。
