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
