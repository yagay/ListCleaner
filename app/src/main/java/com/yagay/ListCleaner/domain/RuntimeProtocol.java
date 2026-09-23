package com.yagay.ListCleaner.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * UID-authenticated runtime control protocol carried by PackageManager resolver queries.
 *
 * RemotePreferences remains the persistent/cold-start store. Protocol v2 transports the actual
 * serialized ModuleConfig to the already-running system_server hook so runtime correctness does
 * not depend on a framework-specific RemotePreferences cache refresh.
 */
public final class RuntimeProtocol {
    public static final String ACTION = "com.yagay.ListCleaner.action.RUNTIME_PROBE_V1";
    public static final String PACKAGE = "com.yagay.ListCleaner";
    public static final String COMPONENT = PACKAGE + ".RuntimeProbe";

    public static final int VERSION = 2;
    public static final int CONFIG_CHUNK_CHARS = 20_000;
    public static final int MAX_CONFIG_CHUNKS = 128;

    public static final String EXTRA_PROTOCOL_VERSION =
            PACKAGE + ".extra.RUNTIME_PROTOCOL_VERSION";
    public static final String EXTRA_OPERATION =
            PACKAGE + ".extra.RUNTIME_OPERATION";
    public static final String EXTRA_EXPECTED_DIGEST =
            PACKAGE + ".extra.EXPECTED_CONFIG_DIGEST";
    public static final String EXTRA_TRANSFER_ID =
            PACKAGE + ".extra.CONFIG_TRANSFER_ID";
    public static final String EXTRA_REVISION =
            PACKAGE + ".extra.CONFIG_REVISION";
    public static final String EXTRA_TOTAL_CHUNKS =
            PACKAGE + ".extra.CONFIG_TOTAL_CHUNKS";
    public static final String EXTRA_TOTAL_CHARS =
            PACKAGE + ".extra.CONFIG_TOTAL_CHARS";
    public static final String EXTRA_CHUNK_INDEX =
            PACKAGE + ".extra.CONFIG_CHUNK_INDEX";
    public static final String EXTRA_CONFIG_CHUNK =
            PACKAGE + ".extra.CONFIG_CHUNK";

    public static final String OP_BEGIN = "begin";
    public static final String OP_CHUNK = "chunk";
    public static final String OP_COMMIT = "commit";

    /** Private ResolveInfo metadata written by the system hook and consumed by Resolver hooks. */
    public static final String META_POLICY_DIGEST =
            PACKAGE + ".meta.POLICY_DIGEST";
    public static final String META_INCLUDE =
            PACKAGE + ".meta.INCLUDE";
    public static final String META_PRIORITY_RANK =
            PACKAGE + ".meta.PRIORITY_RANK";

    private RuntimeProtocol() {}

    public static boolean validDigest(String digest) {
        if (digest == null || digest.length() != 64) return false;
        for (int i = 0; i < digest.length(); i++) {
            char ch = digest.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) return false;
        }
        return true;
    }

    public static boolean validTransferId(String transferId) {
        if (transferId == null || transferId.isEmpty() || transferId.length() > 64) return false;
        for (int i = 0; i < transferId.length(); i++) {
            char ch = transferId.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '-' || ch == '_')) return false;
        }
        return true;
    }

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
