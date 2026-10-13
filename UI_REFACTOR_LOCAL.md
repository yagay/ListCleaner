# ListCleaner test 源码 · 本地 UI 重构说明

**基准：用户上传的 `ListCleaner-test.zip`（原 test 提交 `d61827be`）。本地修改，没有 GitHub 上传、推送或分支变更。**

## 页面层级

- 清理首页：分享、操作、桌面、扩展清理四个独立功能卡块，每行两块，图标与入口数量来自原始 `EntryGroup` 数据。
- 二级入口页：点击分类才显示对应的 `IntentKind` 项目，每项有原始名称与说明；原有不可选择的 `LAUNCHER_SHORTCUT` 不出现。
- 规则工作区：选择具体入口后打开对应应用列表，保留搜索、应用类型、已选/未选/锁定、预设/网页域名、批量操作、子组件展开与规则保存。
- 保留“浏览全部应用”入口，可跨分类搜索；点击面包屑返回分类或上一级入口。
- Root 组件清理：独立的桌面入口 / 磁贴 / 创建快捷方式 / 小部件四种卡块，绝不混入 `EntryGroup.DESKTOP` 的规则体系。
- 排序页：分享 / 操作 / 扩展清理三个可排序入口块，进入具体类型后共用紧凑应用行，保留原有优先级设置、按项排序和恢复功能；只有专用拖动手柄启动移动，滚动进入视野的应用可作为移动目标。

## UI 结构变化

- 新建 `EntryWorkspaceHome.kt`, `RootWorkspaceHome.kt`, `WorkspaceAppRow.kt`。
- 规则、排序、Root 三个页面共享一个明确的应用行交互契约，左右分配选择、名称、锁定/更多、展开，避免各页面维护不同尺寸与操作规则。
- Material 使用原生 Card，MIUIX 使用原生 Miuix Surface/Card；保持两套主题各自的原生渲染，而不是更改业务引擎。
- 清理页不再先显示所有应用的长列表；选中具体入口后才显示应用列表。旧横向四分类条已从该页面的应用列表工作区移除。
- 缩减清理页统计摘要占用；排序列表明确区分已排序/未排序，并将大段说明折叠。
- Root 批量确认固定原始类别，避免弹窗打开期间切换分类导致操作目标错乱。

## 验证现状

- Android 英/中文资源完整性：`python tools/check-bilingual-parity.py` 通过，XML 全部解析通过。
- 纯 Kotlin 分类映射：测试四组 3/13/1/15 种可选择入口，全部覆盖及旧入口排除，通过。
- 轻量 Kotlin 语法检查：新文件及变更文件没有出现解析级 `expecting`/`unexpected tokens` 错误；缺少 Android/Compose 依赖无法做完整类型检查。
- **Debug APK 构建未验证**：本地容器不含 Android SDK，也没有预装 Gradle 9.4.1；离线启动 Gradle Wrapper 时无法下载发行包（`UnknownHostException: services.gradle.org`）。需要在有 SDK/Gradle 的设备上执行 `./gradlew :app:assembleDebug testDebugUnitTest lintDebug`。
- 仓库自带的 `tools/ClassificationCheck.java`（非 UI 测试）目前不通过：`geo + ACTION_VIEW` 预期 null，实际 OPEN。此次 UI 改动未修改该分类器，也未把该原有测试失败描述为重构成功。

## 后续实机验收建议

检查四分类块和二级入口、搜索中的跨分类行为、返回/旋转、MIUIX 与 Material 双主题、小屏与字体放大、应用行锁定/展开/选择、Root 各类及批量确认、排序拖动和自动滚动。自动化测试不能替代这些实机验证。
