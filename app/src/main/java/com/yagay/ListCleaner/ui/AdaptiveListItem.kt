package com.yagay.ListCleaner.ui

import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable

@Composable
internal fun AdaptiveListItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    ListItem(
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        trailingContent = trailingContent
    )
}
