package com.yagay.ListCleaner.ui

import android.net.Uri
import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.RootComponentScan
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenSelectionSource
import com.yagay.ListCleaner.domain.OpenTypeConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Owns file-effect preview and diagnostic export IO, keeping MainViewModel UI-state focused. */
internal class DiagnosticController(
    private val app: ListCleanerApp,
    private val scope: CoroutineScope,
    private val stateProvider: () -> MainState,
    private val rootScanProvider: () -> RootComponentScan,
    private val rootLastOperationProvider: () -> String,
) {
    private val mutableFileCheckStatus = MutableStateFlow<String?>(null)
    val fileCheckStatus: StateFlow<String?> = mutableFileCheckStatus
    private val mutableCheckingFile = MutableStateFlow(false)
    val checkingFile: StateFlow<Boolean> = mutableCheckingFile

    private val mutableCollectingDiagnostics = MutableStateFlow(false)
    val collectingDiagnostics: StateFlow<Boolean> = mutableCollectingDiagnostics
    private val mutableExportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = mutableExportMessage

    fun clearExportMessage() { mutableExportMessage.value = null }

    fun inspectFile(uri: Uri) {
        if (mutableCheckingFile.value) return
        mutableCheckingFile.value = true
        scope.launch {
            mutableFileCheckStatus.value = app.getString(R.string.file_preview_checking)
            try {
                val mime = app.contentResolver.getType(uri)
                val found = app.catalog.inspectFile(uri)
                val config = app.rules.remoteSnapshot()
                val preview = com.yagay.ListCleaner.domain.previewOpenEffect(
                    found,
                    config.rules,
                    config.mode,
                    config.priorities,
                    config.openTypes,
                    mime,
                    uri.scheme,
                    uri.lastPathSegment ?: uri.path
                )
                mutableFileCheckStatus.value = buildString {
                    val typeTitle = preview.preset?.let { openPresetTitle(config.openTypes, it) }
                        ?: app.getString(R.string.file_preview_generic_open)
                    append(app.getString(
                        R.string.file_preview_header,
                        typeTitle,
                        mime ?: app.getString(R.string.common_unknown)
                    ))
                    append(app.getString(R.string.file_preview_counts, preview.rawCount, preview.finalCount))
                    if (preview.restoredEmpty) append(app.getString(R.string.file_preview_empty_restored))
                    append(app.getString(R.string.file_preview_disclaimer))
                    val details = preview.items.take(12)
                    if (details.isNotEmpty()) append('\n')
                    details.forEachIndexed { index, item ->
                        if (index > 0) append('\n')
                        append(if (item.included) "✓ " else "✕ ")
                        append(item.candidate.appLabel)
                        item.rank?.let { append(app.getString(R.string.file_preview_rank, it)) }
                        item.selectedBy?.let {
                            append(app.getString(R.string.file_preview_source, selectionSourceTitle(it)))
                        }
                    }
                    if (preview.items.size > details.size) {
                        append(app.getString(R.string.file_preview_more, preview.items.size - details.size))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "File preview failed", failure)
                mutableFileCheckStatus.value = app.getString(R.string.file_preview_failed)
            } finally {
                mutableCheckingFile.value = false
            }
        }
    }

    fun exportDiagnostics(uri: Uri) {
        if (mutableCollectingDiagnostics.value) return
        mutableCollectingDiagnostics.value = true
        scope.launch {
            var report: File? = null
            try {
                val config = app.rules.remoteSnapshot()
                report = DiagnosticCollector.collect(
                    app,
                    stateProvider().copy(
                        selected = config.rules,
                        displayMode = config.mode,
                        priorities = config.priorities,
                        diagnosticMode = config.diagnostic,
                        openTypes = config.openTypes,
                        openTypesExplicit = config.openTypes,
                        runtime = app.runtime.value,
                        syncStatus = app.syncStatus.value
                    ),
                    rootScanProvider(),
                    rootLastOperationProvider(),
                )
                val ready = requireNotNull(report)
                withContext(Dispatchers.IO) {
                    val output = app.contentResolver.openOutputStream(uri, "wt")
                        ?: error(app.getString(R.string.diagnostic_create_failed))
                    output.use { destination -> ready.inputStream().use { it.copyTo(destination) } }
                }
                mutableExportMessage.value = app.getString(R.string.diagnostic_exported)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Diagnostic export failed", failure)
                mutableExportMessage.value = app.getString(
                    R.string.diagnostic_export_failed,
                    app.getString(R.string.diagnostic_create_failed)
                )
            } finally {
                report?.delete()
                mutableCollectingDiagnostics.value = false
            }
        }
    }

    private fun openPresetTitle(config: OpenTypeConfig, preset: OpenPreset): String =
        config.customDefinitions[preset]?.title ?: app.getString(preset.titleRes())

    private fun selectionSourceTitle(source: OpenSelectionSource): String = app.getString(
        when (source) {
            OpenSelectionSource.GENERIC -> R.string.file_preview_source_generic
            OpenSelectionSource.TYPED -> R.string.file_preview_source_typed
            OpenSelectionSource.GENERIC_AND_TYPED -> R.string.file_preview_source_generic_and_typed
        }
    )

    private companion object {
        const val TAG = "ListCleaner.Diagnostics"
    }
}
