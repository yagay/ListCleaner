# 列表清理 · List Cleaner

**简体中文** | [English](https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/blob/main/README.en.md)

<!-- section:intro -->
精简 Android 分享、打开方式、浏览器和文本处理菜单，让常用应用优先显示；通过 Root 管理应用提供的磁贴、快捷方式创建入口和桌面小部件。

[下载模块](https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/releases/latest) · [源码与完整说明](https://github.com/yagay/ListCleaner) · [问题反馈](https://github.com/yagay/ListCleaner/issues) · [Telegram 频道](https://t.me/LISTCLEANER)

<!-- section:telegram -->
## Telegram 频道

关注 **@LISTCLEANER** 获取版本更新、使用提示和相关消息。

[加入 Telegram 频道 @LISTCLEANER](https://t.me/LISTCLEANER)

<p align="center">
  <a href="https://t.me/LISTCLEANER">
    <img src="https://raw.githubusercontent.com/yagay/ListCleaner/main/docs/telegram-channel.jpg" alt="List Cleaner Telegram 频道二维码" width="360">
  </a>
</p>

<!-- section:requirements -->
## 使用要求

- Android 12 及以上。
- **规则过滤和排序**需要支持 **modern libxposed API 102** 的框架。启用模块、配置推荐作用域，并按框架提示重启。
- **组件管理**另外需要在 KernelSU、Magisk 等管理器授予 Root 权限。启用 LSPosed 模块不等于授予 Root。

<!-- section:features -->
## 功能

- **规则过滤**：分享、多文件分享、打开方式、浏览器、文本处理分别配置，支持搜索和按应用展开组件。可选择隐藏选中、只显示选中或全部显示；当前分类没有选中规则时显示全部。
- **批量操作锁与应用类型筛选**：规则、排序和组件页共用锁定机制；应用或子项向右滑加锁、向左滑解锁，未锁定不显示图标，锁定后才显示。整体或部分锁定的项目会跳过全选/反选等批量操作，仍可手动修改；“全部 → 已锁定”会汇总各分类锁定项。同时可用紧凑下拉按全部、用户应用、系统应用过滤。应用类型与锁定都不参与排序，也不改变已选/半选/未选状态。
- **优先排序**：按分类保存应用顺序。勾选加入优先列表、取消后回到默认名称顺序；支持长按拖动和上移、下移。锁定不会改变已保存顺序。
- **组件管理**：标准磁贴继续按 TileService 发现；快捷方式创建入口合并 Android LauncherApps 配置 Activity 与旧 ACTION_CREATE_SHORTCUT；小部件优先读取 AppWidgetManager Provider 注册表并以 Manifest 扫描补充。组件发现采用标准 API 优先、兼容路径补充、异常 fail-open；APK 更新后若 system_server 仍运行旧 Hook，会通过运行时能力握手自动降级到安全扫描，确认新版协议后才启用完整 Widget 注册表发现。Root 负责真实禁用；同时保存持久禁用策略，通过 LSPosed/system_server 过滤发现结果，并在开机、解锁及应用更新后自动校正被恢复的组件状态。
- **备份与诊断**：导入、导出规则和排序 JSON 备份，查看模块状态并导出诊断日志；组件诊断包含持久策略、发现过滤和开机校正结果。

<!-- section:usage -->
## 使用说明

规则页的应用勾选只针对当前分类和搜索条件下显示的组件，不会禁用整个应用。组件页则实际修改系统组件启用状态，两者作用不同。批量操作锁通过**右滑加锁、左滑解锁**；未锁定不显示锁图标，锁定后才显示状态图标。整体锁保护整个应用，部分锁只保护对应子项；被锁项目仅跳过全选/反选等批量操作，仍能手动修改。锁不参与排序，也不改变已选/半选/未选状态。分类锁仍独立保存，而“全部 → 已锁定”负责聚合展示这些分类锁。规则、排序和组件页都可用“应用：全部 / 用户应用 / 系统应用”紧凑下拉缩小当前显示与批量操作范围；该筛选不改变保存顺序或任何规则状态。

组件操作只针对本应用所在的 Android 用户。Root 真禁用是主机制；持久禁用策略同时用于 LSPosed 发现过滤和开机自动校正。系统启动后会进行延迟校正和稳定后复查，应用安装/更新后也会复查。应用安装、更新或卸载会自动让候选缓存失效；诊断会记录组件发现来源（PackageManager / AppWidgetManager / LauncherApps）和运行时组件发现协议，便于定位漏项或旧 Hook 兼容问题。禁用可能影响已添加的磁贴和小部件，重新启用不保证恢复原位置。卸载模块或清除数据不会撤销组件禁用状态，规则备份也不包含这些状态。

厂商定制选择器、应用自行重排或自建菜单可能不受支持。快捷方式管理不覆盖所有动态、固定快捷方式或应用私有项目；没有独立服务的系统内置磁贴不在管理范围内。

<!-- section:release -->
正式版与作者仓库使用相同 APK 和固定签名。Debug 版本可能签名不同，卸载前请先导出规则并检查组件启用状态。

<!-- section:feedback -->
反馈问题时，请提供系统版本、设备型号、框架版本和复现步骤；分享诊断前请检查其中的应用列表和日志内容。
