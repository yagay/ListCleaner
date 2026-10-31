package com.yagay.ListCleaner.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import com.yagay.ListCleaner.data.ComponentRootCommand
import com.yagay.ListCleaner.domain.PackageIdentity

/**
 * Per-user inventory metadata. Root is required for non-current Android users;
 * a failed command must never be interpreted as an empty system-app set.
 * Call from Dispatchers.IO, never during Compose rendering.
 */
internal object SurfaceProfileAppTypes {
    fun classify(context: Context, userId: Int, names: Collection<String>): Map<String, Boolean?> {
        val current = android.os.Process.myUid() / 100000
        if (userId == current) return names.associateWith { name ->
            runCatching {
                @Suppress("DEPRECATION")
                val app = context.packageManager.getApplicationInfo(name, 0)
                (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }.getOrNull()
        }
        if (userId !in 0..9999) return names.associateWith { null }
        val system = runCatching {
            val result = ComponentRootCommand.runReadOnly("pm list packages -s --user $userId")
            if (result.exitCode != 0 || result.timedOut ||
                "[output truncated]" in result.output || "[output incomplete]" in result.output ||
                "Error:" in result.output || "Exception" in result.output
            ) null else parsePackageOutput(result.output)
        }.getOrNull()
        return names.associateWith { name ->
            if (system == null || !PackageIdentity.valid(name)) null else name in system
        }
    }

    internal fun parsePackageOutput(output: String): Set<String> =
        output.lineSequence().map { it.trim() }.filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").substringBefore(" ") }
            .filter(PackageIdentity::valid).toSet()
}
