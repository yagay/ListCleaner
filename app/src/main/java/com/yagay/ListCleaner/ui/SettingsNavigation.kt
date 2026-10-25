package com.yagay.ListCleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ListCleaner.R

/** Navigation is separate from page content. The title/back row never scrolls away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTabContent(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit,
    onInspectFile: () -> Unit,
    colorMode: UiColorMode,
    onColorModeChange: (UiColorMode) -> Unit,
    openModuleStatus: Boolean = false,
    onModuleStatusOpened: () -> Unit = {},
) {
    var selectedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var showScopeDetails by remember { mutableStateOf(false) }
    var showAppScopePicker by remember { mutableStateOf(false) }
    val savedScrollStates = rememberSaveableStateHolder()

    LaunchedEffect(openModuleStatus) {
        if (openModuleStatus) {
            selectedSection = SettingsSection.COMPATIBILITY
            showScopeDetails = true
            vm.refreshModuleStatus()
            onModuleStatusOpened()
        }
    }
    BackHandler(enabled = selectedSection != null && !showScopeDetails && !showAppScopePicker) {
        selectedSection = null
    }
    if (showScopeDetails) {
        ScopeDialog(state.module, vm::requestScope, vm::refreshModuleStatus) {
            showScopeDetails = false
        }
    }
    if (showAppScopePicker) {
        AppScopePickerDialog(
            selected = state.hiddenFromApps,
            onSelectedChange = vm::setHiddenFromApps
        ) { showAppScopePicker = false }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { AdaptiveText(stringResource(selectedSection?.titleRes ?: R.string.nav_settings)) },
            navigationIcon = {
                if (selectedSection != null) {
                    AdaptiveBackButton(onClick = { selectedSection = null })
                }
            }
        )
        if (selectedSection == null) {
            SettingsCenterHome(onSelect = { selectedSection = it })
        } else {
            // Stable keys preserve the scroll position of each setting area on navigation.
            val page = requireNotNull(selectedSection)
            savedScrollStates.SaveableStateProvider(page.name) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = MaterialUiTokens.pageVertical),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = page.name) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (page) {
                                SettingsSection.APPEARANCE -> {
                                    SettingsAppearanceControls(colorMode, onColorModeChange)
                                    SettingsLanguageControls()
                                }
                                SettingsSection.LISTS -> SettingsListControls()
                                SettingsSection.SCANNING -> SettingsScanControls(vm)
                                SettingsSection.COMPATIBILITY -> CompatibilitySettings(
                                    state, vm,
                                    onScope = { showScopeDetails = true; vm.refreshModuleStatus() },
                                    onAppPicker = { showAppScopePicker = true }
                                )
                                SettingsSection.BACKUP -> BackupSettings(
                                    onRestore, onExport, onColorModeChange
                                )
                                SettingsSection.ADVANCED -> DiagnosticsSettings(
                                    state, vm, collectingDiagnostics,
                                    onCollectDiagnostics, onInspectFile
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
