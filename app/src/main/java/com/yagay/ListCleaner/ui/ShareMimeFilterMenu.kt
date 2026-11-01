package com.yagay.ListCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.OpenTypeConfig
import com.yagay.ListCleaner.domain.ShareTypeOption

/** Share and Multi Share use Open's type picker and localized file-type names. */
@Composable
internal fun ShareMimeFilterMenu(
    selected: ShareTypeOption?,
    supported: List<ShareTypeOption>,
    config: OpenTypeConfig,
    onSelected: (ShareTypeOption?) -> Unit,
) {
    val labels = supported.map { option ->
        EntryTypeMenuOption(
            key = option.key,
            title = when {
                option.key == "universal" -> stringResource(R.string.open_type_universal)
                option.preset != null -> config.localizedTitle(option.preset)
                else -> option.mimeTypes.first()
            },
        )
    }
    EntryTypeFilterMenu(
        selected = selected?.key,
        options = labels,
        onSelected = { key -> onSelected(supported.firstOrNull { it.key == key }) },
        selectedTitle = labels.firstOrNull { it.key == selected?.key }?.title
            ?: stringResource(R.string.common_all),
        filterTitle = stringResource(R.string.share_mime_filter_title),
    )
}
