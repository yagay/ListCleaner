package com.yagay.ListCleaner.ui

import androidx.compose.material3.Text as Material3Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * Central UI translation layer. Chinese is kept as the source language for now;
 * English is used for English and every non-Chinese system locale.
 *
 * User-defined app/component names are intentionally not translated.
 */
internal object UiText {
    private val english = mapOf(
        "搜索应用、组件或包名" to "Search apps, components, or package names",
        "关闭搜索" to "Close search",
        "清空搜索" to "Clear search",
        "搜索" to "Search",
        "刷新" to "Refresh",
        "更多" to "More",
        "恢复备份" to "Restore backup",
        "导出备份" to "Export backup",
        "全部" to "All",
        "查看" to "View",
        "全选" to "Select all",
        "反选" to "Invert selection",
        "LSPosed 未连接" to "LSPosed not connected",
        "旧模块仍在运行／更新未完成" to "An older module is still running / update not completed",
        "模块状态需要处理" to "Module status needs attention",
        "检测到模块已加载" to "Module loaded",
        "LSPosed 已连接" to "LSPosed connected",
        "系统已确认配置" to "System confirmed the configuration",
        "需要检查配置，点击查看状态" to "Configuration needs checking; tap to view status",
        "查看模块状态" to "View module status",
        "查看未匹配的已选规则" to "View selected rules with no current match",
        "没有匹配的组件" to "No matching components",
        "应用列表" to "App list",
        "本页用于管理分享、多文件分享、打开方式、浏览器和文本处理等 Intent 候选入口：可按规则隐藏/保留组件，也可单独修改组件在系统候选菜单中的显示名称；不会修改实际 Intent、包名、组件名，也不会停用或卸载应用。" to "This page manages Intent candidates for sharing, multi-file sharing, opening, browsers, and text processing. You can hide or keep components by rule, or change a component's display name in the system chooser. It does not modify the actual Intent, package name, component name, disable apps, or uninstall apps.",
        "当前为“隐藏选中”：规则生效后，勾选的组件从对应候选列表中隐藏；取消勾选恢复默认显示。" to "Current mode: Hide selected. Once the rules are active, selected components are hidden from the corresponding candidate lists; deselect them to restore the default display.",
        "当前为“只显示选中”：规则生效后，对应分类保留勾选的组件，隐藏其他组件。" to "Current mode: Show selected only. Once the rules are active, selected components remain in the corresponding category and other components are hidden.",
        "当前为“全部显示”：暂停清理系统候选列表，勾选只保存配置，恢复清理模式后生效。" to "Current mode: Show all. System candidate-list cleaning is paused. Selections are saved but take effect after cleaning is resumed.",
        "勾选应用可批量选择当前分类及搜索条件下显示的组件；展开后可逐项选择，点铅笔可设置该组件在当前分类中的菜单显示名称。改名与是否勾选规则相互独立；留空保存恢复原名称。“查看”只筛选本页列表，不改变清理规则。" to "Select an app to batch-select components shown under the current category and search conditions. Expand an app to select individual components, or tap the pencil to change a component's menu name for the current category. Names and selection rules are independent; save an empty name to restore the original. View only filters this page and does not change cleaning rules.",
        "当前正在编辑“打开方式 · " to "Editing dedicated rules for “Open · ",
        "有效勾选由“全部”中的 OPEN 通用规则与当前类型专用规则共同组成；继承自“全部”的项目会继续显示已勾选，并在展开项中标明来源。继承项不能在分类型里直接取消，需要回到“全部”取消；当前类型新增的规则只影响该 MIME / scheme / 文件后缀类型。" to "Effective selections combine the generic OPEN rules from “All” with rules dedicated to the current type. Inherited items remain selected and show their source when expanded. Inherited items must be deselected under “All”; rules added for the current type affect only that MIME, scheme, or file-extension type.",
        "system 已确认暂停过滤、排序和自定义显示名称；相关配置仍保留" to "system has confirmed that filtering, ordering, and custom display names are paused; the configuration is retained",
        "本地已选择暂停，尚未确认系统已应用" to "Paused locally; system application has not yet been confirmed",
        "未设置勾选的分类暂时显示全部" to "Categories with no selections temporarily show all candidates",
        "本次刷新未完整完成，详情见状态页" to "This refresh did not complete fully; see the status page for details",
        "全局清理模式" to "Global cleaning mode",
        "控制系统选择器如何处理已选组件。" to "Controls how the system chooser handles selected components.",
        "切换" to "Switch",
        "同步状态" to "Sync status",
        "本地保存不等于系统生效；以配置确认状态为准，Resolver 侧效果仍需实际验证。" to "Saving locally does not mean the system has applied the configuration. Use the configuration acknowledgement as the source of truth; Resolver-side behavior still needs real-device verification.",
        "运行能力与实际命中" to "Runtime capabilities and actual hits",
        "system_server 已确认当前配置" to "system_server confirmed the current configuration",
        "尚未取得当前配置 ACK" to "No acknowledgement for the current configuration yet",
        "Intent 查询 Hook：" to "Intent query hooks: ",
        "应用可见性兼容过滤：" to "App visibility compatibility filters: ",
        "system 进程记录到的排序改写：" to "Ordering rewrites recorded by system processes: ",
        "查询和应用可见性计数来自 system_server 的真实执行。排序通常在独立 Resolver/Chooser 进程执行，因此这里的排序计数为 0 不能单独判定排序失效；诊断模式会记录 Resolver 侧 ORDER_DELIVERED，实际菜单仍以设备复现为准。" to "Query and app-visibility counts come from actual system_server execution. Ordering usually runs in a separate Resolver/Chooser process, so an ordering count of 0 alone does not prove ordering is broken. Diagnostic mode records Resolver-side ORDER_DELIVERED; verify the final menu on the device.",
        "刷新运行状态" to "Refresh runtime status",
        "数据备份" to "Data backup",
        "从 JSON 恢复" to "Restore from JSON",
        "导出为 JSON" to "Export as JSON",
        "模块状态" to "Module status",
        "应用隐藏列表" to "App visibility list",
        "诊断工具" to "Diagnostics",
        "诊断模式" to "Diagnostic mode",
        "正在检查文件…" to "Inspecting file…",
        "用实际文件预览最终打开方式" to "Preview final open targets with an actual file",
        "正在收集" to "Collecting",
        "一键导出诊断包" to "Export diagnostic package",
        "诊断已开启；请复现分享、打开或文本处理操作。" to "Diagnostics enabled; reproduce the share, open, or text-processing operation.",
        "需要 Root 授权" to "Root permission required",
        "返回" to "Back",
        "应用隐藏列表" to "App visibility list",
        "自定义显示名称" to "Custom display name",
        "原名称：" to "Original name: ",
        "继承自“打开方式 · 全部”" to "Inherited from “Open · All”",
        "恢复继承全部" to "Restore inheritance from All",
        "完整勾选" to "Fully selected",
        "半勾选" to "Partially selected",
        "未勾选" to "Not selected",
        "分享" to "Share",
        "多文件分享" to "Share multiple",
        "打开方式" to "Open",
        "浏览器" to "Browser",
        "文本处理" to "Text processing",
        "图片" to "Images",
        "视频" to "Video",
        "音频" to "Audio",
        "文本" to "Text",
        "压缩包" to "Archives",
        "磁力链接" to "Magnet links",
        "地图位置" to "Map locations",
        "APK 应用" to "APK apps",
        "PDF 文档" to "PDF documents",
        "Office 文档" to "Office documents",
        "未知" to "Unknown",
        "需要 Root" to "Root required",
        "已启用" to "Enabled",
        "已禁用" to "Disabled"
    )

    fun translate(text: String): String {
        if (Locale.getDefault().language.equals("zh", ignoreCase = true)) return text
        english[text]?.let { return it }
        return translateFragments(text)
    }

    private fun translateFragments(text: String): String {
        var result = text
        val fragments = english.entries
            .filter { it.key.length >= 2 && it.key != text }
            .sortedByDescending { it.key.length }
        for ((source, target) in fragments) result = result.replace(source, target)
        return result
    }
}

/**
 * String overload matching the Material 3 Text calls used by the UI.
 * Keeping this in the UI package lets existing screens transparently use the
 * locale layer without changing configuration/state/domain code.
 */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    Material3Text(
        text = UiText.translate(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style
    )
}
