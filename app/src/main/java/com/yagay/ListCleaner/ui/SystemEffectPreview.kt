package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeProtocol
import java.io.File

/** Routes for a real OS interface, rather than a local reconstruction of its filtered results. */
internal enum class SystemPreviewRoute { CHOOSER, PICKER, SETTINGS, UNAVAILABLE }

internal fun systemPreviewRoute(kind: IntentKind): SystemPreviewRoute = when (kind) {
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE,
    IntentKind.SEND_TO, IntentKind.OPEN, IntentKind.BROWSER, IntentKind.DEEP_LINK,
    IntentKind.DIAL, IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO,
    IntentKind.RECORD_AUDIO, IntentKind.PROCESS_TEXT, IntentKind.HOME,
    IntentKind.ASSISTANT, IntentKind.WALLPAPER -> SystemPreviewRoute.CHOOSER

    IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
    IntentKind.DOCUMENT_PROVIDER, IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE ->
        SystemPreviewRoute.PICKER

    IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY, IntentKind.PRINT,
    IntentKind.VPN, IntentKind.NFC_HCE, IntentKind.NOTIFICATION_LISTENER,
    IntentKind.DREAM, IntentKind.CALL_SCREENING -> SystemPreviewRoute.SETTINGS

    IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER, IntentKind.SHORTCUT_ITEM,
    IntentKind.LAUNCHER_SHORTCUT -> SystemPreviewRoute.UNAVAILABLE
}

internal fun systemPreviewRoute(kind: CleanupKind): SystemPreviewRoute = when (kind) {
    CleanupKind.LAUNCHER, CleanupKind.SHORTCUT -> SystemPreviewRoute.CHOOSER
    CleanupKind.TILE, CleanupKind.WIDGET -> SystemPreviewRoute.UNAVAILABLE
}

/**
 * This starts a real Android chooser, picker, or settings Activity. It never evaluates the
 * user's rule set as if it were a system result, sends data, writes to a returned URI,
 * alters a component, or calls a third-party app directly.
 *
 * Cross-user startActivityAsUser requires privileged OS permissions. Do not silently
 * preview the personal user's panel while the work profile is selected.
 */
internal object SystemEffectPreview {
    fun launch(
        context: Context, kind: IntentKind, selectedUserId: Int,
        scenario: SystemProbeScenario = defaultProbeScenario(kind)
    ) {
        if (!checkUser(context, selectedUserId)) return
        val route = systemPreviewRoute(kind)
        if (route == SystemPreviewRoute.UNAVAILABLE) {
            show(context, R.string.system_preview_unavailable)
            return
        }
        runCatching {
            val target = buildIntent(context, kind, scenario)
            if (route == SystemPreviewRoute.SETTINGS) {
                show(context, R.string.system_preview_settings_notice)
            }
            val launch = if (route == SystemPreviewRoute.CHOOSER)
                Intent.createChooser(target, context.getString(R.string.system_preview_chooser_title))
            else target
            context.startActivity(launch)
        }.onFailure { error ->
            if (error is ActivityNotFoundException || error is SecurityException) {
                show(context, R.string.system_preview_no_handler)
            } else {
                show(context, R.string.system_preview_failed)
            }
        }
    }

    fun launch(context: Context, kind: CleanupKind, selectedUserId: Int) {
        if (!checkUser(context, selectedUserId)) return
        if (systemPreviewRoute(kind) == SystemPreviewRoute.UNAVAILABLE) {
            show(context, R.string.system_preview_root_unavailable)
            return
        }
        val target = when (kind) {
            CleanupKind.LAUNCHER ->
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            CleanupKind.SHORTCUT ->
                Intent(Intent.ACTION_CREATE_SHORTCUT)
            else -> return
        }
        runCatching {
            context.startActivity(Intent.createChooser(
                target, context.getString(R.string.system_preview_chooser_title)
            ))
        }.onFailure { error ->
            show(context, if (error is ActivityNotFoundException || error is SecurityException)
                R.string.system_preview_no_handler else R.string.system_preview_failed)
        }
    }

    private fun checkUser(context: Context, requested: Int): Boolean {
        if (requested == AndroidUid.userId(Process.myUid())) return true
        show(context, R.string.system_preview_other_space)
        return false
    }

    private fun show(context: Context, resourceId: Int) =
        Toast.makeText(context, resourceId, Toast.LENGTH_LONG).show()

    /** Shared exact test Intent for native picker and in-app PackageManager probing. */
    internal fun queryIntent(
        context: Context, kind: IntentKind,
        scenario: SystemProbeScenario = defaultProbeScenario(kind),
    ): Intent = markedQueryIntent(buildIntent(context, kind, scenario))

