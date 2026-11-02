package com.yagay.ListCleaner.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.CapturedSettingsPage
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.SettingsPageCaptureProtocol
import com.yagay.ListCleaner.runtime.SettingsPageCaptureStore

/**
 * Browser-role discovery is opt-in and read-only. A discovered Activity is not
 * assumed to be an exported entry point or a faithful replica of its Settings page.
 */
@Composable
internal fun BrowserPageCaptureDialog(userId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pages = remember(context, userId) {
        SettingsPageCaptureStore.pages(context).filter { it.userId == userId }
    }
    val armed = remember(context, userId) { SettingsPageCaptureStore.active(context) }
    val sameUser = userId == AndroidUid.userId(Process.myUid())
    SettingsFlatDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(R.string.page_capture_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                AdaptiveText(stringResource(R.string.page_capture_info))
                SettingsFlatChoiceRow(
                    label = stringResource(R.string.page_capture_open_normal),
                    selected = false,
                    onClick = {
                        if (!sameUser) {
                            showCaptureMessage(context, R.string.system_preview_other_space)
                        } else {
                            SystemEffectPreview.launchSettings(context, IntentKind.BROWSER, userId)
                            onDismiss()
                        }
                    }
                )
                SettingsFlatChoiceRow(
                    label = stringResource(if (armed) R.string.page_capture_stop else R.string.page_capture_start),
                    selected = armed,
                    onClick = {
                        if (!sameUser) {
                            showCaptureMessage(context, R.string.system_preview_other_space)
                        } else if (armed) {
                            SettingsPageCaptureStore.stop(context)
                            onDismiss()
                        } else if (!SettingsPageCaptureStore.begin(context)) {
                            showCaptureMessage(context, R.string.page_capture_start_failed)
                        } else {
                            if (!openDefaultAppsSettings(context)) {
                                SettingsPageCaptureStore.stop(context)
                                showCaptureMessage(context, R.string.page_capture_open_failed)
                            } else onDismiss()
                        }
                    }
                )
                if (armed) AdaptiveText(stringResource(R.string.page_capture_active))
                AdaptiveText(stringResource(if (pages.isEmpty()) R.string.page_capture_none else R.string.page_capture_found))
                pages.forEach { page ->
                    val displayed = page.fragmentClass.ifEmpty { page.activityClass }
                    SettingsFlatChoiceRow(
                        label = displayed.substringAfterLast('.') + " · " + page.packageName,
                        selected = false,
                        onClick = {
                            if (!sameUser || !openCapturedPage(context, page)) {
                                showCaptureMessage(context, R.string.page_capture_no_external)
                            } else onDismiss()
                        }
                    )
                    AdaptiveText(page.activityClass +
                        (if (page.fragmentClass.isNotEmpty()) "\n" + page.fragmentClass else "") +
                        (if (page.action.isNotEmpty()) "\n" + page.action else "") +
                        (if (page.roleName.isNotEmpty()) "\n" + page.roleName else ""))
                }
            }
        },
        confirmButton = { AdaptiveTextButton(onClick = onDismiss) {
            AdaptiveText(stringResource(R.string.common_cancel))
        } }
    )
}

private fun showCaptureMessage(context: Context, id: Int) =
    Toast.makeText(context, id, Toast.LENGTH_LONG).show()

private fun openDefaultAppsSettings(context: Context): Boolean =
    listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS).any { action ->
        runCatching {
            context.startActivity(Intent(action))
            true
        }.getOrDefault(false)
    }

/** Never bypass exported/permission checks to launch an internal settings Activity. */
private fun openCapturedPage(context: Context, page: CapturedSettingsPage): Boolean {
    if (!SettingsPageCaptureProtocol.valid(page)) return false
    val component = ComponentName(page.packageName, page.activityClass)
    val info = runCatching { context.packageManager.getActivityInfo(component, 0) }.getOrNull()
        ?: return false
    if (!info.exported) return false
    if (info.permission != null &&
        context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) return false
    val intent = Intent(page.action.ifBlank { Intent.ACTION_MAIN }).setComponent(component)
    if (page.roleName.isNotBlank()) {
        intent.putExtra("android.intent.extra.ROLE_NAME", page.roleName)
    }
    if (page.fragmentClass.startsWith("com.android.settings.")) {
        intent.putExtra(":settings:show_fragment", page.fragmentClass)
    }
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}
