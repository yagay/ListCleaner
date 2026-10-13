package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One consistent app icon, title, and supporting content in all list surfaces. */
@Composable
internal fun SharedAppIdentity(
    icon: Bitmap?,
    title: String,
    modifier: Modifier = Modifier,
    supportingContent: @Composable ColumnScope.() -> Unit
) {
    AppIcon(bitmap = icon, appLabel = title)
    Column(modifier.weight(1f).padding(start = 10.dp)) {
        AdaptiveText(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        supportingContent()
    }
}
