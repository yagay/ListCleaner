package com.yagay.ListCleaner.domain;

import java.util.regex.Pattern;

/** Shared validation for package identities persisted by manager/runtime configuration. */
public final class PackageIdentity {
    private static final Pattern PACKAGE = Pattern.compile(
        "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"
    );

    private PackageIdentity() {}

    public static boolean valid(String value) {
        return value != null && value.length() <= 255 && PACKAGE.matcher(value).matches();
    }
}
