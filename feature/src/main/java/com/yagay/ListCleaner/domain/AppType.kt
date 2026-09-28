package com.yagay.ListCleaner.domain

import android.content.pm.ApplicationInfo

enum class AppType {
    USER,
    SYSTEM
}

enum class AppTypeFilter {
    ALL,
    USER,
    SYSTEM;

    fun matches(type: AppType): Boolean = when (this) {
        ALL -> true
        USER -> type == AppType.USER
        SYSTEM -> type == AppType.SYSTEM
    }
}

fun ApplicationInfo.listCleanerAppType(): AppType =
    if (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
        AppType.SYSTEM
    } else {
        AppType.USER
    }
