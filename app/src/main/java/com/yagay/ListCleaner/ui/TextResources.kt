package com.yagay.ListCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.EntryGroup
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
internal fun AppTypeFilter.titleRes(): Int = when (this) {
    AppTypeFilter.ALL -> R.string.common_all
    AppTypeFilter.USER -> R.string.filter_user_apps
    AppTypeFilter.SYSTEM -> R.string.filter_system_apps
}

@StringRes
internal fun UiFilter.titleRes(): Int = when (this) {
    UiFilter.ALL -> R.string.common_all
    UiFilter.HIDE_SELECTED -> R.string.filter_unselected_rules
    UiFilter.SHOW_SELECTED -> R.string.filter_selected_rules
    UiFilter.LOCKED -> R.string.filter_locked
}

@StringRes
internal fun DisplayMode.titleRes(): Int = when (this) {
    DisplayMode.HIDE_SELECTED -> R.string.display_hide_selected
    DisplayMode.SHOW_SELECTED -> R.string.display_show_selected
    DisplayMode.SHOW_ALL -> R.string.display_show_all
}

@StringRes
internal fun EntryGroup.titleRes(): Int = when (this) {
    EntryGroup.SHARE -> R.string.entry_group_share
    EntryGroup.OPEN -> R.string.entry_group_open
    EntryGroup.DESKTOP -> R.string.entry_group_desktop
    EntryGroup.ADVANCED -> R.string.entry_group_advanced
}

@StringRes
internal fun IntentKind.titleRes(): Int = when (this) {
    IntentKind.SHARE -> R.string.intent_share
    IntentKind.SHARE_MULTIPLE -> R.string.intent_share_multiple
    IntentKind.DIRECT_SHARE -> R.string.intent_direct_share
    IntentKind.SEND_TO -> R.string.intent_send_to
    IntentKind.OPEN -> R.string.intent_open
    IntentKind.BROWSER -> R.string.intent_browser
    IntentKind.DEEP_LINK -> R.string.intent_deep_link
    IntentKind.DIAL -> R.string.intent_dial
    IntentKind.GET_CONTENT -> R.string.intent_get_content
    IntentKind.OPEN_DOCUMENT -> R.string.intent_open_document
    IntentKind.CREATE_DOCUMENT -> R.string.intent_create_document
    IntentKind.CAPTURE_IMAGE -> R.string.intent_capture_image
    IntentKind.CAPTURE_VIDEO -> R.string.intent_capture_video
    IntentKind.RECORD_AUDIO -> R.string.intent_record_audio
    IntentKind.PROCESS_TEXT -> R.string.intent_process_text
    IntentKind.HOME -> R.string.intent_home
    IntentKind.ASSISTANT -> R.string.intent_assistant
    IntentKind.LAUNCHER_SHORTCUT -> R.string.intent_launcher_shortcut
    IntentKind.SHORTCUT_ITEM -> R.string.intent_shortcut_item
    IntentKind.DOCUMENT_PROVIDER -> R.string.intent_document_provider
    IntentKind.INPUT_METHOD -> R.string.intent_input_method
    IntentKind.AUTOFILL -> R.string.intent_autofill
    IntentKind.CREDENTIAL_PROVIDER -> R.string.intent_credential_provider
    IntentKind.NOTIFICATION_LISTENER -> R.string.intent_notification_listener
    IntentKind.ACCESSIBILITY -> R.string.intent_accessibility
    IntentKind.VPN -> R.string.intent_vpn
    IntentKind.PRINT -> R.string.intent_print
    IntentKind.WALLPAPER -> R.string.intent_wallpaper
    IntentKind.DREAM -> R.string.intent_dream
    IntentKind.NFC_HCE -> R.string.intent_nfc_hce
    IntentKind.CALL_SCREENING -> R.string.intent_call_screening
}

@StringRes
internal fun IntentKind.descriptionRes(): Int = when (this) {
    IntentKind.SHARE -> R.string.intent_desc_share
    IntentKind.SHARE_MULTIPLE -> R.string.intent_desc_share_multiple
    IntentKind.DIRECT_SHARE -> R.string.intent_desc_direct_share
    IntentKind.SEND_TO -> R.string.intent_desc_send_to
    IntentKind.OPEN -> R.string.intent_desc_open
    IntentKind.BROWSER -> R.string.intent_desc_browser
    IntentKind.DEEP_LINK -> R.string.intent_desc_deep_link
    IntentKind.DIAL -> R.string.intent_desc_dial
    IntentKind.GET_CONTENT -> R.string.intent_desc_get_content
    IntentKind.OPEN_DOCUMENT -> R.string.intent_desc_open_document
    IntentKind.CREATE_DOCUMENT -> R.string.intent_desc_create_document
    IntentKind.CAPTURE_IMAGE -> R.string.intent_desc_capture_image
    IntentKind.CAPTURE_VIDEO -> R.string.intent_desc_capture_video
    IntentKind.RECORD_AUDIO -> R.string.intent_desc_record_audio
    IntentKind.PROCESS_TEXT -> R.string.intent_desc_process_text
    IntentKind.HOME -> R.string.intent_desc_home
    IntentKind.ASSISTANT -> R.string.intent_desc_assistant
    IntentKind.LAUNCHER_SHORTCUT -> R.string.intent_desc_launcher_shortcut
    IntentKind.SHORTCUT_ITEM -> R.string.intent_desc_shortcut_item
    IntentKind.DOCUMENT_PROVIDER -> R.string.intent_desc_document_provider
    IntentKind.INPUT_METHOD -> R.string.intent_desc_input_method
    IntentKind.AUTOFILL -> R.string.intent_desc_autofill
    IntentKind.CREDENTIAL_PROVIDER -> R.string.intent_desc_credential_provider
    IntentKind.NOTIFICATION_LISTENER -> R.string.intent_desc_notification_listener
    IntentKind.ACCESSIBILITY -> R.string.intent_desc_accessibility
    IntentKind.VPN -> R.string.intent_desc_vpn
    IntentKind.PRINT -> R.string.intent_desc_print
    IntentKind.WALLPAPER -> R.string.intent_desc_wallpaper
    IntentKind.DREAM -> R.string.intent_desc_dream
    IntentKind.NFC_HCE -> R.string.intent_desc_nfc_hce
    IntentKind.CALL_SCREENING -> R.string.intent_desc_call_screening
}

@StringRes
internal fun VisibilityScope.titleRes(): Int = when (this) {
    VisibilityScope.ALL -> R.string.common_all
    VisibilityScope.SHARE -> R.string.intent_share
    VisibilityScope.SHARE_MULTIPLE -> R.string.intent_share_multiple
    VisibilityScope.SEND_TO -> R.string.intent_send_to
    VisibilityScope.OPEN -> R.string.intent_open
    VisibilityScope.BROWSER -> R.string.intent_browser
    VisibilityScope.DEEP_LINK -> R.string.intent_deep_link
    VisibilityScope.DIAL -> R.string.intent_dial
    VisibilityScope.GET_CONTENT -> R.string.intent_get_content
    VisibilityScope.CAPTURE_IMAGE -> R.string.intent_capture_image
    VisibilityScope.CAPTURE_VIDEO -> R.string.intent_capture_video
    VisibilityScope.RECORD_AUDIO -> R.string.intent_record_audio
    VisibilityScope.PROCESS_TEXT -> R.string.intent_process_text
}

@StringRes
internal fun CleanupKind.titleRes(): Int = when (this) {
    CleanupKind.LAUNCHER -> R.string.cleanup_launcher
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
