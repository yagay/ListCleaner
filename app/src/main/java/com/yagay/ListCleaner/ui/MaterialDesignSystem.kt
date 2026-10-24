package com.yagay.ListCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The one Material 3 geometry contract. All page families share these values;
 * feature-specific behavior (Root switches, chooser rules and sorting) stays separate.
 */
internal object MaterialUiTokens {
    // Compose observes preferences when a public token is read during composition.
    // All features continue using this one geometry contract.
    private val density: Float get() = (when (UiPreferencesRuntime.current.density) {
        UiDensity.COMPACT -> 0.85f
        UiDensity.STANDARD -> 1f
        UiDensity.COMFORTABLE -> 1.15f
    }) * (UiPreferencesRuntime.current.layoutSpacingPercent / 100f)
    val pageHorizontal get() = (16f * density).dp
    val pageVertical get() = (12f * density).dp
    val sectionGap get() = (14f * density).dp
    val contentGap get() = (8f * density).dp
    val cardPadding get() = (16f * density).dp
    val cardRadius get() = UiPreferencesRuntime.current.cardRadius.dp
    val groupedInnerRadius get() = (4f * density).dp
    val groupedOuterGap get() = (4f * density).dp
    val groupedInnerGap get() = (1f * density).dp
    val groupedDetailIndent get() = (12f * density).dp
    val groupedDetailPadding get() = (6f * density).dp
    val groupedDetailStartPadding get() = (16f * density).dp
    val groupedDetailEndPadding get() = (12f * density).dp
    val groupedHeaderPadding get() = (8f * density).dp
    val cardShape get() = RoundedCornerShape(cardRadius)
    val groupHorizontal get() = pageHorizontal
    // Compact category cards: typography and icon size stay unchanged.
    // Minimum only: content can grow for large fonts and longer translations.
    // A single-line title must not reserve the space of a second line.
    val categoryHeight get() = (88f * density).dp
    val categoryPadding get() = (8f * density).dp
    val categoryContentGap get() = (3f * density).dp
    val categoryGap get() = (8f * density).dp
    // Spacing between interactive filter chips, separate from home card spacing.
    val filterChipGap get() = (6f * density).dp
    val listRowHeight get() = UiPreferencesRuntime.current.listRowHeight.dp
    val listRowHorizontal get() = (8f * density).dp
    val listRowVertical get() = (6f * density).dp
    val appIconSize get() = UiPreferencesRuntime.current.appIconSize.dp
    val appIconShape get() = RoundedCornerShape((UiPreferencesRuntime.current.appIconSize * 0.25f).dp)
    val appLabelInset get() = (8f * density).dp
    val detailHeight get() = (48f * density).dp
    val detailHorizontal get() = (14f * density).dp
    val detailVertical get() = (4f * density).dp
    // Shared Entry/Root details: kind labels must not crowd multi-line titles.
    val detailKindMaxWidth get() = (112f * density).dp
    val buttonHeight get() = UiPreferencesRuntime.current.buttonHeight.dp
    // Settings have one row/button geometry regardless of control type.
    val settingsRowHeight get() = (56f * density).dp.coerceAtLeast(48.dp)
    val settingsActionHeight get() = buttonHeight.coerceAtLeast(48.dp)
    val settingsRowGap get() = contentGap
    // 48dp remains the minimum icon touch target for accessible controls.
    val iconTouchTarget = 48.dp
    val compactButtonPadding get() = PaddingValues(horizontal = (8f * density).dp, vertical = (4f * density).dp)
    val normalButtonPadding get() = PaddingValues(horizontal = (12f * density).dp, vertical = (6f * density).dp)
}

@Composable
internal fun MaterialPageColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialUiTokens.pageHorizontal, vertical = MaterialUiTokens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.sectionGap),
        content = content
    )
}

/** Standard page section surface with consistent padding and child spacing. */
@Composable
internal fun MaterialSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    UiCard(modifier = modifier.fillMaxWidth(), variant = UiCardVariant.SECTION) {
        Column(
            Modifier.fillMaxWidth().padding(MaterialUiTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap),
            content = content
        )
    }
}

/**
 * One settings-row geometry: no nested ListItem surface or second 16dp inset.
 * Every setting lives on its section card's background; width and touch
 * target are consistent for choices, switches, links and informational rows.
 */
@Composable
internal fun MaterialSettingsRow(
    title: String,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = MaterialUiTokens.settingsRowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = MaterialUiTokens.detailVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.settingsRowGap)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AdaptiveText(title, style = MaterialTheme.typography.bodyLarge)
            if (!supportingText.isNullOrBlank()) {
                AdaptiveText(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.settingsRowGap),
            content = trailing
        )
    }
}

/** All full-screen picker and scope dialogs share one Material header and insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialFullScreenDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = { AdaptiveBackButton(onClick = onDismissRequest) }
                )
            },
            bottomBar = bottomBar,
            content = content
        )
    }
}

@Composable
internal fun MaterialDialogActions(
    dismissButton: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit
) {
    Surface(shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = MaterialUiTokens.pageHorizontal),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dismissButton()
            confirmButton()
        }
    }
}

/** A single status text treatment for rule, Root and shared editor feedback. */
@Composable
internal fun MaterialInlineStatus(
    message: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (message.isBlank()) return
    AdaptiveText(
        text = message,
        modifier = modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal,
            vertical = MaterialUiTokens.detailVertical
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
