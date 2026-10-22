package com.yagay.ListCleaner.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AppTypeFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt


@Composable
internal fun SettingsResetControls(onColorModeChange: (UiColorMode) -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        AdaptiveAlertDialog(
            onDismissRequest = { confirm = false },
            title = { AdaptiveText(stringResource(R.string.settings_reset_title)) },
            text = { AdaptiveText(stringResource(R.string.settings_reset_description)) },
            confirmButton = {
                AdaptiveTextButton(onClick = {
                    confirm = false
                    UiPreferencesStore.reset(context)
                    onColorModeChange(UiColorMode.SYSTEM)
                }) { AdaptiveText(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { confirm = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    AdaptiveOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { confirm = true }) {
        AdaptiveText(stringResource(R.string.settings_reset_title))
    }
}
