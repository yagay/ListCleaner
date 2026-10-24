package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import kotlin.math.roundToInt

/** All six Settings areas share a flat section title and continuous setting rows. */
@Composable
internal fun SettingsArea(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)) {
        FlatSectionHeader(title)
        MaterialSectionCard { content() }
    }
}

/**
 * Every single-choice preference uses the same row and dialog regardless of
 * its option count. The selected value is visible without entering the dialog.
 * No arbitrary two-choice chip / three-choice button layout split.
 */
@Composable
internal fun <T> SettingsChoices(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    require(options.isNotEmpty())
    var choosing by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second.orEmpty()
    MaterialSettingsRow(
        title = title,
        supportingText = selectedLabel,
        onClick = { choosing = true }
    ) {
        AdaptiveIcon(
            Icons.Rounded.ChevronRight,
            title,
            Modifier.padding(end = 4.dp)
        )
    }
    if (choosing) {
        AdaptiveAlertDialog(
            onDismissRequest = { choosing = false },
            title = { AdaptiveText(title) },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                ) {
                    options.forEach { (item, label) ->
                        ListItem(
                            modifier = Modifier.fillMaxWidth().clickable {
                                choosing = false
                                if (item != value) onSelected(item)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { AdaptiveText(label) },
                            leadingContent = { RadioButton(selected = item == value, onClick = null) }
                        )
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

/** One tappable row and switch source of truth; avoid two independent actions. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    MaterialSettingsRow(
        title = title,
        supportingText = supportingText,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null
    ) {
        // The parent row owns the click; the switch is a visual state only.
        // Keeping two independent toggle handlers could apply a change twice.
        AdaptiveSwitch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

internal enum class SettingsActionStyle { PRIMARY, SECONDARY, DANGER }

/** Uniform height, width, shape and spacing for actions in all Settings pages. */
@Composable
internal fun SettingsActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SettingsActionStyle = SettingsActionStyle.SECONDARY,
    content: @Composable RowScope.() -> Unit
) {
    val size = modifier.fillMaxWidth().heightIn(min = MaterialUiTokens.settingsActionHeight)
    when (style) {
        SettingsActionStyle.PRIMARY -> AdaptiveButton(
            onClick = onClick, modifier = size, enabled = enabled, content = content
        )
        SettingsActionStyle.SECONDARY, SettingsActionStyle.DANGER -> AdaptiveOutlinedButton(
            onClick = onClick,
            modifier = size,
            enabled = enabled,
            danger = style == SettingsActionStyle.DANGER,
            content = content
        )
    }
}

/** Supporting text is always the same weight and semantic color. */
@Composable
internal fun SettingsHint(message: String, warning: Boolean = false) {
    AdaptiveText(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Slider header aligns to the same card inset; gesture persists on release. */
@Composable
internal fun SettingsSlider(
    title: String, value: Int, low: Int, high: Int, suffix: String = "dp", onChange: (Int) -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.settingsRowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdaptiveText(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            AdaptiveText(
                "$value $suffix",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialSelectionPalette.activeText()
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = {
                UiPreferencesStore.write(context, UiPreferencesRuntime.current)
            },
            valueRange = low.toFloat()..high.toFloat()
        )
    }
}
