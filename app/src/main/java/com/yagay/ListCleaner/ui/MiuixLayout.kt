package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
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
        MaterialCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

/** Compact, theme-native workspace header. Functional status and quick actions live
 * together; app lists remain the primary vertical content rather than a dashboard.
 */
@Composable
internal fun PageOverviewSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AdaptiveSurface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                AdaptiveText(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                )
                content()
            }
        }
    }
}

/** Common heading for the editable collection below the filter controls. */
@Composable
internal fun PageResultsSection(title: String, detail: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PageSectionTitle(title)
        if (!detail.isNullOrBlank()) {
            SharedAppSupportingText(detail)
        }
    }
}

/** Consistent actionable empty state for rules, ordering and Root components. */
@Composable
internal fun WorkspaceEmptyState(
    message: String,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null
) {
    AdaptiveSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdaptiveText(
                message,
                textAlign = TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
            if (onClear != null && clearLabel != null) {
                AdaptiveOutlinedButton(onClick = onClear) {
                    AdaptiveText(clearLabel)
                }
            }
        }
    }
}
