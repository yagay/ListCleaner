package com.yagay.ListCleaner.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.readBackupText
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
            val colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                dynamicDarkColorScheme(this)
            } else {
                dynamicLightColorScheme(this)
            }
            MaterialTheme(colorScheme = colors) { ListCleanerScreen() }
        }
    }

    @Composable
    private fun ListCleanerScreen(vm: MainViewModel = viewModel()) {
        val state by vm.state.collectAsStateWithLifecycle()
        var searchExpanded by rememberSaveable { mutableStateOf(false) }
        val collectingDiagnostics by vm.collectingDiagnostics.collectAsStateWithLifecycle()
        val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
        LaunchedEffect(exportMessage) {
            exportMessage?.let { toast(it, true); vm.clearExportMessage() }
        }
        val keyboard = LocalSoftwareKeyboardController.current
        val closeSearch: () -> Unit = {
            vm.setQuery("")
            searchExpanded = false
            keyboard?.hide()
        }
        BackHandler(enabled = searchExpanded, onBack = closeSearch)
        LifecycleResumeEffect(vm) {
            vm.refreshModuleStatus()
            onPauseOrDispose { }
        }

        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val output = contentResolver.openOutputStream(uri) ?: error(getString(R.string.backup_create_failed))
                        output.bufferedWriter().use { it.write(vm.exportJson()) }
                    }
                }.onSuccess { toast(getString(R.string.backup_exported)) }
                    .onFailure { toast(it.message ?: getString(R.string.backup_export_failed), true) }
            }
        }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        vm.importJson(readLimitedText(uri, MainViewModel.MAX_BACKUP_CHARS))
                    }
                }.onSuccess { toast(getString(R.string.backup_restored)) }
                    .onFailure { toast(it.message ?: getString(R.string.backup_restore_failed), true) }
            }
        }
        val diagnosticExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            vm.exportDiagnostics(uri)
        }
        val fileCheck = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::inspectFile)
        }

        Scaffold(
            topBar = {
                MainToolbar(
                    state.query,
                    searchExpanded,
                    vm::setQuery,
                    { searchExpanded = true },
                    closeSearch,
                    { if (state.destination == Destination.TILES) vm.refreshComponents() else vm.refresh() },
                    { restore.launch(arrayOf("application/json", "text/plain")) },
                    { export.launch("ListCleaner-backup.json") }
                )
            },
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = state.destination == dest,
                            onClick = { vm.setDestination(dest) },
                            icon = { Icon(dest.icon, null) },
                            label = { Text(stringResource(dest.labelRes())) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (state.destination) {
                    Destination.RULES -> RulesTab(state, vm)
                    Destination.PRIORITY -> PriorityTab(state, vm)
                    Destination.TILES -> RootComponentsScreen(state, vm)
                    Destination.DASHBOARD -> DashboardTabContent(
                        state,
                        vm,
                        { restore.launch(arrayOf("application/json", "text/plain")) },
                        { export.launch("ListCleaner-backup.json") },
                        collectingDiagnostics,
                        {
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                            diagnosticExport.launch("ListCleaner-diagnostic-$stamp.zip")
                        },
                        { fileCheck.launch(arrayOf("*/*")) }
                    )
                }
            }
        }
    }

    private fun readLimitedText(uri: Uri, maxChars: Int): String {
        val input = contentResolver.openInputStream(uri) ?: error(getString(R.string.backup_read_failed))
        return input.bufferedReader().use {
            it.readBackupText(maxChars, getString(R.string.backup_too_large))
        }
    }

    private fun toast(message: String, long: Boolean = false) =
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
