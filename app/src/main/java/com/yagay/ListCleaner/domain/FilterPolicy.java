package com.yagay.ListCleaner.domain;

/** Pure policy, independent of Android and covered by the host-side regression harness. */
public final class FilterPolicy {
    private FilterPolicy() {}

    /** Android UIDs embed the user/profile id in multiples of 100000. Classify by appId so
     * system/privileged callers in secondary users or work profiles are not mistaken for apps. */
    public static boolean ordinaryAppCaller(int callerUid) {
        return AndroidUid.isApplicationUid(callerUid);
    }

    /** Candidate-preservation guard used by resolver filtering. Privileged/system callers are
     * treated as protected so their framework-internal package-manager queries remain untouched. */
    public static boolean sameCaller(int callerUid, int targetUid) {
        return callerUid >= 0 && (!ordinaryAppCaller(callerUid) || callerUid == targetUid);
    }

    /** Applies only AFTER a PM query without disabled-component match flags.
     * Manifest enabled defaults and this manager's permissions must not veto a match.
     * Never enables or launches an activity; non-exported foreign targets remain private.
     */
    public static boolean catalogRestricted(boolean exported, int targetUid, int managerUid) {
        boolean sameUid = managerUid >= 0 && managerUid == targetUid;
        return !exported && !sameUid;
    }

    public static boolean restoreEmpty(String kind, int before, int after) {
        // Text actions are optional menu entries, not a mandatory file-open destination.
        return before > 0 && after == 0 && !"PROCESS_TEXT".equals(kind);
    }
}
