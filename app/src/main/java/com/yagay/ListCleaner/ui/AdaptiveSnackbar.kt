package com.yagay.ListCleaner.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

@Composable
internal fun AdaptiveSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState)
}
