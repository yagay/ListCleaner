package com.yagay.ListCleaner.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.R

/** About uses the same flat setting rows, spacing and back stack as other areas. */
@Composable
internal fun AboutSettings() {
    SettingsArea(stringResource(R.string.lc_about_app_info)) {
        MaterialSettingsRow(title = stringResource(R.string.app_name))
        MaterialSettingsRow(
            title = stringResource(R.string.lc_about_version),
            supportingText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        MaterialSettingsRow(
            title = stringResource(R.string.lc_about_developer),
            supportingText = "yagay"
        )
    }
    val uriHandler = LocalUriHandler.current
    SettingsArea(stringResource(R.string.lc_about_links)) {
        AboutLinkRow(
            title = stringResource(R.string.lc_about_source),
            url = "https://github.com/yagay/ListCleaner",
            onOpen = uriHandler::openUri
        )
        AboutLinkRow(
            title = stringResource(R.string.lc_about_releases),
            url = "https://github.com/yagay/ListCleaner/releases",
            onOpen = uriHandler::openUri
        )
        AboutLinkRow(
            title = stringResource(R.string.lc_about_issues),
            url = "https://github.com/yagay/ListCleaner/issues",
            onOpen = uriHandler::openUri
        )
    }
}

@Composable
private fun AboutLinkRow(title: String, url: String, onOpen: (String) -> Unit) {
    MaterialSettingsRow(
        title = title,
        supportingText = url,
        onClick = { runCatching { onOpen(url) } }
    ) {
        AdaptiveIcon(
            Icons.Rounded.OpenInNew,
            title,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}
