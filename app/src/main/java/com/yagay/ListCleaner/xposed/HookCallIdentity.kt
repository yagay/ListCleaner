package com.yagay.ListCleaner.xposed

/**
 * Reflection-layout helpers for system_server hooks.
 *
 * Internal PackageManager/ShortcutService methods often carry the original caller UID explicitly.
 * Binder.getCallingUid() is not authoritative after framework code clears/restores Binder identity,
 * so hooks prefer the explicit argument whenever a known signature exposes one.
 */
internal object HookCallIdentity {
    data class ShortcutCall(
        val callerUid: Int,
        val callingPackage: String?,
        val queryFlags: Int?,
        val explicitCallerUid: Boolean,
    )

    fun packageManagerCallerUid(
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
        // AOSP queryIntent*Internal(..., int userId, int callingUid, int callingPid, ...)
        val callerIndex = ints.getOrNull(1) ?: return binderUid
        return args.getOrNull(callerIndex) as? Int ?: binderUid
    }

    fun serviceCallerUid(
        methodName: String,
        parameterTypeNames: List<String>,
        args: List<Any?>,
        binderUid: Int,
    ): Int = packageManagerCallerUid(methodName, parameterTypeNames, args, binderUid)

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
