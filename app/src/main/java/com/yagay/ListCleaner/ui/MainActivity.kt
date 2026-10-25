package com.yagay.ListCleaner.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.SpaceBackupInspection
import com.yagay.ListCleaner.data.readBackupText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedMode = UiAppearanceStore.readColorMode(this)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialDark = savedMode.usesDarkTheme(systemDark)
        val transparent = android.graphics.Color.TRANSPARENT
        val barStyle = if (initialDark) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent)
        // Draw behind both bars on every supported Android version, including 3-button nav.
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        UiPreferencesRuntime.apply(UiPreferencesStore.read(this))
        setContent {
            var colorMode by remember { mutableStateOf(savedMode) }
            val dark = colorMode.usesDarkTheme(isSystemInDarkTheme())
            SideEffect { syncSystemBars(dark) }
            ListCleanerUiTheme(colorMode) {
                // The same dynamic surface is visible through both transparent system bars.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    ListCleanerScreen(
                        colorMode = colorMode,
                        onColorModeChange = { selected ->
                            if (colorMode != selected) {
                                UiAppearanceStore.writeColorMode(this@MainActivity, selected)
                                colorMode = selected
                            }
                        }
                    )
                }
            }
        }
    }

    /** Update system bar contrast whenever the user switches day/night/system mode. */
    @Suppress("DEPRECATION")
    private fun syncSystemBars(dark: Boolean) {
        // Disable Android's grey 3-button navigation scrim: the app draws its own
        // opaque Material surface behind the transparent system navigation area.
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    @Composable
    private fun ListCleanerScreen(
        colorMode: UiColorMode,
        onColorModeChange: (UiColorMode) -> Unit,
        vm: MainViewModel = viewModel()
    ) {
        val state by vm.state.collectAsStateWithLifecycle()
        val spaces by vm.spaces.collectAsStateWithLifecycle()
        val selectedSpace by vm.selectedSpace.collectAsStateWithLifecycle()
        val componentBusy by vm.componentBusy.collectAsStateWithLifecycle()
        val pendingNavigation = remember { PendingNavigationGuard() }
        PendingNavigationDialog(pendingNavigation)
        LaunchedEffect(componentBusy) { pendingNavigation.observeRootBusy(componentBusy) }
        var searchExpanded by rememberSaveable { mutableStateOf(false) }
        // The merged cleanup tab hosts both rule and Root component editors.
        var componentModeActive by remember { mutableStateOf(false) }
        var openModuleStatus by remember { mutableStateOf(false) }
        var restartPromptDismissed by rememberSaveable { mutableStateOf(false) }
        var pendingImport by remember { mutableStateOf<Pair<String, SpaceBackupInspection>?>(null) }
        var showExportDialog by rememberSaveable { mutableStateOf(false) }
        var exportSpaceSelection by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
        var exportRootReference by rememberSaveable { mutableStateOf(true) }
        val backupSnackbar = remember { SnackbarHostState() }
        val collectingDiagnostics by vm.collectingDiagnostics.collectAsStateWithLifecycle()
        val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
        LaunchedEffect(exportMessage) {
            exportMessage?.let { toast(it, true); vm.clearExportMessage() }
        }
        LaunchedEffect(state.module.outdated) {
            if (!state.module.outdated) restartPromptDismissed = false
        }
        if (state.module.outdated && !restartPromptDismissed) {
            AdaptiveAlertDialog(
                onDismissRequest = { restartPromptDismissed = true },
                title = { AdaptiveText(stringResource(R.string.restart_required_title)) },
                text = { AdaptiveText(stringResource(R.string.restart_required_message)) },
                confirmButton = {
                    AdaptiveTextButton(onClick = { restartPromptDismissed = true }) {
                        AdaptiveText(stringResource(R.string.common_ok))
                    }
                },
                dismissButton = {
                    AdaptiveTextButton(onClick = {
                        restartPromptDismissed = true
                        pendingNavigation.request { vm.setDestination(Destination.SETTINGS) }
                    }) {
                        AdaptiveText(stringResource(R.string.restart_required_open_status))
                    }
                }
            )
        }
        pendingImport?.let { (content, inspection) ->
            ImportSpaceBackupDialog(
                inspection = inspection,
                targets = spaces,
                selectedUserId = selectedSpace,
                onDismiss = { pendingImport = null },
                onImport = { mapped ->
                    pendingImport = null
                    lifecycleScope.launch {
                        try {
                            val checkpoint = withContext(Dispatchers.IO) { vm.backupCheckpoint() }
                            withContext(Dispatchers.IO) { vm.importMappedSpaces(content, mapped) }
                            val action = backupSnackbar.showSnackbar(
                                getString(R.string.backup_restored),
                                actionLabel = getString(R.string.backup_undo),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (action == SnackbarResult.ActionPerformed) {
                                withContext(Dispatchers.IO) { vm.restoreBackupCheckpoint(checkpoint) }
                                toast(getString(R.string.backup_undo_done))
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Log.e(TAG, "Multi-space backup import failed", failure)
                            toast(getString(R.string.backup_restore_failed), true)
                        }
                    }
                }
            )
        }
        val keyboard = LocalSoftwareKeyboardController.current
        val closeSearch: () -> Unit = {
            vm.setQuery("")
            searchExpanded = false
            keyboard?.hide()
        }
        BackHandler(enabled = searchExpanded, onBack = closeSearch)
        BackHandler(enabled = !searchExpanded && (
            state.filter != null || state.destination != Destination.RULES || pendingNavigation.hasChanges
        )) {
            pendingNavigation.request {
                when {
                    state.destination != Destination.RULES && state.destination != Destination.PRIORITY ->
                        vm.setDestination(Destination.RULES)
                    vm.ruleOpenPreset.value != null -> vm.setRuleOpenPreset(null)
                    vm.ruleBrowserHost.value != null -> vm.setRuleBrowserHost(null)
                    state.filter != null -> vm.setFilter(null)
                    else -> finish()
                }
            }
        }
        LaunchedEffect(state.destination) {
            if (state.destination == Destination.SETTINGS) closeSearch()
        }
        LifecycleResumeEffect(vm) {
            vm.refreshModuleStatus()
            onPauseOrDispose { }
        }

        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = contentResolver.openOutputStream(uri) ?: error(getString(R.string.backup_create_failed))
                        output.bufferedWriter().use { it.write(vm.exportSpacesJson(exportSpaceSelection.toSet(), exportRootReference)) }
                    }
                    toast(getString(R.string.backup_exported))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Backup export failed", failure)
                    toast(getString(R.string.backup_export_failed), true)
                }
            }
        }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        readLimitedText(uri, MainViewModel.MAX_BACKUP_CHARS)
                    }
                    val preview = withContext(Dispatchers.IO) { vm.inspectSpaceBackup(content) }
                    pendingImport = content to preview
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Backup restore failed", failure)
                    toast(getString(R.string.backup_restore_failed), true)
                }
            }
        }
        val diagnosticExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            vm.exportDiagnostics(uri)
        }
        val fileCheck = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::inspectFile)
        }

        if (showExportDialog) {
            ExportSpaceBackupDialog(
                spaces = spaces,
                onDismiss = { showExportDialog = false },
                onExport = { userIds, includeRootReference ->
                    showExportDialog = false
                    exportRootReference = includeRootReference
                    exportSpaceSelection = userIds.toList()
                    export.launch("ListCleaner-spaces-backup.json")
                }
            )
        }

        val onRefresh: () -> Unit = {
            when (state.destination) {
                Destination.RULES -> {
                    if (componentModeActive) vm.refreshComponents()
                    else vm.refresh(forceCatalog = true)
                }
                Destination.PRIORITY -> vm.refresh(forceCatalog = true)
                Destination.TILES -> vm.refreshComponents()
                Destination.SETTINGS -> vm.refreshModuleStatus()
            }
        }
        val pageContent: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                when (state.destination) {
                    Destination.RULES -> RulesTab(
                        state, vm,
                        searchExpanded = searchExpanded,
                        pendingNavigation = pendingNavigation,
                        onComponentModeChanged = { componentModeActive = it },
                        onOpenModuleStatus = {
                            openModuleStatus = true
                            pendingNavigation.request { vm.setDestination(Destination.SETTINGS) }
                        }
                    )
                    Destination.PRIORITY -> PriorityTab(state, vm, pendingNavigation)
                    Destination.TILES -> RootComponentsScreen(state, vm, pendingNavigation = pendingNavigation)
                    Destination.SETTINGS -> SettingsTabContent(
                        state,
                        vm,
                        { restore.launch(arrayOf("application/json", "text/plain")) },
                        { showExportDialog = true },
                        collectingDiagnostics,
                        {
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                            diagnosticExport.launch("ListCleaner-diagnostic-$stamp.zip")
                        },
                        { fileCheck.launch(arrayOf("*/*")) },
                        colorMode = colorMode,
                        onColorModeChange = onColorModeChange,
                        openModuleStatus = openModuleStatus,
                        onModuleStatusOpened = { openModuleStatus = false }
                    )
                }
            }        }
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { AdaptiveSnackbarHost(backupSnackbar) },
            topBar = {
                if (state.destination != Destination.SETTINGS) MainToolbar(
                    state.query,
                    searchExpanded,
                    vm::setQuery,
                    { searchExpanded = true },
                    closeSearch,
                    onRefresh,
                    { restore.launch(arrayOf("application/json", "text/plain")) },
                    { showExportDialog = true },
                    showSearch = state.destination != Destination.SETTINGS,
                    titleRes = when {
                        state.destination == Destination.TILES -> R.string.root_screen_title
                        state.destination == Destination.PRIORITY -> R.string.nav_priority
                        else -> R.string.app_name
                    },
                    spaces = spaces,
                    selectedSpace = selectedSpace,
                    onSpaceSelected = { target ->
                        if (!componentBusy) pendingNavigation.request {
                            closeSearch()
                            componentModeActive = false
                            vm.selectSpace(target)
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    AdaptiveHorizontalDivider(thickness = 0.5.dp)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        val underlineColor = MaterialSelectionPalette.navigationText()
                        Destination.entries.filter { it != Destination.TILES }.forEach { dest ->
                            val selected = state.destination == dest ||
                                (dest == Destination.RULES && state.destination == Destination.TILES)
                            NavigationBarItem(
                                selected = selected,
                                modifier = Modifier.drawWithContent {
                                    drawContent()
                                    if (selected) {
                                        val y = size.height - 2.dp.toPx()
                                        drawLine(
                                            color = underlineColor,
                                            start = Offset(size.width * 0.22f, y),
                                            end = Offset(size.width * 0.78f, y),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                },
                                onClick = {
                                    if (!componentBusy || state.destination != Destination.TILES && !componentModeActive) {
                                        pendingNavigation.request { vm.setDestination(dest) }
                                    }
                                },
                                colors = MaterialActionColors.navigation(),
                                icon = { AdaptiveIcon(dest.icon, null) },
                                label = { AdaptiveText(stringResource(dest.labelRes())) }
                            )
                        }
                    }
                }
            }
        ) { padding -> pageContent(padding) }
        
    }

    private fun readLimitedText(uri: Uri, maxChars: Int): String {
        val input = contentResolver.openInputStream(uri) ?: error(getString(R.string.backup_read_failed))
        return input.bufferedReader().use {
            it.readBackupText(maxChars, getString(R.string.backup_too_large))
        }
    }

    private fun toast(message: String, long: Boolean = false) =
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private companion object {
        const val TAG = "ListCleaner.MainActivity"
    }
}