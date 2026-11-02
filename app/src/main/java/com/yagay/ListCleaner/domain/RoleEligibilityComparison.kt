package com.yagay.ListCleaner.domain

import kotlin.math.abs

/** The system role candidate list is not equivalent to the rendered Settings UI. */
data class RoleEligibilityComparison(
    val original: RuntimeInterfaceSnapshot?,
    val filtered: RuntimeInterfaceSnapshot?,
    val sameQuery: Boolean,
) {
    val before: List<String> get() = original?.packages.orEmpty()
    val after: List<String> get() = filtered?.packages.orEmpty()
    val removed: List<String> get() =
        if (sameQuery) before.filterNot { it in after.toSet() } else emptyList()
}

/** Reject a different role, user, process, or unmatched observation before diffing. */
fun compareRoleEligibility(
    kind: IntentKind, userId: Int,
    original: RuntimeInterfaceSnapshot?,
    filtered: RuntimeInterfaceSnapshot?,
): RoleEligibilityComparison {
    val roleName = "android.app.role." + kind.name
    fun RuntimeInterfaceSnapshot.matches(sourceName: String): Boolean =
        kind.name == this.kind && callerUserId == userId &&
            source == sourceName && requestAction == roleName &&
            callerPackage.substringBefore(':') in setOf(
                "com.android.permissioncontroller", "com.google.android.permissioncontroller"
            ) && !truncated &&
            totalPackages <= packages.size

    val before = original?.takeIf { it.matches("ROLE_CONTROLLER_ORIGINAL") }
    val after = filtered?.takeIf { it.matches("ROLE_CONTROLLER_RETURN") }
    val paired = before != null && after != null &&
        before.requestToken.isNotBlank() &&
        before.requestToken == after.requestToken &&
        before.callerUid == after.callerUid &&
        before.callerPackage == after.callerPackage &&
        abs(before.capturedAtMillis - after.capturedAtMillis) <= 10_000L
    return RoleEligibilityComparison(before, after, paired)
}
