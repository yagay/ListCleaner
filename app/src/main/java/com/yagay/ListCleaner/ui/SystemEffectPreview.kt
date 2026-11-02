package com.yagay.ListCleaner.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
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
    // Native chooser for Share; Browser uses its dedicated default-role selector.
    IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE ->
        SystemPreviewRoute.CHOOSER

    IntentKind.SEND_TO, IntentKind.OPEN, IntentKind.EDIT, IntentKind.PICK,
    IntentKind.DEEP_LINK, IntentKind.DIAL,
    IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO, IntentKind.RECORD_AUDIO,
    IntentKind.WALLPAPER -> SystemPreviewRoute.DIRECT

    IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
    IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE ->
        SystemPreviewRoute.PICKER
    // DocumentsProvider has no standalone public provider-list activity. Launch
    // the native DocumentsUI Open Document picker as the nearest real surface;
    // its locations/navigation panel exposes available document roots.
    IntentKind.DOCUMENT_PROVIDER -> SystemPreviewRoute.PICKER

    IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT,
    IntentKind.CALL_SCREENING, IntentKind.ACCESSIBILITY,
    IntentKind.PRINT, IntentKind.VPN, IntentKind.NFC_HCE,
    IntentKind.NOTIFICATION_LISTENER, IntentKind.DREAM,
    IntentKind.AUTOFILL -> SystemPreviewRoute.SETTINGS
    // Retired legacy kind: no system preview or cleanable route.
    IntentKind.CREDENTIAL_PROVIDER -> SystemPreviewRoute.UNAVAILABLE

    // The existing preview contract intentionally opens IME management settings.
    IntentKind.INPUT_METHOD -> SystemPreviewRoute.SETTINGS
    IntentKind.PROCESS_TEXT -> SystemPreviewRoute.CONTEXT_MENU

    // A dynamic shortcut is owned by the launcher/ShortcutService. A generic
    // implicit intent would show unrelated apps, not actual shortcuts.
    IntentKind.SHORTCUT_ITEM, IntentKind.LAUNCHER_SHORTCUT ->
        SystemPreviewRoute.UNAVAILABLE
}

internal fun systemPreviewRoute(kind: CleanupKind): SystemPreviewRoute = when (kind) {
    CleanupKind.LAUNCHER, CleanupKind.SHORTCUT -> SystemPreviewRoute.CHOOSER
    CleanupKind.WIDGET -> SystemPreviewRoute.WIDGET_PICKER
    CleanupKind.TILE, CleanupKind.CONTROLS, CleanupKind.AUTHENTICATOR ->
        SystemPreviewRoute.UNAVAILABLE
}

/** UI wording: a system settings page is NOT evidence of filtered candidates. */
internal fun previewRouteNote(kind: IntentKind): Int = when (kind) {
    IntentKind.CREDENTIAL_PROVIDER -> R.string.system_preview_unavailable
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
    IntentKind.ACCESSIBILITY, IntentKind.PRINT,
    IntentKind.VPN, IntentKind.NFC_HCE, IntentKind.NOTIFICATION_LISTENER,
    IntentKind.DREAM -> R.string.preview_settings_not_filtered
    else -> R.string.preview_scenario_not_final_ui
}

