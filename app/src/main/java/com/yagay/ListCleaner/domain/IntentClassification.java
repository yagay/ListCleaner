package com.yagay.ListCleaner.domain;

import java.util.Locale;

/** Shared by catalog probes and runtime hooks. Unknown protocols are never file rules. */
public final class IntentClassification {
    private IntentClassification() {}
    public static String classify(String action, String scheme, String mime) {
        if ("android.intent.action.SEND".equals(action)) return "SHARE";
        if ("android.intent.action.SEND_MULTIPLE".equals(action)) return "SHARE_MULTIPLE";
        if ("android.intent.action.SENDTO".equals(action)) return "SEND_TO";
        if ("android.intent.action.DIAL".equals(action)) return "DIAL";
        if ("android.intent.action.GET_CONTENT".equals(action)) return "GET_CONTENT";
        if ("android.intent.action.OPEN_DOCUMENT".equals(action)) return "OPEN_DOCUMENT";
        if ("android.intent.action.CREATE_DOCUMENT".equals(action)) return "CREATE_DOCUMENT";
        if ("android.intent.action.ASSIST".equals(action)) return "ASSISTANT";
        if ("android.media.action.IMAGE_CAPTURE".equals(action)) return "CAPTURE_IMAGE";
        if ("android.media.action.VIDEO_CAPTURE".equals(action)) return "CAPTURE_VIDEO";
        if ("android.provider.MediaStore.RECORD_SOUND".equals(action)) return "RECORD_AUDIO";
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
        if (s.equals("magnet") || s.equals("geo") || s.equals("mailto") ||
                s.equals("tel") || s.equals("sms") || s.equals("smsto")) return "OPEN";
        return null;
    }
}
