package com.yagay.ListCleaner.domain;

/** Shared Android UID helpers. Keep user/app id arithmetic in one place. */
public final class AndroidUid {
    public static final int PER_USER_RANGE = 100_000;
    public static final int FIRST_APPLICATION_UID = 10_000;

    private AndroidUid() {}

    public static int appId(int uid) {
        return uid < 0 ? -1 : uid % PER_USER_RANGE;
    }

    public static int userId(int uid) {
        return uid < 0 ? -1 : uid / PER_USER_RANGE;
    }

    public static boolean isApplicationUid(int uid) {
        return appId(uid) >= FIRST_APPLICATION_UID;
    }
}
