package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import kotlin.math.roundToInt

/** All six Settings areas share a flat section title and continuous setting rows. */
@Composable
internal fun SettingsArea(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlatSectionHeader(
            title, modifier = Modifier.padding(horizontal = MaterialUiTokens.pageHorizontal)
        )
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
        SettingsFlatDialog(
            onDismissRequest = { choosing = false },
            title = { AdaptiveText(title) },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                ) {
                    options.forEach { (item, label) ->
                        SettingsFlatChoiceRow(
                            label = label,
                            selected = item == value,
                            onClick = {
                                choosing = false
                                if (item != value) onSelected(item)
                            }
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

/** Flat action row for every Settings operation; the role controls text color. */
@Composable
internal fun SettingsActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SettingsActionStyle = SettingsActionStyle.SECONDARY,
    content: @Composable RowScope.() -> Unit
) {
    val actionColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        style == SettingsActionStyle.DANGER -> MaterialTheme.colorScheme.error
        style == SettingsActionStyle.PRIMARY -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    FlatRowSurface(
        modifier = modifier.fillMaxWidth(),
        onClick = if (enabled) onClick else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = MaterialUiTokens.settingsActionHeight)
                .padding(horizontal = MaterialUiTokens.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.settingsRowGap)
        ) {
            CompositionLocalProvider(LocalContentColor provides actionColor) {
                content()
            }
            Spacer(Modifier.weight(1f))
            AdaptiveIcon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = actionColor)
        }
    }
}

/** Settings dialogs use a square, shadowless surface. */
@Composable
internal fun SettingsFlatDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title, text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

@Composable
private fun SettingsFlatChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    FlatRowSurface(onClick = onClick, dividerInset = 0.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlatSelectionRail(selected)
            AdaptiveText(
                label, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
            )
            if (selected) AdaptiveIcon(
                Icons.Rounded.Check, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** A shared flat text-tab selector for light and dark palette settings. */
@Composable
internal fun SettingsFlatPaletteTabs(
    firstLabel: String, secondLabel: String,
    secondSelected: Boolean, onFirst: () -> Unit, onSecond: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaterialUiTokens.pageHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsFlatPaletteTab(firstLabel, !secondSelected, onFirst, Modifier.weight(1f))
        SettingsFlatPaletteTab(secondLabel, secondSelected, onSecond, Modifier.weight(1f))
    }
}

@Composable
private fun SettingsFlatPaletteTab(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier
) {
    FlatUnderlineTab(
        selected = selected, onClick = onClick,
        modifier = modifier,
        label = {
            AdaptiveText(
                label, style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/** Supporting text is always the same weight and semantic color. */
@Composable
internal fun SettingsHint(message: String, warning: Boolean = false) {
    AdaptiveText(
        message,
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal, vertical = 6.dp
        ),
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
    FlatRowSurface(dividerInset = 0.dp) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal, vertical = 4.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
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
}
