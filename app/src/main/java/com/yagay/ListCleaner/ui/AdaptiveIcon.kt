package com.yagay.ListCleaner.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun AdaptiveIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    if (tint == null) Icon(imageVector, contentDescription, modifier)
    else Icon(imageVector, contentDescription, modifier, tint)
}
