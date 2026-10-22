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



import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment

/** All settings pages use the same section spacing, card padding and title hierarchy. */
@Composable
internal fun SettingsArea(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)) {
        AdaptiveText(title, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MaterialSectionCard { content() }
    }
}

/** Two short choices are immediately visible; longer groups use a fully visible single-choice dialog. */
@Composable
internal fun <T> SettingsChoices(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    require(options.isNotEmpty())
    var choosing by remember { mutableStateOf(false) }
    if (options.size <= 2) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)) {
            AdaptiveText(title, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)) {
                options.forEach { (item, label) ->
                    // Column makes both choices wrap cleanly on a narrow display.
                    Column(Modifier.weight(1f)) {
                        AdaptiveFilterChip(selected = item == value, onClick = { onSelected(item) },
                            label = { AdaptiveText(label, maxLines = 2) })
                    }
                }
            }
        }
    } else {
        val currentLabel = options.firstOrNull { it.first == value }?.second.orEmpty()
        MaterialSettingsRow(title = title, supportingText = currentLabel) {
            AdaptiveTextButton(onClick = { choosing = true }) {
                AdaptiveText(stringResource(R.string.dashboard_switch))
            }
        }
    }
    if (choosing) {
        AdaptiveAlertDialog(
            onDismissRequest = { choosing = false },
            title = { AdaptiveText(title) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { (item, label) ->
                        Row(Modifier.fillMaxWidth().clickable {
                            choosing = false
                            onSelected(item)
                        }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = item == value, onClick = {
                                choosing = false
                                onSelected(item)
                            })
                            AdaptiveText(label)
                        }
                    }
                }
            },
            confirmButton = {
                AdaptiveTextButton(onClick = { choosing = false }) {
                    AdaptiveText(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** Update a preference in memory during gesture; persist it only on completion. */
@Composable
internal fun SettingsSlider(
    title: String, value: Int, low: Int, high: Int, suffix: String = "dp", onChange: (Int) -> Unit
) {
    val context = LocalContext.current
    Column {
        Row(Modifier.fillMaxWidth()) {
            AdaptiveText(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            AdaptiveText("$value $suffix", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = { UiPreferencesStore.write(context, UiPreferencesRuntime.current) },
            valueRange = low.toFloat()..high.toFloat()
        )
    }
}
