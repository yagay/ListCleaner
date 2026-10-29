package com.yagay.ListCleaner.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind

/** Consistent semantic icons for the two navigation levels.
 * All group/type values use a single mapping across Cleanup and Ordering.
 * No decorative duplicate folder/grid icon per row; no impact on rule identities.
 */
internal fun EntryGroup.hierarchyIcon(): ImageVector = when (this) {
    EntryGroup.SHARE -> Icons.Rounded.Share
    EntryGroup.OPEN -> Icons.Rounded.OpenInNew
    EntryGroup.FILES -> Icons.Rounded.FolderOpen
    EntryGroup.DESKTOP -> Icons.Rounded.DesktopWindows
    EntryGroup.INPUT -> Icons.Rounded.Keyboard
    EntryGroup.SYSTEM -> Icons.Rounded.Security
    EntryGroup.PERSONALIZATION -> Icons.Rounded.Palette
}

internal fun IntentKind.hierarchyIcon(): ImageVector = when (this) {
    IntentKind.SHARE -> Icons.Rounded.Share
    IntentKind.SHARE_MULTIPLE -> Icons.Rounded.Collections
    IntentKind.DIRECT_SHARE -> Icons.Rounded.People
    IntentKind.SEND_TO -> Icons.Rounded.Send
    IntentKind.OPEN -> Icons.Rounded.OpenInNew
    IntentKind.BROWSER -> Icons.Rounded.Language
    IntentKind.DEEP_LINK -> Icons.Rounded.Link
    IntentKind.DIAL -> Icons.Rounded.Call
    IntentKind.GET_CONTENT -> Icons.Rounded.AttachFile
    IntentKind.OPEN_DOCUMENT -> Icons.Rounded.Description
    IntentKind.CREATE_DOCUMENT -> Icons.Rounded.NoteAdd
    IntentKind.CAPTURE_IMAGE -> Icons.Rounded.PhotoCamera
    IntentKind.CAPTURE_VIDEO -> Icons.Rounded.Videocam
    IntentKind.RECORD_AUDIO -> Icons.Rounded.Mic
    IntentKind.DOCUMENT_PROVIDER -> Icons.Rounded.FolderShared
    IntentKind.DOCUMENT_ROOT -> Icons.Rounded.FolderOpen
    IntentKind.PHOTO_SOURCE -> Icons.Rounded.PhotoLibrary
    IntentKind.HOME -> Icons.Rounded.Home
    IntentKind.ASSISTANT -> Icons.Rounded.Assistant
    IntentKind.LAUNCHER_SHORTCUT -> Icons.Rounded.TouchApp
    IntentKind.SHORTCUT_ITEM -> Icons.Rounded.FlashOn
    IntentKind.PROCESS_TEXT -> Icons.Rounded.TextFields
    IntentKind.INPUT_METHOD -> Icons.Rounded.Keyboard
    IntentKind.AUTOFILL -> Icons.Rounded.Input
    IntentKind.CREDENTIAL_PROVIDER -> Icons.Rounded.VpnKey
    IntentKind.NOTIFICATION_LISTENER -> Icons.Rounded.Notifications
    IntentKind.ACCESSIBILITY -> Icons.Rounded.AccessibilityNew
    IntentKind.VPN -> Icons.Rounded.VpnLock
    IntentKind.PRINT -> Icons.Rounded.Print
    IntentKind.NFC_HCE -> Icons.Rounded.Nfc
    IntentKind.CALL_SCREENING -> Icons.Rounded.PhoneDisabled
    IntentKind.WALLPAPER -> Icons.Rounded.Wallpaper
    IntentKind.DREAM -> Icons.Rounded.NightsStay
}

internal fun CleanupKind.hierarchyIcon(): ImageVector = when (this) {
    CleanupKind.LAUNCHER -> Icons.Rounded.Home
    CleanupKind.TILE -> Icons.Rounded.GridView
    CleanupKind.SHORTCUT -> Icons.Rounded.Link
    CleanupKind.WIDGET -> Icons.Rounded.Widgets
    CleanupKind.CONTROLS -> Icons.Rounded.Extension
}

internal val rootHierarchyIcon: ImageVector get() = Icons.Rounded.Extension

/** All Root components is a browse-all action, not the Tile category. */
internal val allRootComponentsIcon: ImageVector get() = Icons.Rounded.Apps
