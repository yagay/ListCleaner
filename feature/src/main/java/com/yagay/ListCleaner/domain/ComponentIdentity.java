package com.yagay.ListCleaner.domain;

/**
 * Stable component identity shared by catalog discovery and runtime filtering.
 *
 * Android may expose an activity-alias as the resolved ActivityInfo.name while the
 * underlying activity is available through ActivityInfo.targetActivity. Rules should
 * follow the target activity so the same app does not appear under different identities
 * depending on caller, resolver path, OEM framework, or alias entry point.
 */
public final class ComponentIdentity {
    private ComponentIdentity() {}

    public static String canonicalClassName(String packageName, String name, String targetActivity) {
        String chosen = targetActivity == null || targetActivity.trim().isEmpty() ? name : targetActivity;
        if (chosen == null) return "";
        chosen = chosen.trim();
        if (chosen.startsWith(".")) return packageName + chosen;
        // Be tolerant of short manifest-style names in diagnostics/tests even though
        // PackageManager normally expands them before returning ActivityInfo.
        if (!chosen.contains(".") && packageName != null && !packageName.isEmpty()) {
            return packageName + "." + chosen;
        }
        return chosen;
    }
}
