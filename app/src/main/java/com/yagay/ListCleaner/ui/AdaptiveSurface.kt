package com.yagay.ListCleaner.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun AdaptiveSurface(
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = content
    )
}
