package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.VisibilityScope

@StringRes
internal fun Destination.labelRes(): Int = when (this) {
    Destination.RULES -> R.string.nav_rules
    Destination.PRIORITY -> R.string.nav_priority
    Destination.TILES -> R.string.nav_components
    Destination.DASHBOARD -> R.string.nav_status
}

@StringRes
internal fun UiFilter.titleRes(): Int = when (this) {
    UiFilter.ALL -> R.string.common_all
    UiFilter.HIDE_SELECTED -> R.string.filter_unselected_rules
    UiFilter.SHOW_SELECTED -> R.string.filter_selected_rules
}

@StringRes
internal fun DisplayMode.titleRes(): Int = when (this) {
    DisplayMode.HIDE_SELECTED -> R.string.display_hide_selected
    DisplayMode.SHOW_SELECTED -> R.string.display_show_selected
    DisplayMode.SHOW_ALL -> R.string.display_show_all
}

@StringRes
internal fun IntentKind.titleRes(): Int = when (this) {
    IntentKind.SHARE -> R.string.intent_share
    IntentKind.SHARE_MULTIPLE -> R.string.intent_share_multiple
    IntentKind.OPEN -> R.string.intent_open
    IntentKind.BROWSER -> R.string.intent_browser
    IntentKind.PROCESS_TEXT -> R.string.intent_process_text
}

@StringRes
internal fun VisibilityScope.titleRes(): Int = when (this) {
    VisibilityScope.ALL -> R.string.common_all
    VisibilityScope.SHARE -> R.string.intent_share
    VisibilityScope.SHARE_MULTIPLE -> R.string.intent_share_multiple
    VisibilityScope.OPEN -> R.string.intent_open
    VisibilityScope.BROWSER -> R.string.intent_browser
    VisibilityScope.PROCESS_TEXT -> R.string.intent_process_text
}

@StringRes
internal fun CleanupKind.titleRes(): Int = when (this) {
    CleanupKind.TILE -> R.string.cleanup_tile
    CleanupKind.SHORTCUT -> R.string.cleanup_shortcut
    CleanupKind.WIDGET -> R.string.cleanup_widget
}

@StringRes
internal fun OpenPreset.titleRes(): Int = when (this) {
    OpenPreset.BROWSER -> R.string.open_browser
    OpenPreset.PDF -> R.string.open_pdf
    OpenPreset.WORD -> R.string.open_word
    OpenPreset.EXCEL -> R.string.open_excel
    OpenPreset.POWERPOINT -> R.string.open_powerpoint
    OpenPreset.EPUB -> R.string.open_epub
    OpenPreset.APK -> R.string.open_apk
    OpenPreset.TORRENT -> R.string.open_torrent
    OpenPreset.MARKDOWN -> R.string.open_markdown
    OpenPreset.CSV -> R.string.open_csv
    OpenPreset.JSON -> R.string.open_json
    OpenPreset.XML -> R.string.open_xml
    OpenPreset.SVG -> R.string.open_svg
    OpenPreset.GIF -> R.string.open_gif
    OpenPreset.IMAGE -> R.string.open_image
    OpenPreset.VIDEO -> R.string.open_video
    OpenPreset.AUDIO -> R.string.open_audio
    OpenPreset.TEXT -> R.string.open_text
    OpenPreset.ARCHIVE -> R.string.open_archive
    OpenPreset.MAGNET -> R.string.open_magnet
    OpenPreset.GEO -> R.string.open_geo
    OpenPreset.MAILTO -> R.string.open_mailto
    OpenPreset.TEL -> R.string.open_tel
    OpenPreset.SMS -> R.string.open_sms
    OpenPreset.CUSTOM_1 -> R.string.open_custom_1
    OpenPreset.CUSTOM_2 -> R.string.open_custom_2
    OpenPreset.CUSTOM_3 -> R.string.open_custom_3
    OpenPreset.CUSTOM_4 -> R.string.open_custom_4
    OpenPreset.CUSTOM_5 -> R.string.open_custom_5
    OpenPreset.CUSTOM_6 -> R.string.open_custom_6
    OpenPreset.CUSTOM_7 -> R.string.open_custom_7
    OpenPreset.CUSTOM_8 -> R.string.open_custom_8
}

@StringRes
internal fun OpenPreset.descriptionRes(): Int = when (this) {
    OpenPreset.BROWSER -> R.string.open_desc_browser
    OpenPreset.PDF -> R.string.open_desc_pdf
    OpenPreset.WORD -> R.string.open_desc_word
    OpenPreset.EXCEL -> R.string.open_desc_excel
    OpenPreset.POWERPOINT -> R.string.open_desc_powerpoint
    OpenPreset.EPUB -> R.string.open_desc_epub
    OpenPreset.APK -> R.string.open_desc_apk
    OpenPreset.TORRENT -> R.string.open_desc_torrent
    OpenPreset.MARKDOWN -> R.string.open_desc_markdown
    OpenPreset.CSV -> R.string.open_desc_csv
    OpenPreset.JSON -> R.string.open_desc_json
    OpenPreset.XML -> R.string.open_desc_xml
    OpenPreset.SVG -> R.string.open_desc_svg
    OpenPreset.GIF -> R.string.open_desc_gif
    OpenPreset.IMAGE -> R.string.open_desc_image
    OpenPreset.VIDEO -> R.string.open_desc_video
    OpenPreset.AUDIO -> R.string.open_desc_audio
    OpenPreset.TEXT -> R.string.open_desc_text
    OpenPreset.ARCHIVE -> R.string.open_desc_archive
    OpenPreset.MAGNET -> R.string.open_desc_magnet
    OpenPreset.GEO -> R.string.open_desc_geo
    OpenPreset.MAILTO -> R.string.open_desc_mailto
    OpenPreset.TEL -> R.string.open_desc_tel
    OpenPreset.SMS -> R.string.open_desc_sms
    else -> R.string.open_user_defined
}

@Composable
internal fun OpenTypeConfig.localizedTitle(preset: OpenPreset): String =
    customDefinitions[preset]?.title ?: stringResource(preset.titleRes())
