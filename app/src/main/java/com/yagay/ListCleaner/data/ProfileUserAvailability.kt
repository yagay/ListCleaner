package com.yagay.ListCleaner.data

/**
 * Check the target Android user's actual unlock state, not merely the "running"
 * marker from `cmd user list`. Android may expose installed packages while the
 * work/private profile's resolver services are still locked.
 */
internal enum class ProfileUnlockState { UNLOCKED, LOCKED, UNKNOWN }

internal object ProfileUserAvailability {
    fun parse(output: String): ProfileUnlockState = when {
        output.contains("RUNNING_UNLOCKED") -> ProfileUnlockState.UNLOCKED
        output.contains("RUNNING_LOCKED") ||
            output.contains("RUNNING_UNLOCKING") ||
            output.contains("STOPPING") ||
            output.contains("SHUTDOWN") ||
            output.contains("BOOTING") ||
            output.contains("not started", ignoreCase = true) -> ProfileUnlockState.LOCKED
        else -> ProfileUnlockState.UNKNOWN
    }

    fun check(userId: Int): ProfileUnlockState {
        if (userId !in 0..21474) return ProfileUnlockState.UNKNOWN
        return runCatching {
            val result = ComponentRootCommand.runReadOnly("am get-started-user-state $userId")
            if (result.exitCode != 0 || result.timedOut ||
                !ProfileShellBatch.clean(result.output)) ProfileUnlockState.UNKNOWN
            else parse(result.output)
        }.getOrDefault(ProfileUnlockState.UNKNOWN)
    }
}
