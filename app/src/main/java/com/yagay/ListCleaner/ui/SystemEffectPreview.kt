package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.util.Log
import android.widget.Toast
import com.yagay.ListCleaner.data.ComponentRootCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.RuntimeProtocol
import java.io.File

/** Routes for a real OS interface, rather than a local reconstruction of its filtered results. */
/** Launch type describes the actual Android operation, not a simulated cleaned list. */
internal enum class SystemPreviewRoute {
    CHOOSER, DIRECT, PICKER, SETTINGS, INPUT_METHOD_PICKER, CONTEXT_MENU, WIDGET_PICKER, UNAVAILABLE
}

internal fun systemPreviewRoute(kind: IntentKind): SystemPreviewRoute = when (kind) {
    // ACTION_CHOOSER is appropriate for explicit sharing, not for default-app selection.
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE ->
        SystemPreviewRoute.CHOOSER

    IntentKind.SEND_TO, IntentKind.OPEN, IntentKind.DEEP_LINK, IntentKind.DIAL,
    IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO, IntentKind.RECORD_AUDIO,
    IntentKind.WALLPAPER -> SystemPreviewRoute.DIRECT

    IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
    IntentKind.DOCUMENT_PROVIDER, IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE ->
        SystemPreviewRoute.PICKER

    IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT,
    IntentKind.CALL_SCREENING, IntentKind.ACCESSIBILITY,
    IntentKind.PRINT, IntentKind.VPN, IntentKind.NFC_HCE,
    IntentKind.NOTIFICATION_LISTENER, IntentKind.DREAM,
    // Credential/Autofill settings below are related navigation only, not a
    // privileged request to enable a provider from another package.
    IntentKind.CREDENTIAL_PROVIDER, IntentKind.AUTOFILL ->
        SystemPreviewRoute.SETTINGS

    IntentKind.INPUT_METHOD -> SystemPreviewRoute.INPUT_METHOD_PICKER
    IntentKind.PROCESS_TEXT -> SystemPreviewRoute.CONTEXT_MENU

    // A dynamic shortcut is owned by the launcher/ShortcutService. A generic
    // implicit intent would show unrelated apps, not actual shortcuts.
    IntentKind.SHORTCUT_ITEM, IntentKind.LAUNCHER_SHORTCUT ->
        SystemPreviewRoute.UNAVAILABLE
}

internal fun systemPreviewRoute(kind: CleanupKind): SystemPreviewRoute = when (kind) {
    CleanupKind.LAUNCHER, CleanupKind.SHORTCUT -> SystemPreviewRoute.CHOOSER
    CleanupKind.WIDGET -> SystemPreviewRoute.WIDGET_PICKER
    CleanupKind.TILE -> SystemPreviewRoute.UNAVAILABLE
}

/** An extra scenario tester never replaces an Android role/default-app settings UI. */
internal fun supportsScenarioTest(kind: IntentKind): Boolean = kind in setOf(
    IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT
)

/** UI wording: a system settings page is NOT evidence of filtered candidates. */
internal fun previewRouteNote(kind: IntentKind): Int = when (kind) {
    IntentKind.CREDENTIAL_PROVIDER -> R.string.preview_credential_navigation
    IntentKind.AUTOFILL -> R.string.preview_autofill_navigation
    IntentKind.ASSISTANT -> R.string.preview_assistant_role_picker
    IntentKind.HOME, IntentKind.BROWSER,
    IntentKind.CALL_SCREENING -> R.string.preview_default_apps_settings
    IntentKind.INPUT_METHOD -> R.string.preview_ime_native_picker
    IntentKind.PROCESS_TEXT -> R.string.preview_process_text_selection
    IntentKind.SHORTCUT_ITEM, IntentKind.LAUNCHER_SHORTCUT ->
        R.string.preview_shortcut_observation
    IntentKind.DOCUMENT_PROVIDER, IntentKind.DOCUMENT_ROOT,
    IntentKind.PHOTO_SOURCE -> R.string.preview_provider_picker_not_inventory
    IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY, IntentKind.PRINT,
    IntentKind.VPN, IntentKind.NFC_HCE, IntentKind.NOTIFICATION_LISTENER,
    IntentKind.DREAM -> R.string.preview_settings_not_filtered
    else -> R.string.preview_scenario_not_final_ui
}

internal fun previewRouteNote(kind: CleanupKind): Int = when (kind) {
    CleanupKind.TILE -> R.string.preview_tile_no_public_editor
    CleanupKind.WIDGET -> R.string.preview_widget_temporary_host
    CleanupKind.LAUNCHER, CleanupKind.SHORTCUT ->
        R.string.preview_launcher_chooser_not_real_host
}

