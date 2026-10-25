package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.yagay.ListCleaner.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AdaptiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = androidx.compose.material3.ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) = TextButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = RectangleShape,
    colors = MaterialActionColors.text(),
    contentPadding = contentPadding, content = content
)

@Composable
internal fun AdaptiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    content: @Composable RowScope.() -> Unit
) = OutlinedButton(
    onClick = onClick, modifier = modifier,
    enabled = enabled,
    shape = RectangleShape,
    colors = if (danger) MaterialActionColors.dangerOutlined() else MaterialActionColors.outlined(),
    border = if (danger) MaterialActionColors.dangerOutlinedBorder() else MaterialActionColors.outlinedBorder(enabled),
    content = content
)

@Composable
internal fun AdaptiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) = IconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled, content = content
)

@Composable
internal fun AdaptiveBackButton(
    onClick: () -> Unit,
    contentDescription: String? = null
) {
    AdaptiveIconButton(onClick = onClick) {
        AdaptiveIcon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription ?: stringResource(R.string.common_back), Modifier.size(24.dp))
    }
}

@Composable
internal fun FlatUnderlineTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    // Shared flat tabs for system preview and app visibility: no capsules.
    Surface(
        onClick = onClick, modifier = modifier,
        enabled = enabled, shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column {
            Row(
                Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                leadingIcon?.invoke()
                label()
            }
            Box(
                Modifier.fillMaxWidth().height(2.dp).background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            )
        }
    }
}

/** Filter/scoped choices delegate to the same flat tab as palette settings. */
@Composable
internal fun AdaptiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    FlatUnderlineTab(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon
    )
}

@Composable
internal fun AdaptiveTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = TriStateCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled)

@Composable
internal fun AdaptiveHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = Color.Unspecified
) = HorizontalDivider(
    modifier = modifier,
    thickness = thickness,
    color = if (color == Color.Unspecified) MaterialTheme.colorScheme.outlineVariant else color
)

@Composable
internal fun AdaptiveLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null
) {
    if (progress == null) LinearProgressIndicator(modifier = modifier)
    else LinearProgressIndicator(progress = progress, modifier = modifier)
}

@Composable
internal fun AdaptiveCircularProgressIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp
) = CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)

@Composable
internal fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) = Button(
    onClick = onClick, modifier = modifier,
    enabled = enabled,
    shape = RectangleShape,
    colors = MaterialActionColors.filled(),
    content = content
)

@Composable
internal fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)

@Composable
internal fun AdaptiveCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)

/** Compatibility entrypoint now delegates to the shared flat row surface. */
@Composable
internal fun AdaptiveGroupSurface(
    modifier: Modifier = Modifier,
    position: ListRowPosition = ListRowPosition.SINGLE,
    horizontalInset: Dp = MaterialUiTokens.groupHorizontal,
    content: @Composable () -> Unit
) = FlatRowSurface(
    modifier = modifier,
    horizontalInset = horizontalInset,
    content = content
)
