package com.yagay.ListCleaner.ui

import android.content.Context
import android.os.Process
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RoleEligibilityComparison
import com.yagay.ListCleaner.domain.RuntimeCaptureContract
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshot
import com.yagay.ListCleaner.domain.RuntimeInterfaceSnapshotCodec
import com.yagay.ListCleaner.domain.compareRoleEligibility
import java.util.Date

internal val ROLE_PREVIEW_KINDS = setOf(
    IntentKind.BROWSER, IntentKind.HOME, IntentKind.ASSISTANT,
    IntentKind.CALL_SCREENING,
)

/** Role eye actions launch the native picker without opening our capture list first. */
internal fun opensRolePickerDirectly(kind: IntentKind?): Boolean = kind in ROLE_PREVIEW_KINDS

private fun readRoleComparison(context: Context, kind: IntentKind, userId: Int): RoleEligibilityComparison {
    val prefs = context.getSharedPreferences(RuntimeCaptureContract.PREFS, Context.MODE_PRIVATE)
    fun decode(key: String): RuntimeInterfaceSnapshot? =
        RuntimeInterfaceSnapshotCodec.decode(prefs.getString(key, null), kind)
    val original = decode(RuntimeInterfaceSnapshotCodec.keyForRoleOriginal(kind, userId))
    val filtered = decode(RuntimeInterfaceSnapshotCodec.keyForRoleFiltered(kind, userId))
        ?: decode(RuntimeInterfaceSnapshotCodec.keyForRoleEligibility(kind))
    return compareRoleEligibility(kind, userId, original, filtered)
}

/**
 * Read-only comparison of RoleController's actual qualifying-package return.
 * No reconstructed chooser and no floating icon/activity injection.
 */
@Composable
internal fun RoleEligibilityPreviewDialog(
    request: SystemProbeRequest,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val kind = requireNotNull(request.entryKind)
    val currentUser = AndroidUid.userId(Process.myUid())
    val sameUser = request.userId == currentUser
    val comparison = remember(context, kind, request.userId) {
        if (sameUser) readRoleComparison(context, kind, request.userId)
        else compareRoleEligibility(kind, request.userId, null, null)
    }
    val roleState = remember(context, kind, request.userId) {
        if (sameUser) SystemEntryProbe.query(context, request) else null
    }
    val currentHolders = if (roleState?.evidence == ProbeEvidence.ENABLED)
        roleState.enabledItems.map { it.packageName }.toSet() else emptySet()

    val packages = (comparison.before + comparison.after).distinct()
    val packageLabels = remember(context, packages) {
        packages.associateWith { pkg ->
            runCatching {
                val info = context.packageManager.getApplicationInfo(pkg, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(pkg)
        }
    }
    val beforeSet = comparison.before.toSet()
    val afterSet = comparison.after.toSet()

    SettingsFlatDialog(
        onDismissRequest = onDismiss,
        title = { AdaptiveText(stringResource(kind.titleRes())) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                AdaptiveText(stringResource(R.string.role_preview_explanation))
                if (!sameUser) {
                    AdaptiveText(stringResource(R.string.system_preview_other_space))
                } else {
                    if (comparison.original == null && comparison.filtered == null) {
                        AdaptiveText(stringResource(R.string.role_preview_not_captured))
                    } else {
                        val captured = listOfNotNull(
                            comparison.original?.capturedAtMillis,
                            comparison.filtered?.capturedAtMillis,
                        ).maxOrNull() ?: 0L
                        val timestamp = DateFormat.getTimeFormat(context).format(Date(captured))
                        AdaptiveText(stringResource(R.string.role_preview_last_observed, timestamp))
                        if (comparison.sameQuery) {
                            AdaptiveText(stringResource(
                                R.string.role_preview_counts,
                                comparison.before.size,
                                comparison.after.size,
                            ))
                        } else {
                            AdaptiveText(stringResource(R.string.role_preview_unpaired))
                        }
                        if (comparison.original == null || comparison.filtered == null) {
                            AdaptiveText(stringResource(R.string.role_preview_partial))
                        }
                        if (packages.isEmpty()) {
                            AdaptiveText(stringResource(R.string.role_preview_empty_observation))
                        }
                        packages.forEach { pkg ->
                            val status = when {
                                comparison.sameQuery && pkg in beforeSet && pkg !in afterSet ->
                                    stringResource(R.string.role_preview_removed)
                                pkg in afterSet -> stringResource(R.string.role_preview_kept)
                                else -> stringResource(R.string.role_preview_original_only)
                            }
                            val defaultMarker = if (pkg in currentHolders)
                                " · " + stringResource(R.string.role_preview_current_default) else ""
                            AdaptiveText((packageLabels[pkg] ?: pkg) + " · " + status + defaultMarker)
                            AdaptiveText(pkg, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    AdaptiveText(stringResource(R.string.role_preview_not_pixel_verified))
                    SettingsFlatChoiceRow(
                        label = stringResource(R.string.role_preview_open_native),
                        selected = false,
                        onClick = {
                            SystemEffectPreview.launchSettings(context, kind, request.userId)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                AdaptiveText(stringResource(R.string.common_ok))
            }
        },
    )
}
