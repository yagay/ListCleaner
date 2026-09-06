package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.domain.OpenPreset

@Composable
fun OpenPresetFilterRow(selected: OpenPreset?, onSelected: (OpenPreset?) -> Unit, modifier: Modifier = Modifier) {
    val presets = OpenPreset.entries.filter { it != OpenPreset.BROWSER }
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 8.dp)) {
        item("all") {
            FilterChip(selected = selected == null, onClick = { onSelected(null) }, label = { Text("全部") })
        }
        items(presets, key = { it.name }) { preset ->
            FilterChip(selected = selected == preset, onClick = { onSelected(preset) }, label = { Text(preset.title) })
        }
    }
}
