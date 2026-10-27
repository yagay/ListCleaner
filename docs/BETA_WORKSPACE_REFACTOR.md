# Beta：工作区 UI 架构重构

本文件记录 beta 分支的界面架构调整；**不改变 LSPosed Hook、Root 执行器、规则标识、备份格式或多空间规则**。Beta 不用于自动发布正式版。

## 信息架构

- 底部主导航继续使用 **清理 / 排序 / 设置**。
- 清理内保留 **规则（LSPosed）/ 组件（Root）** 的双模式切换；分类与筛选属于当前工作区，不是新的主页面。
- 规则运行模式（隐藏选中/只显示选中/全部显示）直接在规则页面可见，与只调整列表显示的筛选分离。
- 系统效果预览不再塞入定高小弹窗；使用全屏预览，保留实际返回、候选、启用状态、历史数据的来源区别和系统测试动作。
- 设置首页展示实时运行状态入口；详细检测、作用域、备份和诊断仍在原来的功能区。

## 共享组件

| 组件 | 职责 |
| --- | --- |
| `WorkspaceStickyHeader` | 清理模式 + 分类/筛选共用一个固定区域；排序复用同一框架 |
| `WorkspaceEditorFrame` | 为列表、拖动浮层和待确认操作栏分配真实布局空间，不使用 90dp 的硬编码遮挡补偿 |
| `WorkspaceAppRow` | 应用组图标、三态选择、锁定菜单、展开 |
| `WorkspaceDetailRow` | 组件级选择、菜单、锁定以及多行信息 |
| `PendingChangesBar` | 三类编辑器共用待应用/放弃及二次确认 |
| `MaterialFullScreenDialog` | 系统预览沿用项目的全屏弹出层和返回处理 |

## 操作语义保持不变

- **规则**：选中一条候选规则并不禁用系统组件；最终效果还受到全局显示模式控制。
- **组件**：选中代表目标为禁用，Root 操作在明确应用后执行并回读结果。
- **排序**：勾选代表加入优先顺序，拖动和上/下移动保持原样。
- **锁定**：与选中状态独立；长按和三点仍打开同一菜单，受锁项不参与批量操作。
- **多空间**：每个空间的规则、排序、组件和锁相互隔离；切换空间的保护流程不变。

## 验证

最后统一触发 beta 的 Debug CI：

1. 源码本地化、翻译资源、中英对照和 Material 控件结构检查；
2. arm64-v8a Debug APK 编译；
3. Debug 单元回归测试；
4. 必须再在实机检查深浅色、系统字体放大、窄屏、两种系统导航方式、展开/选中/锁定、Root 授权、拖动排序、全屏系统预览及多空间返回栈。

编译与单元测试通过不等于已完成实机行为验收。


## 第二轮对照整改（本分支）

- 应用选择控件占固定槽位；组件行使用同宽占位以对齐应用图标及内容，保留三点菜单和展开箭头。
- 分类选项的名称在窄屏可截断，不再挤走末尾展开指示。
- 列表可视觉紧凑，但交互目标的最小高度不得小于 48dp。
- Root 详情明确显示实际启用/禁用状态与待提交的启用/禁用目标；Root 的状态筛选不等价于 LSPosed 规则勾选。
- 排序中的已优先/未优先两个区域显示各自数量，拖动逻辑保持独立。
- 系统预览以系统动作优先、其他动作水平滚动、关闭按钮常驻的底部控制栏替代多层竖排操作按钮，且保留导航栏安全边距。
- 规则清理的固定模式/分类栏在前，运行模式信息随列表滚动；设置首页优先呈现真实错误或同步状态。
- 以上约束加入 UI 源码回归检查，但最终视觉效果仍必须通过实机验证。

### 与最初方案的边界

本轮没有新增全局「应用管理」主导航，也不重写 Root/LSPosed 数据与执行器；系统预览仍是全屏 Dialog（作为二级工具），不是独立持久导航目的地。未来卡片模式可以作为可选渲染器，但本轮维持唯一的紧凑列表模式。

### Beta QA checklist

Verify: runtime mode vs list filter, selected/indeterminate locks, Root system-state vs pending-state markers, ordered/available counts, horizontal preview footer and persistent Close, wide/narrow layouts, large font, all Android user spaces. Debug CI validates compilation, but does not replace those device checks.

## Global Root component mode (beta)

- Active is the default; Root-managed components follow their saved disabled entries.
- Paused temporarily enables only exact component identities already saved by ListCleaner for the selected Android user. It never clears the desired disabled entries, and it does not change LSPosed's Global Cleaning Mode.
- Root transitions perform a fresh full component scan before operations and use the existing per-component verification path. A partial transition reports failures and can be retried in the Root workspace.
- While paused, ordinary Root manual editing is unavailable. On resuming Active, the saved disabled set is applied again.
- The remote component-state guard and atomic runtime configuration use the **effective** disabled set, excluding paused Android user IDs. Boot/package reconciliation does not re-disable paused users.
- The mode flag is local to the manager and independent of rule-backup import/export. It does not authorize a backup to execute arbitrary Root commands.
- On a device where components are unavailable, Root is denied, or discovery is incomplete, the selected mode may be stored while a subset of Android overrides remains unchanged. Review the reported result and use Retry; do not assume all components changed.


## 完整布局对照（后续修订）

1. **导航**：保持清理 / 排序 / 设置三个主入口，清理内部切换 Entry / Component；不新增仪表盘。
2. **固定区**：Entry / Component 的模式切换与 Category / Filter 共享一个 sticky header；列表摘要和运行状态随列表滚动。Open / Deep Link 子分类合并进入 Filter 菜单，避免挤压分类宽度。
3. **统一密集列表**：应用选择区域、图标与组件详情均使用统一的槽位；展开应用使用窄色条提示展开，组件的选中状态仍使用选中背景，两种语义不同。
4. **全局模式**：LSPosed 规则模式与 Root Active / Paused 模式只共享视觉组件，不共享持久化或底层操作。Root 暂停时展示保存的禁用规则与真实的组件状态。
5. **排序**：已优先 / 未优先分别显示数量；拖动与原有规则草稿保持独立。
6. **系统预览**：保留全屏预览、真实结果 / 候选 / 已启用 / 历史来源分段与横向操作区。
7. **设置**：按照外观与列表、扫描与权限、备份与维护明确分组，不依赖枚举项位置。
8. **自动回归**：新增按用户空间过滤 Root 有效策略的单元测试，以及针对固定槽位、菜单位置、设置分组和模式语义的结构检查。

构建通过仅代表编译、结构检查和纯逻辑单测通过。Root 模式的真实系统行为、组件自恢复、跨空间切换、预览来源准确性、导航返回以及大字体/窄屏适配必须在测试机上继续验证；这些不能由静态测试替代。