/**
 * This starts a real Android chooser, picker, or settings Activity. It never evaluates the
 * user's rule set as if it were a system result, sends data, writes to a returned URI,
 * alters a component, or calls a third-party app directly.
 *
 * Cross-user startActivityAsUser requires privileged OS permissions. Do not silently
 * preview the personal user's panel while the work profile is selected.
 */
internal object SystemEffectPreview {
    fun launch(
        context: Context, kind: IntentKind, selectedUserId: Int,
        scenario: SystemProbeScenario = defaultProbeScenario(kind)
    ) {
        if (!checkUser(context, selectedUserId)) return
        val route = systemPreviewRoute(kind)
        if (route == SystemPreviewRoute.UNAVAILABLE ||
            route == SystemPreviewRoute.WIDGET_PICKER ||
            route == SystemPreviewRoute.CONTEXT_MENU) {
            show(context, R.string.system_preview_unavailable)
            return
        }
        if (route == SystemPreviewRoute.INPUT_METHOD_PICKER) {
            runCatching {
                context.getSystemService(InputMethodManager::class.java)
                    ?.showInputMethodPicker() ?: error("No input method manager")
            }.onFailure {
                Log.w("ListCleaner.SystemPreview", "IME_PICKER_FAILED " + it.javaClass.simpleName)
                show(context, R.string.system_preview_failed)
            }
            return
        }
        if (route == SystemPreviewRoute.SETTINGS) {
            launchSettings(context, kind, selectedUserId)
            return
        }
        val tracked = kind in setOf(
            IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
            IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_ROOT,
        )
        val sessionStore = if (tracked) PickerProbeSessionStore(context) else null
        runCatching {
            val target = buildIntent(context, kind, scenario)
            val launch = if (route == SystemPreviewRoute.CHOOSER)
                Intent.createChooser(target, context.getString(R.string.system_preview_chooser_title))
            else target
            // Record before launch so a fast DocumentsUI hook cannot race the marker.
            sessionStore?.begin(PickerProbeSession(
                kind, scenario, selectedUserId, System.currentTimeMillis()
            ))
            context.startActivity(launch)
        }.onFailure { error ->
            sessionStore?.clear()
            if (error is ActivityNotFoundException || error is SecurityException) {
                show(context, R.string.system_preview_no_handler)
            } else {
                show(context, R.string.system_preview_failed)
            }
        }
    }

    /** Open only supported, exported Android settings actions; never assume an OEM
     * retains an AOSP-specific Activity class or pretend that this is the final list.
     */
    fun launchSettings(context: Context, kind: IntentKind, selectedUserId: Int) {
        if (AospProviderSettingsRoute.isProvider(kind)) {
            launchProviderSettings(context, kind, selectedUserId)
            return
        }
        if (!checkUser(context, selectedUserId)) return
        val targets = settingsIntents(context, kind)
        if (kind == IntentKind.ASSISTANT) {
            launchAssistantRolePicker(context, selectedUserId)
            return
        }
        launchSettingsTargets(context, kind, targets)
    }

