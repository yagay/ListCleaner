package com.yagay.ListCleaner.domain

/**
 * System-managed service surfaces that Android discovers through PackageManager service queries.
 *
 * These definitions are shared by manager discovery and the system_server filter so both sides use
 * exactly the same action/permission contract. Filtering is non-destructive: components remain
 * enabled and are only omitted from matching discovery results while the rule is active.
 */
data class SystemServiceEntryDefinition(
    val kind: IntentKind,
    val action: String,
    val requiredPermission: String? = null,
    val alternativePermissions: Set<String> = emptySet(),
) {
    fun acceptsPermission(permission: String?): Boolean =
        requiredPermission == null || permission == requiredPermission || permission in alternativePermissions
}

val SYSTEM_SERVICE_ENTRY_DEFINITIONS: List<SystemServiceEntryDefinition> = listOf(
    SystemServiceEntryDefinition(
        IntentKind.INPUT_METHOD,
        "android.view.InputMethod",
        "android.permission.BIND_INPUT_METHOD",
    ),
    SystemServiceEntryDefinition(
        IntentKind.AUTOFILL,
        "android.service.autofill.AutofillService",
        "android.permission.BIND_AUTOFILL_SERVICE",
        alternativePermissions = setOf("android.permission.BIND_AUTOFILL"),
    ),
    SystemServiceEntryDefinition(
        IntentKind.CREDENTIAL_PROVIDER,
        "android.service.credentials.CredentialProviderService",
        "android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.NOTIFICATION_LISTENER,
        "android.service.notification.NotificationListenerService",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.ACCESSIBILITY,
        "android.accessibilityservice.AccessibilityService",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.VPN,
        "android.net.VpnService",
        "android.permission.BIND_VPN_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.PRINT,
        "android.printservice.PrintService",
        "android.permission.BIND_PRINT_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.WALLPAPER,
        "android.service.wallpaper.WallpaperService",
        "android.permission.BIND_WALLPAPER",
    ),
    SystemServiceEntryDefinition(
        IntentKind.DREAM,
        "android.service.dreams.DreamService",
        "android.permission.BIND_DREAM_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.NFC_HCE,
        "android.nfc.cardemulation.action.HOST_APDU_SERVICE",
        "android.permission.BIND_NFC_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.NFC_HCE,
        "android.nfc.cardemulation.action.OFF_HOST_APDU_SERVICE",
        "android.permission.BIND_NFC_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.CALL_SCREENING,
        "android.telecom.CallScreeningService",
        "android.permission.BIND_SCREENING_SERVICE",
    ),
)

fun systemServiceEntryKind(action: String?): IntentKind? =
    SYSTEM_SERVICE_ENTRY_DEFINITIONS.firstOrNull { it.action == action }?.kind

fun IntentKind.isSystemServiceEntry(): Boolean =
    SYSTEM_SERVICE_ENTRY_DEFINITIONS.any { it.kind == this }

fun IntentKind.isSpecialEntrySurface(): Boolean = when (this) {
    IntentKind.SHORTCUT_ITEM,
    IntentKind.DIRECT_SHARE,
    IntentKind.DOCUMENT_PROVIDER -> true
    else -> isSystemServiceEntry()
}
