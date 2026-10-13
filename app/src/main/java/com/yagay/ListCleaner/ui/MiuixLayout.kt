package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle as NativeSectionTitle
import top.yukonga.miuix.kmp.basic.Surface as NativeSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Separate MIUIX page structure, not merely a Material color override.
 * Keep groups in one visual section with connected first/middle/last row corners,
 * while Material retains the original independent rows and dividers.
 */
internal enum class MiuixRowPosition { SINGLE, FIRST, MIDDLE, LAST }

internal fun MiuixRowPosition.shape(): RoundedCornerShape {
    val top = if (this == MiuixRowPosition.SINGLE || this == MiuixRowPosition.FIRST) 20.dp else 4.dp
    val bottom = if (this == MiuixRowPosition.SINGLE || this == MiuixRowPosition.LAST) 20.dp else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

internal fun MiuixRowPosition.verticalPadding(): PaddingValues = PaddingValues(
    top = if (this == MiuixRowPosition.SINGLE || this == MiuixRowPosition.FIRST) 7.dp else 1.dp,
    bottom = if (this == MiuixRowPosition.SINGLE || this == MiuixRowPosition.LAST) 7.dp else 1.dp
)

@Composable
internal fun PageSectionTitle(title: String) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MIUIX_STYLE) {
        NativeSectionTitle(text = title)
    } else {
        AdaptiveText(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}

/** MIUIX uses an inset rounded filter section instead of the Material full-width toolbar. */
@Composable
internal fun PageFilterSection(content: @Composable () -> Unit) {
    if (LocalUiThemeStyle.current == UiThemeStyle.MIUIX_STYLE) {
        NativeSurface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = MiuixTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.padding(vertical = 8.dp)) { content() }
        }
    } else {
        AdaptiveSurface(tonalElevation = 2.dp) { content() }
    }
}

/** A common page hierarchy: a distinct section label followed by a grouped content surface. */
@Composable
internal fun PageOverviewSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PageSectionTitle(title)
        AdaptiveSurface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
}
