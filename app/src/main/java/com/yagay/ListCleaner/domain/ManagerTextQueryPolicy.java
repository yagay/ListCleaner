package com.yagay.ListCleaner.domain;

/** Keep management inventory unfiltered; allow real PROCESS_TEXT ActionMode in the manager. */
public final class ManagerTextQueryPolicy {
    private ManagerTextQueryPolicy() {}

    public static boolean bypassManager(
            boolean managerCaller, boolean managerPreview,
            boolean managerCatalog, boolean processText) {
        return managerCaller && !managerPreview && (!processText || managerCatalog);
    }
}
