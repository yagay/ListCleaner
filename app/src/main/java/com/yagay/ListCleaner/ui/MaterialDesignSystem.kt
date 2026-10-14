package com.yagay.ListCleaner.ui

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
    val pageHorizontal = 16.dp
    val pageVertical = 12.dp
    val sectionGap = 14.dp
    val contentGap = 8.dp
    val cardPadding = 16.dp
    val cardRadius = 20.dp
    val groupedInnerRadius = 4.dp
    val groupedOuterGap = 4.dp
    val groupedInnerGap = 1.dp
    val cardShape = RoundedCornerShape(cardRadius)
    val groupHorizontal = 16.dp
    val categoryHeight = 124.dp
    val categoryPadding = 14.dp
    val categoryGap = 10.dp
    val listRowHeight = 60.dp
    val listRowHorizontal = 8.dp
    val listRowVertical = 6.dp
    val appIconSize = 36.dp
    val appIconShape = RoundedCornerShape(9.dp)
    val appLabelInset = 8.dp
    val detailHeight = 48.dp
    val detailHorizontal = 14.dp
    val detailVertical = 4.dp
    val buttonHeight = 40.dp
    val iconTouchTarget = 48.dp
    val compactButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    val normalButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
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

/** Shared settings row layout; controls are supplied by each feature. */
@Composable
internal fun MaterialSettingsRow(
    title: String,
    supportingText: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MaterialUiTokens.listRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AdaptiveText(title, style = MaterialTheme.typography.bodyLarge)
            if (!supportingText.isNullOrBlank()) {
                AdaptiveText(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
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
