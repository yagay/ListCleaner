package com.yagay.ListCleaner.domain;

/**
 * Stable component identity shared by catalog discovery and runtime filtering.
 *
 * An activity-alias is an independent Android component. Its target is metadata,
 * never a replacement for the resolved component's identity.
 */
public final class ComponentIdentity {
    private ComponentIdentity() {}

    public static String canonicalClassName(String packageName, String name, String targetActivity) {
        String chosen = name;
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

