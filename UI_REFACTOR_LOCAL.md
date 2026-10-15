# ListCleaner test3 · Material 3 视觉组件规范

适用范围：清理主页、规则候选列表、Root 组件列表、排序工作区、应用隐藏列表、状态页及全屏选择器。这里的改动仅统一视觉组件，不改变规则保存、扫描、Root、锁定或 LSPosed Hook 逻辑。

## 单一设计系统

- `ui/MaterialDesignSystem.kt` 的 `MaterialUiTokens` 是页面边距、卡片、应用行、图标、按钮、分组间隔的唯一视觉尺寸来源。请勿重新引入 `WorkspaceUiTokens`、`MaterialControlMetrics` 或 MIUIX 组件。
- `MaterialPageColumn` 管理常规滚动页面的宽度与节距；使用 LazyColumn 的大型候选列表通过 `MaterialUiTokens` 对齐相同横向安全边距。
- `UiCard.kt` 是唯一调用原生 Material 3 Card 的位置，通过 `UiCardVariant.SECTION / CATEGORY / LIST_GROUP / STATUS` 区分用途；公共色彩、圆角、分组间距和警告边框都由它处理。`MaterialSectionCard` 仅编排内容内边距，`WorkspaceCategoryCard` 仅编排分类内容，`AdaptiveGroupSurface` 仅保留旧的列表调用接口，三者全部委托给 `UiCard`。
- `MaterialActionColors.kt` 是所有按钮和选中导航的唯一颜色策略。Filled、Outlined、Text、Icon、选中态 FilterChip 与底部 NavigationBarItem 都使用当前 Material 3 主题的 `primary` 主色。底部导航原生选中胶囊在图标后面，因此胶囊内的图标使用 `onPrimary`，胶囊外的选中文字使用 `primary`，未选中使用 `onSurfaceVariant`。填充按钮与选中筛选内文字使用 `onPrimary`，描边、文字、图标操作使用主色前景。禁用状态由 Material 3 处理，不再使用彼此独立的配色。
- `MaterialSettingsRow` 用于有说明文字、尾部操作按钮或开关的设置条目。
- `MaterialAppRowBody` 定义应用图标、名称、包名/副标题、左侧选择器和尾部插槽；规则、排序、Root 的 `WorkspaceAppRow` 与应用隐藏选择器共同使用。它们保留不同的勾选、锁定、拖动与展开语义。
- 分组卡片使用 `ListRowPosition` 决定首/中/末圆角，其半径与分隔间距来自 `MaterialUiTokens`。已带 16dp 页面内边距的应用隐藏和二级分类列表传入 `horizontalInset = 0.dp`，不得叠加同等内边距。可选择和只读详情共用 `UiCardDetailRow`。
- `MaterialFullScreenDialog`、`MaterialDialogActions` 提供全屏弹窗统一的返回入口、顶部栏和操作区。普通确认对话框仍通过 `AdaptiveAlertDialog` 使用 Material 3。
- `MaterialInlineStatus` 供列表和工作区展示错误与提示；`ModuleStatusRow`、`RuntimePanel` 和 `CleanupStatusAlert` 都通过 `UiCardVariant.STATUS` 展示，并将警告状态作为边框参数。加载中和空态继续用现有 `WorkspaceProgress` 与 `WorkspaceEmptyState`。
- `Adaptive*()` 是公共 Material 控件的兼容入口。不要在功能页面再次创建独立的 `Button`、`FilterChip` 或返回导航组件。

## 验收

执行 `python3 tools/check-material3-controls.py`，再运行 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。test3 的 GitHub Actions 会运行上述检查并上传 Debug APK 和当前提交的完整源码。

实机还需检查浅色、深色、系统模式、小屏、大字体、返回、页面滚动、展开、批量选中、锁定、排序拖动、Root 开关和权限提示。自动编译及静态检查不能证明实机视觉、无障碍或 Hook 行为完全正确。

## 独立设置入口

- 底部“设置”使用 `Destination.SETTINGS`（齿轮），沿用 `MaterialActionColors.navigation()` 的统一选中与未选中配色。
- 底部导航仅有清理、排序、设置三项；不再存在独立的 `Destination.DASHBOARD` / `DashboardTabContent`。
- `SettingsTabContent` 在同一滚动设置页承载外观模式、全局清理模式、备份、应用隐藏列表、诊断模式开关，以及原状态页的 RuntimePanel、同步状态、Hook 命中统计、模块作用域入口、文件预览与诊断导出操作。
- 所有模块状态/运行异常的旧跳转直接进入设置；设置页禁用搜索入口。保留共享组件、状态数据来源、返回逻辑和 Root/Hook 行为，不再维护两个重复页面。

## 完整设置中心（独立于 Hook 规则）

- `UiPreferences.kt` 将全部新增 UI 偏好放在 `ui_preferences_v1`，并用 `UiPreferencesRuntime` 的 Compose state + StateFlow 通知 UI 和规则列表排序。设置不进入 `ModuleConfig`，也不会造成 LSPosed 配置修订/重启。
- `SettingsPreferencesControls.kt` 提供主色切换、界面密度、卡片圆角、按钮高度、图标和列表高度（即时预览），启动页、应用列表排序、默认筛选和用户/系统应用默认分类，以及扫描策略和手动刷新。
- `MaterialUiTokens` 由该配置的响应式 getter 统一确定尺寸，所有旧消费者继续从同一处取值。系统图标触控区仍至少 48dp。
- `UiScanMode`：智能缓存（默认 10 分钟，可调 1–60 分钟）、仅手动、每次进入；扫描失败仍执行 30 秒重试保护。
- `UiAppSort` 只改变管理器中的规则候选应用顺序，不改变 LSPosed 目标优先排序。默认启动页、筛选及应用分类在重新进入应用后生效。
- 列表页增加“记住上次使用筛选”开关，开启后通过 `UiPreferencesStore.rememberFilter/rememberAppType` 存储实际选择；未开启时使用独立配置的默认筛选和默认应用分类。首页分类支持单列/双列，规则和排序说明支持默认展开。
- Android 13 以上的“应用语言”入口跳转系统逐应用语言设置，低版本打开应用信息。
- “恢复默认设置”重置界面/列表/扫描偏好与明暗模式，不修改任何已保存的规则、锁定、Root 组件、优先排序或 Hook。
- 原 `UiAppearanceStore` 继续存明暗模式；备份导入/导出的 JSON 仍只涉及清理数据，未混入 UI 偏好，确保旧版本兼容。

## 六个独立设置分类（test3）

- **设置首页** 不再把所有设置堆在同一长页面里；`SettingsCenterHome` 使用一个共享的分组列表容器，每一行统一图标、标题、说明、右箭头和 Material 3 卡片样式。
- 独立二级页面：`SettingsSection.APPEARANCE` 外观与主题、`LISTS` 列表与显示、`SCANNING` 扫描与刷新、`COMPATIBILITY` 清理与兼容、`BACKUP` 备份与恢复、`ADVANCED` 高级与诊断。
- 每个分类的内容仍使用 `MaterialPageColumn` / `MaterialSectionCard` 和现有状态流；不重复实现 Hook、Root、备份、列表或外观偏好的任何业务逻辑。
- 子页面由 `rememberSaveable` 保存导航层级，二级内容页的返回箭头和安卓 Back 返回设置首页；不增加底部导航目的地。原“状态”相关内容全部位于“高级与诊断”。
- 恢复默认界面设置放在“外观与主题”，不会清空清理规则。备份页明确告知 JSON 不含独立 UI 偏好。
