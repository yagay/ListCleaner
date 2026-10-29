# 列表清理 · List Cleaner

**简体中文** | [English](https://github.com/Xposed-Modules-Repo/com.yagay.ListCleaner/blob/main/README.en.md)

<!-- section:intro -->
清理 Android 分享、打开方式、浏览器/深度链接、长按快捷方式和文件选择等系统候选，支持优先排序与 Android 多空间；也可通过 Root 管理桌面图标、磁贴、快捷方式创建入口和小部件。

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

- **系统入口清理**：管理分享、多文件分享、发送到、打开方式、浏览器、深度链接、拨号、内容/媒体选择、文本处理、长按快捷方式与 Storage Access Framework 存储位置。浏览器与深度链接独立，深度链接支持域名规则。
- **筛选与操作**：搜索、按应用展开、类别筛选、用户/系统应用筛选；应用和组件右侧三点菜单（或长按行）提供加锁/解锁。批量操作会跳过锁定项，锁定子项也需先解锁才能直接勾选。规则更改需确认“应用”。
- **优先排序**：各分类独立保存顺序，通过右侧拖动手柄调整，上移/下移仍可用；确认“应用”后才保存，长按应用行打开操作菜单。
- **Root 组件管理**：桌面入口、TileService 磁贴、LauncherApps/旧式快捷方式创建入口与 AppWidgetManager 小部件。标准 API 优先，兼容扫描补充并安全降级；Root 实际禁用结合 LSPosed 发现过滤、开机及更新后状态校正，最终以系统回读为准。
- **Android 多空间**：规则、排序、组件状态和批量锁按用户空间隔离；可切换目标空间，选择多个空间导出 v13 备份并逐项映射导入位置。
- **设置与诊断**：Material 3 扁平化列表，设置集中外观、列表、扫描、兼容、备份、高级诊断和关于，包含 GitHub / Telegram 项目链接。

<!-- section:usage -->
## 使用说明

底部导航为**入口、组件、设置**；在入口页选中支持排序的分类后，点击排序图标进入**优先级排序**；组件页单独管理 Root 组件。规则只过滤系统返回的候选，不会真正禁用组件；组件清理则通过 Root 变更启用状态。三点菜单或长按行控制**批量锁**，锁不代表组件禁用。规则、排序和 Root 组件更改都需要在底部确认应用，组件 Root 命令随后才执行。

检测到多个 Android 用户空间时，可在顶部切换目标。v13 备份支持选择多个空间并在导入时明确映射，旧版 v1–v12 规则备份仍可读取。Root 禁用组件列表可作为**参考记录**导出，但导入**不执行 Root 命令、不自动恢复实际禁用状态或保护策略**。卸载或清除本应用数据同样不会自动启用此前禁用的组件。

厂商自定义选择器、应用私有菜单和部分动态快捷方式可能不受支持。组件扫描采用标准 Android API 和兼容路径；更新 APK 后如运行中的 system_server Hook 仍是旧版本，将安全降级直至新 Hook 加载。请在“设置 → 清理与兼容 / 高级与诊断”检查状态；分享诊断前确认其中的应用和日志信息。



### 排序支持与系统 Hook

排序仅对有对应运行时消费路径的入口分类开放。排序图标在不支持的分类中显示灰色，但仍可点击查看“当前列表不支持排序”的提示。**支持编辑不等于系统最终界面一定遵守优先级**：系统分享面板、桌面、角色选择器和厂商界面可能再次排序。

- **存储位置 / Document Root**：复用 DocumentsUI 根目录查询与最终侧栏 Hook，仅在能够识别的位置按保存的优先级调整顺序；保留系统行、分隔项及多用户位置。不同厂商 DocumentsUI 可能不兼容。
- **屏保 / Dream**：尝试在 Settings 的 `DreamBackend.getDreamInfos()` 返回候选时按应用优先级排序；不更改默认屏保或启用状态。类或方法不存在时跳过。
- **Document Provider、Photo Source、Notification Listener、Wallpaper**：尚无经过确认的最终界面排序路径，暂不开放排序。旧 Launcher Shortcut 类型已废弃。

每个 Android 用户空间独立保存规则和排序；新空间默认采用“隐藏选中”，列表默认优先显示用户应用。旧备份的排序配置不因界面可用性调整而删除。实际生效请在目标系统选择器验证；CI 通过并不代表设备适配验证通过。

<!-- section:release -->
正式版与作者仓库使用相同 APK 和固定签名。Debug 版本可能签名不同，卸载前请先导出规则并检查组件启用状态。

<!-- section:feedback -->
反馈问题时，请提供系统版本、设备型号、框架版本和复现步骤；分享诊断前请检查其中的应用列表和日志内容。
