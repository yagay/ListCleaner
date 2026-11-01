package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isPackageScopedEntry

/** What the native button actually does, independently of the candidate source. */
internal enum class PreviewNativeAction {
    SYSTEM_SETTINGS, SYSTEM_PICKER, INTENT_TEST, CHOOSER_TEST, NONE
}

internal enum class PreviewAdapter {
    RESOLVER, ROLE_CONTROLLER, ENABLED_MANAGER, SYSTEM_SERVICES,
    PROVIDER_REGISTRY, DOCUMENTS_UI, HOST_PRIVATE, ROOT_COMPONENT_STATE,
}

/**
 * One contract for every Entry/Root class. Neither a test Intent nor a registration
 * inventory may claim to be the platform's final rendered picker list.
 */
internal data class PreviewContract(
    val adapter: PreviewAdapter,
    val nativeAction: PreviewNativeAction,
    val packageScoped: Boolean,
    val supportsLiveSystemList: Boolean,
    val supportsHistoricalEvidence: Boolean = false,
)

internal object PreviewContracts {
    fun forRequest(request: SystemProbeRequest): PreviewContract =
        request.entryKind?.let(::forEntry)
            ?: forRoot(requireNotNull(request.componentKind))

    fun forEntry(kind: IntentKind): PreviewContract {
        val adapter = when (kind.previewAuthority()) {
            PreviewAuthority.RESOLVER_CANDIDATES -> PreviewAdapter.RESOLVER
            PreviewAuthority.ROLE_CANDIDATES -> PreviewAdapter.ROLE_CONTROLLER
            PreviewAuthority.ENABLED_MANAGER -> PreviewAdapter.ENABLED_MANAGER
            PreviewAuthority.SERVICE_CANDIDATES -> PreviewAdapter.SYSTEM_SERVICES
            PreviewAuthority.PROVIDER_CANDIDATES -> PreviewAdapter.PROVIDER_REGISTRY
            PreviewAuthority.PICKER_PRIVATE -> PreviewAdapter.DOCUMENTS_UI
            PreviewAuthority.SHORTCUT_PRIVATE -> PreviewAdapter.HOST_PRIVATE
            PreviewAuthority.HOST_CANDIDATES -> PreviewAdapter.HOST_PRIVATE
        }
        val action = when (kind) {
            IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE ->
                PreviewNativeAction.CHOOSER_TEST
            IntentKind.SEND_TO, IntentKind.OPEN, IntentKind.EDIT,
            IntentKind.PICK, IntentKind.DEEP_LINK, IntentKind.DIAL,
            IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO,
            IntentKind.RECORD_AUDIO, IntentKind.WALLPAPER ->
                PreviewNativeAction.INTENT_TEST
            IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
            IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE ->
                PreviewNativeAction.SYSTEM_PICKER
            IntentKind.HOME, IntentKind.BROWSER, IntentKind.ASSISTANT,
            IntentKind.CALL_SCREENING, IntentKind.ACCESSIBILITY,
            IntentKind.PRINT, IntentKind.VPN, IntentKind.NFC_HCE,
            IntentKind.NOTIFICATION_LISTENER, IntentKind.DREAM,
            IntentKind.AUTOFILL -> PreviewNativeAction.SYSTEM_SETTINGS
            IntentKind.INPUT_METHOD -> PreviewNativeAction.SYSTEM_PICKER
            IntentKind.DOCUMENT_PROVIDER, IntentKind.PROCESS_TEXT,
            IntentKind.SHORTCUT_ITEM, IntentKind.LAUNCHER_SHORTCUT,
            IntentKind.CREDENTIAL_PROVIDER -> PreviewNativeAction.NONE
        }
        return PreviewContract(
            adapter, action, kind.isPackageScopedEntry(),
            supportsLiveSystemList = kind in setOf(
                IntentKind.ASSISTANT, IntentKind.HOME, IntentKind.BROWSER,
                IntentKind.CALL_SCREENING, IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT,
                IntentKind.CREATE_DOCUMENT, IntentKind.DOCUMENT_ROOT,
            ),
            supportsHistoricalEvidence = kind in setOf(
                IntentKind.DIRECT_SHARE, IntentKind.SHORTCUT_ITEM,
                IntentKind.DOCUMENT_ROOT, IntentKind.DOCUMENT_PROVIDER,
            ),
        )
    }

    fun forRoot(kind: CleanupKind): PreviewContract = PreviewContract(
        adapter = PreviewAdapter.ROOT_COMPONENT_STATE,
        nativeAction = when (kind) {
            CleanupKind.WIDGET -> PreviewNativeAction.SYSTEM_PICKER
            CleanupKind.LAUNCHER, CleanupKind.SHORTCUT ->
                PreviewNativeAction.CHOOSER_TEST
            CleanupKind.TILE, CleanupKind.CONTROLS, CleanupKind.AUTHENTICATOR ->
                PreviewNativeAction.NONE
        },
        packageScoped = false,
        supportsLiveSystemList = false,
    )
}
