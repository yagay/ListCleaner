package com.yagay.ListCleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.OpenPreset
import com.yagay.ListCleaner.domain.OpenTypeConfig

/** Secondary OPEN-type tabs using the same visual language as the primary IntentKind tabs. */
@Composable
fun OpenPresetFilterRow(
    selected: OpenPreset?,
    config: OpenTypeConfig,
    onSelected: (OpenPreset?) -> Unit,
    onManageCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = config.configuredPresets()
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        item("all") {
            OpenPresetTab("全部", selected == null) { onSelected(null) }
        }
        items(presets, key = { it.name }) { preset ->
            OpenPresetTab(config.titleFor(preset), selected == preset) { onSelected(preset) }
        }
        item("manage-custom") {
            OpenPresetTab("+ 自定义", false, onManageCustom)
        }
    }
}

@Composable
private fun OpenPresetTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onClick) {
            Text(
                title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier.height(2.dp).width(24.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}
