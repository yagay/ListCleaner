package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One consistent app icon, title, and supporting content in all list surfaces. */
@Composable
internal fun RowScope.SharedAppIdentity(
    icon: Bitmap?,
    title: String,
    modifier: Modifier = Modifier,
    supportingContent: @Composable ColumnScope.() -> Unit
) {
    AppIcon(bitmap = icon, appLabel = title)
    Column(modifier.weight(1f).then(modifier).padding(start = 10.dp)) {
        AdaptiveText(
            title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        supportingContent()
    }
}

/** Common secondary text styling across rules, priority, and component lists. */
@Composable
internal fun SharedAppSupportingText(
    text: String,
    modifier: Modifier = Modifier
) {
    AdaptiveText(
        text,
        modifier = modifier,
        style = if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
            androidx.compose.material3.MaterialTheme.typography.bodySmall
        else top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.main,
        color = if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL)
            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        else androidx.compose.ui.graphics.Color.Unspecified,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
