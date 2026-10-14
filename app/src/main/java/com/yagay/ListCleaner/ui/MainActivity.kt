package com.yagay.ListCleaner.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import com.yagay.ListCleaner.data.BackupImportPreview
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
        enableEdgeToEdge()
        setContent {
            var colorMode by remember { mutableStateOf(UiAppearanceStore.readColorMode(this@MainActivity)) }
            val dark = colorMode.usesDarkTheme(isSystemInDarkTheme())
            SideEffect {
                // Edge-to-edge system bar icons must match the app's forced theme, not just the OS.
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            ListCleanerUiTheme(colorMode) {
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

    @Composable
    private fun ListCleanerScreen(
        colorMode: UiColorMode,
        onColorModeChange: (UiColorMode) -> Unit,
        vm: MainViewModel = viewModel()
    ) {
        val state by vm.state.collectAsStateWithLifecycle()
        var searchExpanded by rememberSaveable { mutableStateOf(false) }
        // The merged cleanup tab hosts both rule and Root component editors.
        var componentModeActive by remember { mutableStateOf(false) }
        var restartPromptDismissed by rememberSaveable { mutableStateOf(false) }
        var pendingImport by remember { mutableStateOf<Pair<String, BackupImportPreview>?>(null) }
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
                        vm.setDestination(Destination.DASHBOARD)
                    }) {
                        AdaptiveText(stringResource(R.string.restart_required_open_status))
                    }
                }
            )
        }
        pendingImport?.let { (content, preview) ->
            AdaptiveAlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { AdaptiveText(stringResource(R.string.backup_import_confirm_title)) },
                text = {
                    AdaptiveText(stringResource(
                        R.string.backup_import_confirm_summary,
                        preview.version, preview.total, preview.added, preview.removed
                    ))
                },
                confirmButton = {
                    AdaptiveTextButton(onClick = {
                        pendingImport = null
                        lifecycleScope.launch {
                            try {
                                val previous = withContext(Dispatchers.IO) { vm.exportJson() }
                                withContext(Dispatchers.IO) { vm.importJson(content) }
                                val action = backupSnackbar.showSnackbar(
                                    getString(R.string.backup_restored),
                                    actionLabel = getString(R.string.backup_undo),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long,
                                )
                                if (action == SnackbarResult.ActionPerformed) {
                                    withContext(Dispatchers.IO) { vm.importJson(previous) }
                                    toast(getString(R.string.backup_undo_done))
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                Log.e(TAG, "Backup import failed", failure)
                                toast(getString(R.string.backup_restore_failed), true)
                            }
                        }
                    }) { AdaptiveText(stringResource(R.string.backup_import_apply)) }
                },
                dismissButton = {
                    AdaptiveTextButton(onClick = { pendingImport = null }) {
                        AdaptiveText(stringResource(R.string.common_cancel))
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
        BackHandler(enabled = !searchExpanded && (state.filter != null || state.destination != Destination.RULES)) {
            when {
                state.destination != Destination.RULES && state.destination != Destination.PRIORITY ->
                    vm.setDestination(Destination.RULES)
                vm.ruleOpenPreset.value != null -> vm.setRuleOpenPreset(null)
                vm.ruleBrowserHost.value != null -> vm.setRuleBrowserHost(null)
                state.filter != null -> vm.setFilter(null)
                else -> vm.setDestination(Destination.RULES)
            }
        }
        LaunchedEffect(state.destination) {
            if (state.destination == Destination.DASHBOARD || state.destination == Destination.SETTINGS) closeSearch()
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
                        output.bufferedWriter().use { it.write(vm.exportJson()) }
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
                    val preview = withContext(Dispatchers.IO) { vm.previewImportJson(content) }
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

        val onRefresh: () -> Unit = {
            when (state.destination) {
                Destination.RULES -> {
                    if (componentModeActive) vm.refreshComponents()
                    else vm.refresh(forceCatalog = true)
                }
                Destination.PRIORITY -> vm.refresh(forceCatalog = true)
                Destination.TILES -> vm.refreshComponents()
                Destination.DASHBOARD -> vm.refreshModuleStatus()
                Destination.SETTINGS -> vm.refreshModuleStatus()
            }
        }
        val pageContent: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (state.destination) {
                    Destination.RULES -> RulesTab(
                        state, vm,
                        searchExpanded = searchExpanded,
                        onComponentModeChanged = { componentModeActive = it }
                    )
                    Destination.PRIORITY -> PriorityTab(state, vm)
                    Destination.TILES -> RootComponentsScreen(state, vm)
                    Destination.DASHBOARD -> DashboardTabContent(
                        state,
                        vm,
                        collectingDiagnostics,
                        {
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                            diagnosticExport.launch("ListCleaner-diagnostic-$stamp.zip")
                        },
                        { fileCheck.launch(arrayOf("*/*")) }
                    )
                    Destination.SETTINGS -> SettingsTabContent(
                        state,
                        vm,
                        { restore.launch(arrayOf("application/json", "text/plain")) },
                        { export.launch("ListCleaner-backup.json") },
                        colorMode = colorMode,
                        onColorModeChange = onColorModeChange
                    )
                }
            }        }
        
        Scaffold(
            snackbarHost = { AdaptiveSnackbarHost(backupSnackbar) },
            topBar = {
                MainToolbar(
                    state.query,
                    searchExpanded,
                    vm::setQuery,
                    { searchExpanded = true },
                    closeSearch,
                    onRefresh,
                    { restore.launch(arrayOf("application/json", "text/plain")) },
                    { export.launch("ListCleaner-backup.json") },
                    showSearch = state.destination != Destination.DASHBOARD && state.destination != Destination.SETTINGS
                )
            },
            bottomBar = {
                NavigationBar {
                    Destination.entries.filter { it != Destination.TILES }.forEach { dest ->
                        NavigationBarItem(
                            selected = state.destination == dest ||
                                (dest == Destination.RULES && state.destination == Destination.TILES),
                            onClick = { vm.setDestination(dest) },
                            colors = MaterialActionColors.navigation(),
                            icon = { AdaptiveIcon(dest.icon, null) },
                            label = { AdaptiveText(stringResource(dest.labelRes())) }
                        )
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