    /**
     * Open the actual role chooser, not the ManageAssist parent.
     * Use the existing user-approved Root facility for discovery AND launch.
     * Never interpret ordinary PackageManager visibility as proof that the
     * privileged PermissionController Activity does not exist.
     */
    private fun launchAssistantRolePicker(context: Context, userId: Int) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val discovery = ComponentRootCommand.runReadOnly(
                        AssistantRolePickerBridge.discoverCommand(userId)
                    )
                    if (discovery.exitCode != 0 || discovery.timedOut ||
                        discovery.output.contains("[output truncated]")) {
                        return@runCatching AssistantPickerOutcome(
                            R.string.preview_assistant_discovery_failed,
                            "DISCOVERY_FAILED exit=${discovery.exitCode} " +
                                "timeout=${discovery.timedOut} " +
                                discovery.output.take(350)
                        )
                    }
                    val systemPackagesResult = ComponentRootCommand.runReadOnly(
                        AssistantRolePickerBridge.systemPackageQueryCommand(userId)
                    )
                    if (systemPackagesResult.exitCode != 0 || systemPackagesResult.timedOut ||
                        systemPackagesResult.output.contains("[output truncated]")) {
                        return@runCatching AssistantPickerOutcome(
                            R.string.preview_assistant_discovery_failed,
                            "SYSTEM_PACKAGE_QUERY_FAILED exit=${systemPackagesResult.exitCode} " +
                                systemPackagesResult.output.take(350)
                        )
                    }
                    val registeredSystems = AssistantRolePickerBridge.systemPackages(
                        systemPackagesResult.output
                    )
                    val component = AssistantRolePickerBridge.selectRegisteredComponent(
                        discovery.output, registeredSystems
                    ) ?: return@runCatching AssistantPickerOutcome(
                        R.string.preview_assistant_missing_component,
                        "NO_ROLE_COMPONENT " + discovery.output.take(550)
                    )
                    val started = ComponentRootCommand.run(
                        AssistantRolePickerBridge.launchCommand(userId, component)
                    )
                    if (started.timedOut || started.exitCode != 0 ||
                        !AssistantRolePickerBridge.launchAccepted(started.output, component)) {
                        return@runCatching AssistantPickerOutcome(
                            R.string.preview_assistant_launch_failed,
                            "LAUNCH_FAILED component=$component exit=${started.exitCode} " +
                                "timeout=${started.timedOut} " +
                                started.output.take(550)
                        )
                    }
                    // Verify the *foreground*, not only am start's exit code.
                    val foreground = ComponentRootCommand.runReadOnly(
                        "dumpsys activity activities"
                    )
                    if (foreground.exitCode != 0 || foreground.timedOut) {
                        return@runCatching AssistantPickerOutcome(
                            R.string.preview_assistant_not_verified,
                            "FOREGROUND_QUERY_FAILED component=$component"
                        )
                    }
                    val actual = AssistantRolePickerBridge.foregroundPackage(
                        foreground.output
                    )
                    when {
                        AssistantRolePickerBridge.foregroundMatches(
                            foreground.output, component
                        ) -> AssistantPickerOutcome(
                            null, "VERIFIED component=$component foreground=$actual"
                        )
                        actual?.contains("settings", ignoreCase = true) == true ->
                            AssistantPickerOutcome(
                                R.string.preview_assistant_redirected,
                                "REDIRECTED component=$component foreground=$actual"
                            )
                        else -> AssistantPickerOutcome(
                            R.string.preview_assistant_not_verified,
                            "FOREGROUND_UNVERIFIED component=$component foreground=$actual"
                        )
                    }
                }.getOrElse { failure ->
                    AssistantPickerOutcome(
                        R.string.preview_assistant_discovery_failed,
                        "ROOT_EXCEPTION " + failure.javaClass.simpleName +
                            " message=" + failure.message.orEmpty().take(120)
                    )
                }
            }
            if (outcome.messageRes == null) {
                Log.i("ListCleaner.SystemPreview", "ASSISTANT_ROLE_PICKER " + outcome.diagnostic)
            } else {
                Log.w("ListCleaner.SystemPreview", "ASSISTANT_ROLE_PICKER " + outcome.diagnostic)
                show(context, outcome.messageRes)
            }
        }
    }

    private data class AssistantPickerOutcome(
        val messageRes: Int?,
        val diagnostic: String,
    )

    /**
     * AOSP Settings opens the combined Autofill/Credential chooser with an explicit
     * CredentialsPickerActivity Intent (DefaultCombinedPreferenceController).
     * Its AndroidManifest marks the Activity exported. An OEM can remove or
     * restrict it; in that case use only documented public Settings screens.
     * Never invoke provider enablement actions on behalf of another package.
     */
    fun launchProviderSettings(context: Context, kind: IntentKind, selectedUserId: Int) {
        if (!AospProviderSettingsRoute.isProvider(kind)) return
        if (!checkUser(context, selectedUserId)) return
        val component = AospProviderSettingsRoute.pickerComponent(kind)
        val picker = android.content.ComponentName(
            AospProviderSettingsRoute.SETTINGS_PACKAGE, component
        )
        val info = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(picker, 0)
        }.getOrNull()
        if (info != null && info.exported && info.permission.isNullOrEmpty()) {
            try {
                context.startActivity(Intent(Intent.ACTION_MAIN)
                    .setComponent(picker)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Log.i("ListCleaner.SystemPreview",
                    "AOSP_PROVIDER_PICKER_LAUNCHED kind=$kind component=$component")
                return
            } catch (failure: ActivityNotFoundException) {
                Log.w("ListCleaner.SystemPreview",
                    "AOSP_PROVIDER_PICKER_MISSING kind=$kind component=$component")
            } catch (failure: SecurityException) {
                Log.w("ListCleaner.SystemPreview",
                    "AOSP_PROVIDER_PICKER_BLOCKED kind=$kind component=$component")
            } catch (failure: RuntimeException) {
                Log.w("ListCleaner.SystemPreview",
                    "AOSP_PROVIDER_PICKER_FAILED kind=$kind error=${failure.javaClass.simpleName}")
            }
        } else {
            Log.i("ListCleaner.SystemPreview",
                "AOSP_PROVIDER_PICKER_UNAVAILABLE kind=$kind found=${info != null} " +
                    "exported=${info?.exported} permission=${info?.permission}")
        }
        // Public, non-provider-specific Settings pages remain reachable even
        // on customized ROMs. These are not misrepresented as a chooser.
        launchSettingsTargets(context, kind,
            AospProviderSettingsRoute.fallbackActions(kind).map {
                Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }, firstIndex = 1)
    }
    /** Explicit opt-in fallback; never open the wrong page automatically. */
    fun launchAssistantSettings(context: Context, userId: Int) {
        if (!checkUser(context, userId)) return
        launchSettingsTargets(context, IntentKind.ASSISTANT,
            settingsIntents(context, IntentKind.ASSISTANT).drop(1),
            firstIndex = 1)
    }

    private fun launchSettingsTargets(
        context: Context, kind: IntentKind, targets: List<Intent>,
        firstIndex: Int = 0
    ) {
        if (targets.isEmpty()) {
            show(context, R.string.system_preview_unavailable)
            return
        }
        for ((offset, candidate) in targets.withIndex()) {
            val index = firstIndex + offset
            val action = candidate.action.orEmpty()
            try {
                context.startActivity(candidate)
                Log.i("ListCleaner.SystemPreview",
                    "SETTINGS_LAUNCHED kind=$kind action=$action fallback=${index != 0}")
                if (index > 0 && kind != IntentKind.ASSISTANT) {
                    show(context, R.string.preview_settings_fallback)
                }
                return
            } catch (failure: ActivityNotFoundException) {
                Log.w("ListCleaner.SystemPreview",
                    "SETTINGS_UNSUPPORTED kind=$kind action=$action")
            } catch (failure: SecurityException) {
                Log.w("ListCleaner.SystemPreview",
                    "SETTINGS_BLOCKED kind=$kind action=$action")
            } catch (failure: RuntimeException) {
                Log.w("ListCleaner.SystemPreview",
                    "SETTINGS_FAILED kind=$kind action=$action error=${failure.javaClass.simpleName}")
            }
        }
        show(context, R.string.system_preview_no_handler)
    }

    internal fun settingsActions(kind: IntentKind): List<String> = when (kind) {
        IntentKind.HOME -> listOf(
            Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        IntentKind.ASSISTANT -> listOf(
            ACTION_MANAGE_DEFAULT_APP,
            Settings.ACTION_VOICE_INPUT_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        IntentKind.BROWSER, IntentKind.CALL_SCREENING ->
            listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        IntentKind.INPUT_METHOD -> listOf(Settings.ACTION_INPUT_METHOD_SETTINGS)
        IntentKind.ACCESSIBILITY -> listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        IntentKind.PRINT -> listOf(Settings.ACTION_PRINT_SETTINGS)
        IntentKind.VPN -> listOf(Settings.ACTION_VPN_SETTINGS)
        IntentKind.NFC_HCE -> listOf(Settings.ACTION_NFC_PAYMENT_SETTINGS)
        IntentKind.NOTIFICATION_LISTENER ->
            listOf(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        IntentKind.DREAM -> listOf(Settings.ACTION_DREAM_SETTINGS)
        else -> emptyList()
    }

    /** Navigation only; never impersonate a credential/autofill service package. */
    internal fun settingsIntents(context: Context, kind: IntentKind): List<Intent> =
        settingsActions(kind).map { action ->
            Intent(action).apply {
                if (kind == IntentKind.ASSISTANT && action == ACTION_MANAGE_DEFAULT_APP) {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    putExtra(EXTRA_ROLE_NAME, ROLE_ASSISTANT)
                }
            }
        }

    private const val ACTION_MANAGE_DEFAULT_APP = "android.intent.action.MANAGE_DEFAULT_APP"
    private const val EXTRA_ROLE_NAME = "android.intent.extra.ROLE_NAME"
    private const val ROLE_ASSISTANT = "android.app.role.ASSISTANT"

    /** Build the resolver/PackageManager QUERY, never a Settings navigation intent. */
    internal fun candidateIntent(
        context: Context, kind: IntentKind,
        scenario: SystemProbeScenario = defaultProbeScenario(kind),
    ): Intent = when (kind) {
        IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT ->
            scenarioIntent(context, kind, scenario)
        IntentKind.CALL_SCREENING -> Intent(kind.action)
        IntentKind.PROCESS_TEXT -> Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT,
                context.getString(R.string.system_preview_example_text))
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
        else -> buildIntent(context, kind, scenario)
    }

    /**
     * Optional implicit-Intent test beside the authoritative default-app settings.
     * It can launch a chosen app, so it is never used for passive state inspection.
     */
    fun launchScenario(
        context: Context, kind: IntentKind, selectedUserId: Int,
        scenario: SystemProbeScenario = defaultProbeScenario(kind),
    ) {
        if (!supportsScenarioTest(kind)) return
        if (!checkUser(context, selectedUserId)) return
        runCatching {
            val target = scenarioIntent(context, kind, scenario)
            context.startActivity(Intent.createChooser(
                target, context.getString(R.string.system_preview_chooser_title)
            ))
        }.onFailure { failure ->
            show(context, if (failure is ActivityNotFoundException || failure is SecurityException)
                R.string.system_preview_no_handler else R.string.system_preview_failed)
        }
    }

    fun launch(context: Context, kind: CleanupKind, selectedUserId: Int) {
        if (!checkUser(context, selectedUserId)) return
        if (systemPreviewRoute(kind) != SystemPreviewRoute.CHOOSER) {
            show(context, R.string.system_preview_root_unavailable)
            return
        }
        val target = when (kind) {
            CleanupKind.LAUNCHER ->
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            CleanupKind.SHORTCUT ->
                Intent(Intent.ACTION_CREATE_SHORTCUT)
            else -> return
        }
        runCatching {
            context.startActivity(Intent.createChooser(
                target, context.getString(R.string.system_preview_chooser_title)
            ))
        }.onFailure { error ->
            show(context, if (error is ActivityNotFoundException || error is SecurityException)
                R.string.system_preview_no_handler else R.string.system_preview_failed)
        }
    }

    private fun checkUser(context: Context, requested: Int): Boolean {
        if (requested == AndroidUid.userId(Process.myUid())) return true
        show(context, R.string.system_preview_other_space)
        return false
    }

    private fun show(context: Context, resourceId: Int) =
        Toast.makeText(context, resourceId, Toast.LENGTH_LONG).show()

    /** Read-only PM query uses resolver semantics, never the destination settings page. */
    internal fun queryIntent(
        context: Context, kind: IntentKind,
        scenario: SystemProbeScenario = defaultProbeScenario(kind),
    ): Intent = markedQueryIntent(candidateIntent(context, kind, scenario))

    /** Only PM read-only queries use the marker; do not attach it to native launches. */
    internal fun markedQueryIntent(intent: Intent): Intent = intent.apply {
        putExtra(RuntimeProtocol.EXTRA_MANAGER_PREVIEW_QUERY, true)
    }

    private fun buildIntent(
        context: Context, kind: IntentKind, scenario: SystemProbeScenario
    ): Intent = when (kind) {
        IntentKind.SHARE, IntentKind.DIRECT_SHARE -> {
            if (scenario == SystemProbeScenario.TEXT || kind == IntentKind.DIRECT_SHARE) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.system_preview_example_text))
                }
            } else {
                val uri = sampleFiles(context, scenario).first()
                Intent(Intent.ACTION_SEND).apply {
                    type = requireNotNull(scenario.mimeType)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Preview", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        IntentKind.SHARE_MULTIPLE -> sampleFiles(context, scenario).let { uris ->
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = scenario.mimeType ?: "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver,
                    context.getString(R.string.system_preview_chooser_title), uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
        }
        IntentKind.SEND_TO -> Intent(Intent.ACTION_SENDTO,
            if (scenario == SystemProbeScenario.SMS) Uri.parse("smsto:0000000000")
            else Uri.parse("mailto:preview@example.invalid"))
        IntentKind.OPEN -> Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(sampleFiles(context, scenario).first(),
                scenario.mimeType ?: "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        IntentKind.DEEP_LINK ->
            Intent(Intent.ACTION_VIEW, Uri.parse(
                if (scenario == SystemProbeScenario.HTTP) "http://example.com/"
                else "https://example.com/"
            )).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        IntentKind.DIAL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:0000000000"))
        IntentKind.GET_CONTENT -> Intent(Intent.ACTION_GET_CONTENT).apply {
            type = scenario.mimeType ?: "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        IntentKind.OPEN_DOCUMENT, IntentKind.DOCUMENT_PROVIDER ->
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = if (kind == IntentKind.DOCUMENT_PROVIDER) "*/*"
                    else scenario.mimeType ?: "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        IntentKind.CREATE_DOCUMENT -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = scenario.mimeType ?: "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, context.getString(R.string.system_preview_file_name))
        }
        IntentKind.DOCUMENT_ROOT -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        IntentKind.PHOTO_SOURCE -> if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
        } else Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        IntentKind.CAPTURE_IMAGE -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        IntentKind.CAPTURE_VIDEO -> Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        IntentKind.RECORD_AUDIO -> Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT,
        IntentKind.CALL_SCREENING -> error("System roles use settingsActions for UI and candidateIntent for candidates")
        IntentKind.WALLPAPER -> Intent(Intent.ACTION_SET_WALLPAPER)
        IntentKind.INPUT_METHOD, IntentKind.ACCESSIBILITY, IntentKind.PRINT,
        IntentKind.VPN, IntentKind.NFC_HCE, IntentKind.NOTIFICATION_LISTENER,
        IntentKind.DREAM -> error("Manager settings are not resolver candidate Intents")
        // For a third-party manager these are only related settings. Never use
        // ACTION_REQUEST_SET_AUTOFILL_SERVICE/ACTION_CREDENTIAL_PROVIDER with
        // another package; those actions request a provider for the caller.
        IntentKind.AUTOFILL, IntentKind.CREDENTIAL_PROVIDER ->
            error("Provider settings are not resolver candidate Intents")
        IntentKind.PROCESS_TEXT, IntentKind.SHORTCUT_ITEM,
        IntentKind.LAUNCHER_SHORTCUT ->
            error("No launchable system preview for this entry")
    }

    private fun scenarioIntent(
        context: Context, kind: IntentKind, scenario: SystemProbeScenario
    ): Intent = when (kind) {
        IntentKind.HOME -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        IntentKind.ASSISTANT -> Intent(Intent.ACTION_ASSIST)
        IntentKind.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse(
            if (scenario == SystemProbeScenario.HTTP) "http://example.com/"
            else "https://example.com/"
        )).addCategory(Intent.CATEGORY_BROWSABLE)
        else -> error("No scenario test")
    }

    /** Small valid files so matching MIME types correspond to real, safe test inputs. */
    private fun sampleFiles(context: Context, scenario: SystemProbeScenario): List<Uri> {
        val folder = File(context.cacheDir, "system-preview").apply { mkdirs() }
        return (1..2).map { index ->
            val extension = when (scenario) {
                SystemProbeScenario.IMAGE -> "png"
                SystemProbeScenario.PDF -> "pdf"
                else -> "txt"
            }
            val file = File(folder, "list-cleaner-demo-"+index+"."+extension)
            when (scenario) {
                SystemProbeScenario.IMAGE -> {
                    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                    try {
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
                SystemProbeScenario.PDF -> {
                    val document = PdfDocument()
                    try {
                        val page = document.startPage(
                            PdfDocument.PageInfo.Builder(100, 100, 1).create()
                        )
                        document.finishPage(page)
                        file.outputStream().use(document::writeTo)
                    } finally { document.close() }
                }
                else -> file.writeText(context.getString(R.string.system_preview_example_text))
            }
            FileProvider.getUriForFile(context, context.packageName + ".preview-files", file)
        }
    }
}

/** Route taken from AOSP Settings DefaultCombinedPreferenceController and manifest. */
internal object AospProviderSettingsRoute {
    internal const val SETTINGS_PACKAGE = "com.android.settings"
    internal const val COMBINED_PICKER =
        "com.android.settings.applications.credentials.CredentialsPickerActivity"

    internal fun isProvider(kind: IntentKind): Boolean =
        kind == IntentKind.AUTOFILL || kind == IntentKind.CREDENTIAL_PROVIDER

    internal fun pickerComponent(kind: IntentKind): String {
        require(isProvider(kind))
        // Modern AOSP merges Credential Manager and Autofill in one picker.
        return COMBINED_PICKER
    }

    internal fun fallbackActions(kind: IntentKind): List<String> = when (kind) {
        IntentKind.CREDENTIAL_PROVIDER -> listOf(
            Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS)
        IntentKind.AUTOFILL -> listOf(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)
        else -> emptyList()
    }
}
