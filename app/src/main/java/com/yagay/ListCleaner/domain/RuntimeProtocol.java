package com.yagay.ListCleaner.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** A read-only, UID-authenticated system hook probe; never an activity launch. */
public final class RuntimeProtocol {
    public static final String ACTION = "com.yagay.ListCleaner.action.RUNTIME_PROBE_V1";
    public static final String PACKAGE = "com.yagay.ListCleaner";
    public static final String COMPONENT = PACKAGE + ".RuntimeProbe";
    /**
     * Optional manager -> hook hint. New hooks use it only to detect stale
     * RemotePreferences snapshots and force a one-shot fresh read.
     */
    public static final String EXTRA_EXPECTED_DIGEST =
            PACKAGE + ".extra.EXPECTED_CONFIG_DIGEST";
    private RuntimeProtocol() {}

    public static String digest(String config) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(config.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * STALE can mean only the manager APK changed. Keep using the loaded hook when it is from
     * the current hook generation; require reload/restart only when the hook generation advanced.
     */
    public static boolean hookCompatible(
            String state,
            long loaded,
            long requiredHookVersion,
            long installedVersion
    ) {
        boolean active = "UP_TO_DATE".equals(state) || "STALE".equals(state);
        return active && loaded >= requiredHookVersion && loaded <= installedVersion;
    }

    public static boolean supportsSafetyPause(
            String state,
            long loaded,
            long requiredHookVersion,
            long installedVersion
    ) {
        return hookCompatible(state, loaded, requiredHookVersion, installedVersion);
    }
}
