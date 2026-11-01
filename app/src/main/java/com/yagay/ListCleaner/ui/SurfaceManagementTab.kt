package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import androidx.compose.ui.graphics.vector.ImageVector
import com.yagay.ListCleaner.domain.kinds
import androidx.compose.material3.MaterialTheme

/**
 * Bottom-navigation Lists tab: Global Mode management only.
 *
 * No active notification, stale-rule or AOSP surface editor is exposed here.
 * The underlying cleanup engines and stored rules remain untouched.
 * Effective modes control system Entry filtering, never the manager's UiFilter.
 */
@Composable
internal fun SurfaceManagementTab(state: MainState, vm: MainViewModel) {
    val userId by vm.selectedSpace.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val entryModeItems = CleanupCategoryRegistry.sorted(context, CleanupCategoryRegistry.lsposedCategories())
    val rootModeItems = CleanupCategoryRegistry.sorted(context, CleanupCategoryRegistry.rootCategories())
    val savedManagedDisabled = UiPreferencesRuntime.current.disabledManagedSurfaces
    var draftManagedDisabled by remember(userId, savedManagedDisabled) { mutableStateOf(savedManagedDisabled) }
    val savedRootDisabled = UiPreferencesRuntime.current.disabledRootKinds
    var draftRootDisabled by remember(userId, savedRootDisabled) { mutableStateOf(savedRootDisabled) }
    // Only one inline mode editor at a time, and no modes are written
    // until its explicit confirmation button is pressed.
    var editingModeKey by rememberSaveable(userId) { mutableStateOf<String?>(null) }

    val savedDisabled = remember(state.disabledEntryKinds, state.disabledEntryGroups) {
        state.disabledEntryKinds + state.disabledEntryGroups.flatMap { it.kinds() }
    }
    var draftDisabled by remember(userId, savedDisabled) { mutableStateOf(savedDisabled) }
    val hasPendingChanges = draftDisabled != savedDisabled || draftRootDisabled != savedRootDisabled || draftManagedDisabled != savedManagedDisabled

    Scaffold(
        bottomBar = {
            if (hasPendingChanges) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdaptiveTextButton(onClick = { draftDisabled = savedDisabled; draftRootDisabled = savedRootDisabled; draftManagedDisabled = savedManagedDisabled }) {
                        AdaptiveText(stringResource(R.string.common_cancel))
                    }
                    AdaptiveButton(onClick = {
                        // Convert legacy group disables to explicit per-kind state so
                        // enabling one member cannot be overridden by its old group flag.
                        vm.setDisabledEntryKinds(draftDisabled)
                        if (state.disabledEntryGroups.isNotEmpty()) vm.setDisabledEntryGroups(emptySet())
                        if (draftRootDisabled != savedRootDisabled || draftManagedDisabled != savedManagedDisabled) UiPreferencesStore.write(context, UiPreferencesRuntime.current.copy(disabledRootKinds = draftRootDisabled, disabledManagedSurfaces = draftManagedDisabled))
                    }) {
                        AdaptiveText(stringResource(R.string.lc_mode_confirm_apply))
                    }
                }
            }
        },
    ) { scaffoldPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        contentPadding = PaddingValues(vertical = MaterialUiTokens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "global-mode-default") {
            GlobalModeSettingsRow(
                title = stringResource(R.string.lc_mode_default_title),
                selectedMode = stringResource(state.displayMode.titleRes()),
                expanded = editingModeKey == GLOBAL_MODE_KEY,
                onClick = {
                    editingModeKey = if (editingModeKey == GLOBAL_MODE_KEY) null else GLOBAL_MODE_KEY
                },
                modeMenu = {
                    EntryModeFilterMenu(
                        state = state, vm = vm, kind = null,
                        expanded = editingModeKey == GLOBAL_MODE_KEY,
                        onDismiss = { editingModeKey = null },
                    )
                },
            )
        }

        item(key = "modes-entry-heading") { FlatPopupHeader(stringResource(R.string.lc_mode_entry_heading)) }
        entryModeItems.forEach { category ->
            when (category) {
                is CleanupCategoryId.Entry -> {
                    val kind = category.kind
            item(key = "global-mode-kind-${kind.name}") {
                        val isDisabled = kind in draftDisabled
                        GlobalModeSettingsRow(
                            title = stringResource(kind.titleRes()),
                            leadingIcon = CleanupCategoryId.Entry(kind).icon(),
                            description = stringResource(kind.modeDescriptionRes()),
                            selectedMode = stringResource(
                                if (kind in (draftDisabled - savedDisabled)) R.string.lc_mode_pending_disable
                                else if (kind in (savedDisabled - draftDisabled)) R.string.lc_mode_pending_enable
                                else if (isDisabled) R.string.lc_mode_disabled else R.string.lc_mode_enabled
                            ) + " · " + (state.modeOverrides[kind]?.let {
                                stringResource(it.titleRes())
                            } ?: stringResource(
                                R.string.lc_mode_follow_global_value,
                                stringResource(state.displayMode.titleRes()),
                            )),
                            expanded = editingModeKey == kind.name,
                            onClick = {
                                editingModeKey = if (editingModeKey == kind.name) null else kind.name
                            },
                            modeMenu = {
                                EntryModeFilterMenu(
                                    state = state, vm = vm, kind = kind,
                                    expanded = editingModeKey == kind.name,
                                    onDismiss = { editingModeKey = null },
                                )
                            },
                            enabled = !isDisabled,
                            onEnabledChange = { enabled ->
                                draftDisabled = if (enabled) draftDisabled - kind else draftDisabled + kind
                            },
                        )
            }
                }
                is CleanupCategoryId.Managed -> {
                    val managedCategory = category
            val managedSurface = managedCategory.surface
            item(key = "modes-managed-${managedSurface.name}") {
                val disabled = managedSurface.name in draftManagedDisabled
                GlobalModeSettingsRow(
                    title = stringResource(managedSurface.titleRes),
                    leadingIcon = managedCategory.icon(),
                    description = stringResource(if (managedSurface == ManagedComponentSurface.SETTINGS_APPS) R.string.lc_mode_managed_settings_desc else R.string.lc_mode_managed_notifications_desc),
                    selectedMode = stringResource(if (disabled) R.string.lc_mode_disabled else R.string.lc_mode_enabled),
                    expanded = false, onClick = {}, modeMenu = {},
                    enabled = !disabled,
                    onEnabledChange = { enabled ->
                        draftManagedDisabled = if (enabled) draftManagedDisabled - managedSurface.name else draftManagedDisabled + managedSurface.name
                    },
                )
            }
                }
                else -> Unit
            }
        }
        item(key = "modes-root-heading") { FlatPopupHeader(stringResource(R.string.lc_mode_root_heading)) }
        // Root section lists only Root component kinds. Hook-backed managed surfaces stay in LSPosed.
        rootModeItems.filterIsInstance<CleanupCategoryId.Root>().forEach { rootCategory ->
            val kind = rootCategory.kind

            item(key = "modes-root-${kind.name}") {
                val disabled = kind in draftRootDisabled
                GlobalModeSettingsRow(
                    title = stringResource(kind.titleRes()),
                    leadingIcon = CleanupCategoryId.Root(kind).icon(),
                    description = stringResource(kind.rootModeDescriptionRes()),
                    selectedMode = stringResource(if (disabled) R.string.lc_mode_disabled else R.string.lc_mode_enabled),
                    expanded = false,
                    onClick = {},
                    modeMenu = {},
                    enabled = !disabled,
                    onEnabledChange = { enabled ->
                        draftRootDisabled = if (enabled) draftRootDisabled - kind else draftRootDisabled + kind
                    },
                )
            }
        }
    }
    }

}

