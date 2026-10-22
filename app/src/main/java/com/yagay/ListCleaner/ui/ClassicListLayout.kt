package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R

/**
 * Classic (main-branch inspired) landing surface.
 * The existing app-list editor and ListControls render below this header;
 * no second rule store, query engine or Root management stack is introduced.
 */
@Composable
internal fun ClassicListHeader(
    state: MainState,
    onOpenStatus: () -> Unit,
    onOpenComponents: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(
            horizontal = MaterialUiTokens.pageHorizontal,
            vertical = MaterialUiTokens.contentGap
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialUiTokens.contentGap)
    ) {
        CleanupStatusAlert(state, onOpenStatus = onOpenStatus)
        AdaptiveOutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenComponents
        ) {
            AdaptiveIcon(Icons.Rounded.GridView, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            AdaptiveText(stringResource(R.string.root_screen_title),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