    /** Only PM read-only queries use the marker; do not attach it to native launches. */
    internal fun markedQueryIntent(intent: Intent): Intent = intent.apply {
        putExtra(RuntimeProtocol.EXTRA_MANAGER_PREVIEW_QUERY, true)
    }

    private fun buildIntent(
        context: Context, kind: IntentKind, scenario: SystemProbeScenario
    ): Intent = when (kind) {
        IntentKind.SHARE, IntentKind.DIRECT_SHARE -> {
            if (scenario == SystemProbeScenario.TEXT || kind == IntentKind.DIRECT_SHARE) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.system_preview_example_text))
                }
            } else {
                val uri = sampleFiles(context, scenario).first()
                Intent(Intent.ACTION_SEND).apply {
                    type = requireNotNull(scenario.mimeType)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Preview", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        IntentKind.SHARE_MULTIPLE -> sampleFiles(context, scenario).let { uris ->
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = scenario.mimeType ?: "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver,
                    context.getString(R.string.system_preview_chooser_title), uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
        }
        IntentKind.SEND_TO -> Intent(Intent.ACTION_SENDTO,
            if (scenario == SystemProbeScenario.SMS) Uri.parse("smsto:0000000000")
            else Uri.parse("mailto:preview@example.invalid"))
        IntentKind.OPEN -> Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(sampleFiles(context, scenario).first(),
                scenario.mimeType ?: "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        IntentKind.BROWSER, IntentKind.DEEP_LINK ->
            Intent(Intent.ACTION_VIEW, Uri.parse(
                if (scenario == SystemProbeScenario.HTTP) "http://example.com/"
                else "https://example.com/"
            )).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        IntentKind.DIAL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:0000000000"))
        IntentKind.GET_CONTENT -> Intent(Intent.ACTION_GET_CONTENT).apply {
            type = scenario.mimeType ?: "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        IntentKind.OPEN_DOCUMENT, IntentKind.DOCUMENT_PROVIDER ->
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = if (kind == IntentKind.DOCUMENT_PROVIDER) "*/*"
                    else scenario.mimeType ?: "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        IntentKind.CREATE_DOCUMENT -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = scenario.mimeType ?: "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, context.getString(R.string.system_preview_file_name))
        }
        IntentKind.DOCUMENT_ROOT -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        IntentKind.PHOTO_SOURCE -> if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
        } else Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        IntentKind.CAPTURE_IMAGE -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        IntentKind.CAPTURE_VIDEO -> Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        IntentKind.RECORD_AUDIO -> Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        IntentKind.PROCESS_TEXT -> Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, context.getString(R.string.system_preview_example_text))
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
        IntentKind.HOME -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        IntentKind.ASSISTANT -> Intent(Intent.ACTION_ASSIST)
        IntentKind.WALLPAPER -> Intent(Intent.ACTION_SET_WALLPAPER)
        IntentKind.INPUT_METHOD -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        IntentKind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        IntentKind.PRINT -> Intent(Settings.ACTION_PRINT_SETTINGS)
        IntentKind.VPN -> Intent(Settings.ACTION_VPN_SETTINGS)
        IntentKind.NFC_HCE -> Intent(Settings.ACTION_NFC_PAYMENT_SETTINGS)
        IntentKind.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        IntentKind.DREAM -> Intent(Settings.ACTION_DREAM_SETTINGS)
        IntentKind.CALL_SCREENING -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER, IntentKind.SHORTCUT_ITEM,
        IntentKind.LAUNCHER_SHORTCUT -> error("No public Android picker for this entry")
    }

    /** Small valid files so matching MIME types correspond to real, safe test inputs. */
    private fun sampleFiles(context: Context, scenario: SystemProbeScenario): List<Uri> {
        val folder = File(context.cacheDir, "system-preview").apply { mkdirs() }
        return (1..2).map { index ->
            val extension = when (scenario) {
                SystemProbeScenario.IMAGE -> "png"
                SystemProbeScenario.PDF -> "pdf"
                else -> "txt"
            }
            val file = File(folder, "list-cleaner-demo-"+index+"."+extension)
            when (scenario) {
                SystemProbeScenario.IMAGE -> {
                    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                    try {
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
                SystemProbeScenario.PDF -> {
                    val document = PdfDocument()
                    try {
                        val page = document.startPage(
                            PdfDocument.PageInfo.Builder(100, 100, 1).create()
                        )
                        document.finishPage(page)
                        file.outputStream().use(document::writeTo)
                    } finally { document.close() }
                }
                else -> file.writeText(context.getString(R.string.system_preview_example_text))
            }
            FileProvider.getUriForFile(context, context.packageName + ".preview-files", file)
        }
    }
}
