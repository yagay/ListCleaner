package com.yagay.ListCleaner.domain;

import java.util.Locale;

/** Shared by catalog probes and runtime hooks. Unknown protocols are never file rules. */
public final class IntentClassification {
    private IntentClassification() {}
    public static String classify(String action, String scheme, String mime) {
        if ("android.intent.action.SEND".equals(action)) return "SHARE";
        if ("android.intent.action.SEND_MULTIPLE".equals(action)) return "SHARE_MULTIPLE";
        if ("android.intent.action.PROCESS_TEXT".equals(action)) return "PROCESS_TEXT";
        if (!"android.intent.action.VIEW".equals(action)) return null;
        String s = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (m.startsWith("vnd.android.cursor.")) return null;
        if (s.equals("http") || s.equals("https")) {
            return m.isEmpty() || m.equals("*/*") || m.equals("text/html") ||
                    m.equals("application/xhtml+xml") ? "BROWSER" : "OPEN";
        }
        if (s.equals("file")) return "OPEN";
        if (s.equals("content") || s.isEmpty()) return m.isEmpty() ? null : "OPEN";
        return null;
    }
}
