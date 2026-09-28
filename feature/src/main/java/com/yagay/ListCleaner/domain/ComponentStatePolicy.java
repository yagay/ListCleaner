package com.yagay.ListCleaner.domain;

/** No stored blacklist: system override + manifest default determine the displayed state. */
public final class ComponentStatePolicy {
    private ComponentStatePolicy() {}
    public static Boolean enabled(int override, boolean manifestEnabled) {
        switch (override) {
            case 0: return manifestEnabled;
            case 1: return true;
            case 2: case 3: case 4: return false;
            default: return null;
        }
    }
    public static boolean valid(String pkg, String cls, int user) {
        return user >= 0 && user <= 21474 && pkg != null && cls != null &&
            pkg.length() <= 255 && cls.length() <= 512 &&
            pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+") &&
            cls.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    }
    public static String commandLine(String pkg, String cls, int user, boolean enable) {
        if (!valid(pkg, cls, user)) throw new IllegalArgumentException("Invalid component identity");
        // Always a fully-qualified component, never a package-wide enable/disable.
        return "/system/bin/pm " + (enable ? "enable" : "disable") +
            " --user " + user + " '" + pkg + "/" + cls + "'";
    }
    public static String command(String pkg, String cls, int user, boolean enable) {
        return "test \"$(id -u)\" = 0 || exit 77\nexec " + commandLine(pkg, cls, user, enable);
    }
}
