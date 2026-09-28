package com.yagay.ListCleaner.domain;

public final class ManagerIdentity {
    private ManagerIdentity() {}
    public static boolean valid(int appId) { return appId >= 10000 && appId < 100000; }
    public static boolean matches(int callerUid, int managerAppId) {
        return callerUid >= 0 && valid(managerAppId) && callerUid % 100000 == managerAppId;
    }
}