internal fun previewRouteNote(kind: CleanupKind): Int = when (kind) {
    CleanupKind.TILE -> R.string.preview_tile_no_public_editor
    CleanupKind.CONTROLS -> R.string.surface_controls_probe_notice
    CleanupKind.AUTHENTICATOR -> R.string.root_kind_authenticator_help
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
        scenario: SystemProbeScenario = defaultProbeScenario(kind),
        request: SystemProbeRequest? = null,
    ) {
        if (!checkUser(context, selectedUserId)) return
        val route = systemPreviewRoute(kind)
        if (route == SystemPreviewRoute.UNAVAILABLE ||
            route == SystemPreviewRoute.WIDGET_PICKER) {
            show(context, R.string.system_preview_unavailable)
            return
        }
        if (route == SystemPreviewRoute.CONTEXT_MENU) {
            runCatching {
                context.startActivity(Intent(context, TextSelectionProbeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Log.w("ListCleaner.SystemPreview", "TEXT_SELECTION_PROBE_FAILED", it)
                show(context, R.string.system_preview_failed)
            }
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
            launchSettings(context, kind, selectedUserId, request)
            return
        }
        val tracked = kind in setOf(
            IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
            IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_ROOT,
        )
        val sessionStore = if (tracked) PickerProbeSessionStore(context) else null
        runCatching {
            val target = buildIntent(context, kind, scenario, request)
            val launch = if (route == SystemPreviewRoute.CHOOSER ||
                (kind == IntentKind.OPEN && request?.directLaunch == true))
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
    fun launchSettings(
        context: Context, kind: IntentKind, selectedUserId: Int,
        request: SystemProbeRequest? = null,
    ) {
        if (AospProviderSettingsRoute.isProvider(kind)) {
            launchProviderSettings(context, kind, selectedUserId)
            return
        }
        if (!checkUser(context, selectedUserId)) return
        if (kind == IntentKind.ACCESSIBILITY) {
            if (request?.focusPackage != null) {
                // A row with a concrete service opens that service's detail page.
                launchAccessibilityServiceDetails(context, selectedUserId, request)
            } else if (OplusAccessibilityDownloadedRoute.supportsManufacturer(Build.MANUFACTURER)) {
                // Device capture on OxygenOS 16: Downloaded apps lives in an
                // OEM SubSettings fragment, NOT the public Accessibility landing page.
                launchOplusAccessibilityDownloaded(context, selectedUserId)
            } else {
                launchSettingsTargets(context, kind, settingsIntents(context, kind))
            }
            return
        }
        if (kind in setOf(IntentKind.ASSISTANT, IntentKind.BROWSER,
                IntentKind.HOME, IntentKind.CALL_SCREENING)) {
            launchRolePicker(context, kind, selectedUserId)
            return
        }
        launchSettingsTargets(context, kind, settingsIntents(context, kind))
    }

    /**
     * Open OxygenOS's actual "Downloaded apps" Accessibility list through its
     * captured Settings fragment. Never report a generic Settings launch as
     * proof of the downloaded-services list. The normal public AOSP route
     * continues to be used on other manufacturers.
     */
    private fun launchOplusAccessibilityDownloaded(context: Context, userId: Int) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val started = ComponentRootCommand.run(
                        OplusAccessibilityDownloadedRoute.rootLaunchCommand(userId)
                    )
                    if (started.timedOut || started.exitCode != 0 ||
                        !OplusAccessibilityDownloadedRoute.activityLaunchAccepted(started.output)) {
                        return@runCatching false to
                            "START_REJECTED exit=${started.exitCode} timedOut=${started.timedOut} " +
                            "result=${started.output.take(240)}"
                    }
                    // SubSettings can launch before its Fragment is attached.
                    // The launch is known; record whether the native fragment was
                    // actually observed rather than silently claiming success.
                    var observed = false
                    var latest = ""
                    repeat(2) { attempt ->
                        Thread.sleep(if (attempt == 0) 500 else 600)
                        val top = ComponentRootCommand.runReadOnly(
                            "dumpsys activity top | grep -F -m 2 " +
                                "'${OplusAccessibilityDownloadedRoute.FRAGMENT}'"
                        )
                        if (!top.timedOut) {
                            latest = top.output.take(240)
                            if (OplusAccessibilityDownloadedRoute.actualFragmentVisible(
                                top.output
                            )) {
                                observed = true
                                return@repeat
                            }
                        }
                    }
                    if (observed) {
                        true to "DOWNLOADED_FRAGMENT_VERIFIED"
                    } else {
                        true to "SUBSETTINGS_STARTED_FRAGMENT_UNVERIFIED probe=${latest.take(140)}"
                    }
                }.getOrElse { err ->
                    false to "ROOT_EXCEPTION ${err.javaClass.simpleName}: ${err.message?.take(110)}"
                }
            }
            if (outcome.first) {
                Log.i("ListCleaner.SystemPreview",
                    "ACCESSIBILITY_DOWNLOADED ${outcome.second}")
            } else {
                Log.w("ListCleaner.SystemPreview",
                    "ACCESSIBILITY_DOWNLOADED ${outcome.second}")
                show(context, R.string.preview_accessibility_downloaded_unavailable)
            }
        }
    }

    /**
     * Open the Settings detail/toggle page for a specific installed accessibility
     * service, not the parent Accessibility landing page. AOSP protects the detail
     * action with OPEN_ACCESSIBILITY_DETAILS_SETTINGS, so fall back to explicitly
     * authorized Root only when the ordinary Activity launch is blocked.
     */
    @Suppress("DEPRECATION")
    private fun launchAccessibilityServiceDetails(
        context: Context, userId: Int, request: SystemProbeRequest,
    ) {
        val component = AccessibilityServiceDetailRoute.component(
            request.focusPackage, request.focusClass,
        )
        if (component == null) {
            Log.w("ListCleaner.SystemPreview", "ACCESSIBILITY_DETAIL_INVALID_COMPONENT")
            show(context, R.string.preview_accessibility_detail_invalid)
            return
        }
        // Accessibility shortcut Activities are separate from installed
        // AccessibilityService implementations. Never guess which service a
        // shortcut controls, and never open another app's settings by package.
        val serviceManager = context.getSystemService(AccessibilityManager::class.java)
        val managerMatch = runCatching {
            serviceManager?.installedAccessibilityServiceList.orEmpty().any { info ->
                info.resolveInfo?.serviceInfo?.let { service ->
                    service.packageName == component.packageName &&
                        service.name == component.className
                } == true
            }
        }.getOrDefault(false)
        val pmMatch = runCatching {
            context.packageManager.queryIntentServices(
                Intent("android.accessibilityservice.AccessibilityService"),
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            ).any { info ->
                info.serviceInfo?.let { service ->
                    service.packageName == component.packageName &&
                        service.name == component.className
                } == true
            }
        }.getOrDefault(false)
        if (!managerMatch && !pmMatch) {
            Log.w("ListCleaner.SystemPreview",
                "ACCESSIBILITY_DETAIL_NOT_SERVICE component=${component.flattenToString()}")
            show(context, R.string.preview_accessibility_detail_not_service)
            return
        }

        // On AOSP the exported details Activity is a trampoline to SubSettings;
        // successful startActivity() may immediately redirect to the main list.
        // Always use one explicit, registered target and verify the final fragment.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val (verified, diagnostic) = withContext(Dispatchers.IO) {
                runCatching {
                    val found = ComponentRootCommand.runReadOnly(
                        AccessibilityServiceDetailRoute.discoverCommand(userId)
                    )
                    if (found.timedOut || found.exitCode != 0 ||
                        found.output.contains("[output truncated]") ||
                        found.output.contains("[output incomplete]")) {
                        return@runCatching false to
                            "DISCOVERY_FAILED exit=${found.exitCode} timeout=${found.timedOut}"
                    }
                    val activity = AccessibilityServiceDetailRoute.selectRegisteredDetails(
                        found.output
                    ) ?: return@runCatching false to
                        "NO_REGISTERED_DETAILS_ACTIVITY output=${found.output.take(170)}"
                    val launched = ComponentRootCommand.run(
                        AccessibilityServiceDetailRoute.rootLaunchCommand(
                            userId, component.flattenToString(), activity
                        )
                    )
                    if (launched.timedOut || launched.exitCode != 0 ||
                        !AccessibilityServiceDetailRoute.launchAccepted(
                            launched.output, activity
                        )) {
                        return@runCatching false to
                            "START_REJECTED component=${component.flattenToString()} " +
                            "activity=$activity exit=${launched.exitCode} " +
                            "result=${launched.output.take(230)}"
                    }
                    // The initial Activity immediately starts a settings fragment
                    // then finishes. Check the actual Settings fragment, not
                    // 'Status: ok' or the com.android.settings package.
                    var lastDump = ""
                    repeat(2) { attempt ->
                        Thread.sleep(if (attempt == 0) 450 else 650)
                        val top = ComponentRootCommand.runReadOnly(
                            "dumpsys activity top | grep -E -i " +
                                "'accessibility|subsettings|activity com.android.settings' | head -n 90"
                        )
                        if (!top.timedOut && top.exitCode == 0 &&
                            !top.output.contains("[output truncated]")) {
                            lastDump = top.output
                        }
                        if (AccessibilityServiceDetailRoute.detailFragmentVisible(lastDump)) {
                            return@runCatching true to
                                "DETAIL_FRAGMENT_VERIFIED target=${component.flattenToString()} " +
                                "activity=$activity"
                        }
                    }
                    val reason = if (AccessibilityServiceDetailRoute.parentAccessibilityVisible(lastDump)) {
                        "REDIRECTED_TO_ACCESSIBILITY_MAIN"
                    } else {
                        "DETAIL_FRAGMENT_NOT_VERIFIED"
                    }
                    false to "$reason target=${component.flattenToString()} " +
                        "activity=$activity top=${lastDump.take(300)}"
                }.getOrElse { failure ->
                    false to "ROOT_EXCEPTION ${failure.javaClass.simpleName}: " +
                        failure.message.orEmpty().take(120)
                }
            }
            if (verified) {
                Log.i("ListCleaner.SystemPreview", "ACCESSIBILITY_DETAIL $diagnostic")
            } else {
                Log.w("ListCleaner.SystemPreview", "ACCESSIBILITY_DETAIL $diagnostic")
                show(context, R.string.preview_accessibility_detail_unavailable)
            }
        }
    }

    /**
     * Root is used only for a concrete, system-registered PermissionController
     * DefaultAppActivity. It is not used to change a role holder or bypass user
     * confirmation. The launched Activity is verified by exact component name:
     * a normal parent Settings page is not reported as success.
     */
    private fun launchRolePicker(context: Context, kind: IntentKind, userId: Int) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val discovery = ComponentRootCommand.runReadOnly(
                        DefaultRolePickerBridge.discoverCommand(userId)
                    )
                    if (discovery.timedOut || discovery.exitCode != 0 ||
                        discovery.output.contains("[output truncated]") ||
                        discovery.output.contains("[output incomplete]")) {
                        return@runCatching RolePickerOutcome(
                            false, false,
                            "DISCOVERY_FAILED kind=$kind exit=${discovery.exitCode} " +
                                "timeout=${discovery.timedOut}"
                        )
                    }

                    val packages = ComponentRootCommand.runReadOnly(
                        DefaultRolePickerBridge.systemPackageQueryCommand(userId)
                    )
                    if (packages.timedOut || packages.exitCode != 0 ||
                        packages.output.contains("[output truncated]") ||
                        packages.output.contains("[output incomplete]")) {
                        return@runCatching RolePickerOutcome(
                            false, false,
                            "SYSTEM_PACKAGE_FAILED kind=$kind exit=${packages.exitCode}"
                        )
                    }
                    val component = DefaultRolePickerBridge.selectRegisteredComponent(
                        discovery.output,
                        DefaultRolePickerBridge.systemPackages(packages.output)
                    ) ?: return@runCatching RolePickerOutcome(
                        false, false, "COMPONENT_NOT_REGISTERED kind=$kind"
                    )

                    val launched = ComponentRootCommand.run(
                        DefaultRolePickerBridge.launchCommand(userId, component, kind)
                    )
                    if (launched.timedOut || launched.exitCode != 0 ||
                        !DefaultRolePickerBridge.launchAccepted(launched.output, component)) {
                        return@runCatching RolePickerOutcome(
                            false, false,
                            "START_REJECTED kind=$kind component=$component " +
                                "exit=${launched.exitCode} timeout=${launched.timedOut} " +
                                "reason=${launched.output.take(180)}"
                        )
                    }

                    // am start -W is not proof of the requested screen. Verify
                    // the foreground after the OS has resumed its target.
                    val foreground = ComponentRootCommand.runReadOnly(
                        "dumpsys activity activities"
                    )
                    if (foreground.timedOut || foreground.exitCode != 0 ||
                        foreground.output.contains("[output truncated]") ||
                        foreground.output.contains("[output incomplete]")) {
                        return@runCatching RolePickerOutcome(
                            false, true,
                            "FOREGROUND_QUERY_FAILED kind=$kind component=$component"
                        )
                    }
                    val verified = DefaultRolePickerBridge.foregroundMatches(
                        foreground.output, component
                    )
                    RolePickerOutcome(
                        verified, true,
                        if (verified) "VERIFIED kind=$kind component=$component"
                        else "REDIRECTED_OR_UNVERIFIED kind=$kind expected=$component " +
                            "foreground=${DefaultRolePickerBridge.foregroundComponent(foreground.output)}"
                    )
                }.getOrElse { failure ->
                    RolePickerOutcome(
                        false, false,
                        "ROOT_UNAVAILABLE kind=$kind error=${failure.javaClass.simpleName}"
                    )
                }
            }
            if (outcome.verified) {
                Log.i("ListCleaner.SystemPreview", "DEFAULT_ROLE_PICKER " + outcome.diagnostic)
                return@launch
            }
            Log.w("ListCleaner.SystemPreview", "DEFAULT_ROLE_PICKER " + outcome.diagnostic)

            if (outcome.started) {
                // We may already be in an OEM-redirected Activity. Don't start a
                // second settings screen and conceal the original redirect.
                show(context, R.string.preview_role_opened_not_verified)
                return@launch
            }
            // Explicitly identify the fallback as a parent Settings screen, not
            // as the actual default-app candidate picker.
            show(context, R.string.preview_role_parent_fallback)
            val fallback = when (kind) {
                IntentKind.HOME -> settingsIntents(context, kind).take(1)
                IntentKind.ASSISTANT, IntentKind.BROWSER, IntentKind.CALL_SCREENING ->
                    settingsIntents(context, kind).filterNot {
                        it.action == ACTION_MANAGE_DEFAULT_APP
                    }
                else -> emptyList()
            }
            launchSettingsTargets(context, kind, fallback)
        }
    }

    private data class RolePickerOutcome(
        val verified: Boolean,
        /** Android reported that the privileged target Activity started. */
        val started: Boolean,
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
        IntentKind.BROWSER -> listOf(
            ACTION_MANAGE_DEFAULT_APP, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        IntentKind.CALL_SCREENING -> listOf(
            ACTION_MANAGE_DEFAULT_APP, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
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
                if (action == ACTION_MANAGE_DEFAULT_APP &&
                    (kind == IntentKind.ASSISTANT || kind == IntentKind.BROWSER ||
                        kind == IntentKind.CALL_SCREENING)) {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    // Match AOSP Settings: dispatch the role-specific management
                    // activity to PermissionController, not a generic settings handler.
                    if (kind == IntentKind.BROWSER) {
                        // The public SDK does not expose PackageManager's
                        // permission-controller package accessor. Let Android
                        // resolve this role-specific management intent.
                    }
                    putExtra(EXTRA_ROLE_NAME,
                        when (kind) {
                            IntentKind.BROWSER -> ROLE_BROWSER
                            IntentKind.CALL_SCREENING -> ROLE_CALL_SCREENING
                            else -> ROLE_ASSISTANT
                        })
                }
            }
        }

    private const val ACTION_MANAGE_DEFAULT_APP = "android.intent.action.MANAGE_DEFAULT_APP"
    private const val EXTRA_ROLE_NAME = "android.intent.extra.ROLE_NAME"
    private const val ROLE_ASSISTANT = "android.app.role.ASSISTANT"
    private const val ROLE_BROWSER = "android.app.role.BROWSER"
    private const val ROLE_CALL_SCREENING = "android.app.role.CALL_SCREENING"

    /** Build the resolver/PackageManager QUERY, never a Settings navigation intent. */
    internal fun candidateIntent(
        context: Context, kind: IntentKind,
        scenario: SystemProbeScenario = defaultProbeScenario(kind),
        request: SystemProbeRequest? = null,
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
        else -> buildIntent(context, kind, scenario, request)
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
        request: SystemProbeRequest? = null,
    ): Intent = markedQueryIntent(candidateIntent(context, kind, scenario, request))

    /** Only PM read-only queries use the marker; do not attach it to native launches. */
    internal fun markedQueryIntent(intent: Intent): Intent = intent.apply {
        putExtra(RuntimeProtocol.EXTRA_MANAGER_PREVIEW_QUERY, true)
    }

    private fun buildIntent(
        context: Context, kind: IntentKind, scenario: SystemProbeScenario,
        request: SystemProbeRequest? = null,
    ): Intent = when (kind) {
        IntentKind.SHARE, IntentKind.DIRECT_SHARE -> {
            val type = previewMimeForScenario(kind, scenario, request)
            if (type.startsWith("text/")) {
                Intent(Intent.ACTION_SEND).apply {
                    this.type = type
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.system_preview_example_text))
                }
            } else {
                val uri = sampleFiles(context, scenario, type).first()
                Intent(Intent.ACTION_SEND).apply {
                    this.type = type
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Preview", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        IntentKind.SHARE_MULTIPLE -> {
            val type = previewMimeForScenario(kind, scenario, request)
            val uris = sampleFiles(context, scenario, type)
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                this.type = type
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
        IntentKind.PICK -> Intent(Intent.ACTION_PICK).apply {
            type = scenario.mimeType ?: "image/*"
        }
        IntentKind.EDIT -> Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(sampleFiles(context, scenario).first(),
                previewMimeForScenario(kind, scenario, request))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        IntentKind.OPEN -> request?.openPreset?.let(::scopedProbeUri)?.let { uri ->
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        } ?: Intent(Intent.ACTION_VIEW).apply {
            val type = previewMimeForScenario(kind, scenario, request)
            setDataAndType(sampleFiles(context, scenario, type).first(), type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        IntentKind.DEEP_LINK ->
            Intent(Intent.ACTION_VIEW, Uri.parse(
                (if (scenario == SystemProbeScenario.HTTP) "http://" else "https://") +
                    (com.yagay.ListCleaner.domain.normalizeBrowserHost(request?.browserHost) ?: "example.com") + "/"
            )).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        IntentKind.DIAL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:0000000000"))
        IntentKind.GET_CONTENT -> Intent(Intent.ACTION_GET_CONTENT).apply {
            type = previewMimeForScenario(kind, scenario, request)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        IntentKind.OPEN_DOCUMENT, IntentKind.DOCUMENT_PROVIDER ->
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = if (kind == IntentKind.DOCUMENT_PROVIDER) "*/*"
                    else previewMimeForScenario(kind, scenario, request)
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        IntentKind.CREATE_DOCUMENT -> Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            val selectedMime = previewMimeForScenario(kind, scenario, request)
            type = selectedMime
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE,
                previewDocumentName(selectedMime, context.getString(R.string.system_preview_file_name)))
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
        IntentKind.BROWSER -> scenarioIntent(context, kind, scenario)
        IntentKind.HOME, IntentKind.ASSISTANT,
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

    /**
     * Use the selected MIME to name the test attachment, instead of handing
     * Android a .txt for Word, EPUB, ZIP or JPEG previews. PDF/PNG/JPEG have
     * valid minimal content; all other formats are harmless placeholders and
     * must not be mistaken for a complete document inside a third-party app.
     */
    private fun sampleFiles(
        context: Context, scenario: SystemProbeScenario, selectedMime: String? = null,
    ): List<Uri> {
        val mime = selectedMime?.substringBefore(';')?.lowercase()
            ?: scenario.mimeType ?: "text/plain"
        val folder = File(context.cacheDir, "system-preview").apply { mkdirs() }
        val extension = previewExtensionForMime(mime)
        return (1..2).map { index ->
            val file = File(folder, "list-cleaner-demo-$index.$extension")
            when (mime) {
                "image/png", "image/jpeg" -> {
                    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                    try {
                        file.outputStream().use {
                            bitmap.compress(
                                if (mime == "image/jpeg") Bitmap.CompressFormat.JPEG
                                else Bitmap.CompressFormat.PNG, 100, it
                            )
                        }
                    } finally { bitmap.recycle() }
                }
                "application/pdf" -> {
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
        kind == IntentKind.AUTOFILL

    internal fun pickerComponent(kind: IntentKind): String {
        require(isProvider(kind))
        // Modern AOSP merges Credential Manager and Autofill in one picker.
        return COMBINED_PICKER
    }

    internal fun fallbackActions(kind: IntentKind): List<String> = when (kind) {
        IntentKind.AUTOFILL -> listOf(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)
        else -> emptyList()
    }
}
