package com.yagay.ListCleaner.domain

import android.content.Intent

fun Intent.intentKind(resolvedType: String? = null): IntentKind? {
    val effective = selector ?: this
    if (effective.action == Intent.ACTION_MAIN && effective.categories?.contains(Intent.CATEGORY_HOME) == true) {
        return IntentKind.HOME
    }
    return IntentClassification.classify(
        effective.action,
        effective.data?.scheme,
        effective.type ?: resolvedType,
    )?.let { IntentKind.valueOf(it) }
}
