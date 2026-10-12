package com.yagay.ListCleaner.ui

import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon as NativeIcon

/** Native icon tint and semantics provided by the selected component library. */
@Composable
internal fun AdaptiveIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MATERIAL) {
        if (tint == null) MaterialIcon(imageVector, contentDescription, modifier)
        else MaterialIcon(imageVector, contentDescription, modifier, tint)
    } else {
        if (tint == null) NativeIcon(imageVector, contentDescription, modifier)
        else NativeIcon(imageVector, contentDescription, modifier, tint)
    }
}