/** The same anchored Filter-style menu opens from each Lists mode row. */
private const val GLOBAL_MODE_KEY = "GLOBAL"

@Composable
private fun GlobalModeSettingsRow(
    title: String,
    selectedMode: String,
    description: String? = null,
    expanded: Boolean,
    onClick: () -> Unit,
    modeMenu: @Composable () -> Unit,
    enabled: Boolean? = null,
    leadingIcon: ImageVector? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        MaterialSettingsRow(
            title = title,
            leadingIcon = leadingIcon,
            supportingText = if (description == null) selectedMode else "$description\n$selectedMode",
            onClick = onClick,
        ) {
            if (enabled != null && onEnabledChange != null) {
                AdaptiveSwitch(checked = enabled, onCheckedChange = onEnabledChange)
            } else {
                AdaptiveIcon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    title,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The popup is anchored to this particular row, not a full-screen dialog.
        modeMenu()
    }
}

/** Short, localized explanations; enabled state and effective mode remain separate. */
private fun IntentKind.modeDescriptionRes(): Int = when (this) {
    IntentKind.SHARE -> R.string.lc_mode_desc_share
    IntentKind.SHARE_MULTIPLE -> R.string.lc_mode_desc_share_multiple
    IntentKind.DIRECT_SHARE -> R.string.lc_mode_desc_direct_share
    IntentKind.SEND_TO -> R.string.lc_mode_desc_send_to
    IntentKind.OPEN -> R.string.lc_mode_desc_open
    IntentKind.BROWSER -> R.string.lc_mode_desc_browser
    IntentKind.DEEP_LINK -> R.string.lc_mode_desc_deep_link
    IntentKind.DIAL -> R.string.lc_mode_desc_dial
    IntentKind.EDIT -> R.string.lc_mode_desc_edit
    IntentKind.GET_CONTENT -> R.string.lc_mode_desc_get_content
    IntentKind.PICK -> R.string.lc_mode_desc_pick
    IntentKind.OPEN_DOCUMENT -> R.string.lc_mode_desc_open_document
    IntentKind.CREATE_DOCUMENT -> R.string.lc_mode_desc_create_document
    IntentKind.CAPTURE_IMAGE -> R.string.lc_mode_desc_capture_image
    IntentKind.CAPTURE_VIDEO -> R.string.lc_mode_desc_capture_video
    IntentKind.RECORD_AUDIO -> R.string.lc_mode_desc_record_audio
    IntentKind.DOCUMENT_PROVIDER -> R.string.lc_mode_desc_document_provider
    IntentKind.DOCUMENT_ROOT -> R.string.lc_mode_desc_document_root
    IntentKind.PHOTO_SOURCE -> R.string.lc_mode_desc_photo_source
    IntentKind.HOME -> R.string.lc_mode_desc_home
    IntentKind.ASSISTANT -> R.string.lc_mode_desc_assistant
    IntentKind.LAUNCHER_SHORTCUT -> R.string.lc_mode_desc_launcher_shortcut
    IntentKind.SHORTCUT_ITEM -> R.string.lc_mode_desc_shortcut_item
    IntentKind.PROCESS_TEXT -> R.string.lc_mode_desc_process_text
    IntentKind.INPUT_METHOD -> R.string.lc_mode_desc_input_method
    IntentKind.AUTOFILL -> R.string.lc_mode_desc_autofill
    IntentKind.CREDENTIAL_PROVIDER -> R.string.lc_mode_desc_credential_provider
    IntentKind.NOTIFICATION_LISTENER -> R.string.lc_mode_desc_notification_listener
    IntentKind.ACCESSIBILITY -> R.string.lc_mode_desc_accessibility
    IntentKind.VPN -> R.string.lc_mode_desc_vpn
    IntentKind.PRINT -> R.string.lc_mode_desc_print
    IntentKind.NFC_HCE -> R.string.lc_mode_desc_nfc_hce
    IntentKind.CALL_SCREENING -> R.string.lc_mode_desc_call_screening
    IntentKind.WALLPAPER -> R.string.lc_mode_desc_wallpaper
    IntentKind.DREAM -> R.string.lc_mode_desc_dream
}

/** These descriptions explain what each Root category manages, not visibility. */
private fun CleanupKind.rootModeDescriptionRes(): Int = when (this) {
    CleanupKind.LAUNCHER -> R.string.lc_mode_root_desc_launcher
    CleanupKind.SHORTCUT -> R.string.lc_mode_root_desc_shortcut
    CleanupKind.WIDGET -> R.string.lc_mode_root_desc_widget
    CleanupKind.TILE -> R.string.lc_mode_root_desc_tile
    CleanupKind.CONTROLS -> R.string.lc_mode_root_desc_controls
    CleanupKind.AUTHENTICATOR -> R.string.lc_mode_root_desc_authenticator
}
