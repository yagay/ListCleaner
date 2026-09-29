package com.yagay.ListCleaner.xposed

/**
 * Small reflection-layout helpers for system_server hooks.
 *
 * Internal PackageManager/ShortcutService methods often carry the original caller UID explicitly.
 * Binder.getCallingUid() is not authoritative after framework code clears/restores Binder identity,
 * so hooks should prefer the explicit argument whenever the known signature exposes one.
 */
internal object HookCallIdentity {
    data class ShortcutCall(
        val callerUid: Int,
        val callingPackage: String?,
        val queryFlags: Int?,
        val explicitCallerUid: Boolean,
    )

    fun serviceCallerUid(
        methodName: String,
        parameterTypeNames: List<String>,
        args: List<Any?>,
        binderUid: Int,
    ): Int {
        if (!methodName.endsWith("Internal")) return binderUid
        val intentIndex = parameterTypeNames.indexOf("android.content.Intent")
        if (intentIndex < 0) return binderUid
        val ints = parameterTypeNames.indices.filter { index ->
            index > intentIndex && parameterTypeNames[index] == "int"
        }
        // AOSP queryIntentServicesInternal(..., int userId, int callingUid, ...)
        val callerIndex = ints.getOrNull(1) ?: return binderUid
        return args.getOrNull(callerIndex) as? Int ?: binderUid
    }

    fun shortcutCall(
        parameterTypeNames: List<String>,
        args: List<Any?>,
        binderUid: Int,
    ): ShortcutCall {
        val callingPackageIndex = parameterTypeNames.indexOfFirst { it == "java.lang.String" }
        val componentIndex = parameterTypeNames.indexOf("android.content.ComponentName")
        val intsAfterComponent = if (componentIndex >= 0) {
            parameterTypeNames.indices.filter { index ->
                index > componentIndex && parameterTypeNames[index] == "int"
            }
        } else {
            emptyList()
        }

        // Modern AOSP LocalService#getShortcuts has:
        // ... ComponentName, int queryFlags, int userId, int callingPid, int callingUid.
        // Older releases may omit pid/uid; in that case Binder UID is the safe fallback.
        val queryFlagsIndex = intsAfterComponent.firstOrNull()
        val explicitCallerIndex = intsAfterComponent.getOrNull(3)
        val explicitUid = explicitCallerIndex?.let { args.getOrNull(it) as? Int }
        return ShortcutCall(
            callerUid = explicitUid ?: binderUid,
            callingPackage = callingPackageIndex.takeIf { it >= 0 }
                ?.let { args.getOrNull(it) as? String }
                ?.takeIf { it.isNotBlank() },
            queryFlags = queryFlagsIndex?.let { args.getOrNull(it) as? Int },
            explicitCallerUid = explicitUid != null,
        )
    }
}
