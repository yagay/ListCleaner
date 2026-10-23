package com.yagay.ListCleaner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind

/** The classic component list offers the same category filtering as the Entry list.
 * Null means all kinds; the filter only affects presentation, never Root state.
 */
internal object ComponentCategoryFilterPolicy {
    val choices: List<CleanupKind?> = listOf(null) + CleanupKind.entries
    fun matches(selected: CleanupKind?, candidate: CleanupKind): Boolean =
        selected == null || selected == candidate
}

@Composable
internal fun ClassicComponentCategoryFilter(
    selected: CleanupKind?,
    onSelected: (CleanupKind?) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = MaterialUiTokens.pageHorizontal,
            vertical = 2.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialUiTokens.categoryGap)
    ) {
        items(
            count = ComponentCategoryFilterPolicy.choices.size,
            key = { index -> ComponentCategoryFilterPolicy.choices[index]?.name ?: "ALL" }
        ) { index ->
            val kind = ComponentCategoryFilterPolicy.choices[index]
            val active = selected == kind
            CompactCategoryButton(
                selected = active,
                onClick = { onSelected(kind) }
            ) {
                WorkspaceControlLabel(
                    if (kind == null) stringResource(R.string.common_all)
                    else stringResource(kind.titleRes()),
                    color = compactCategoryLabelColor(active)
                )
            }
        }
    }
}
