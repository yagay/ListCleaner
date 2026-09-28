package com.yagay.ListCleaner.domain

import android.content.Intent

fun Intent.intentKind(resolvedType: String? = null): IntentKind? {
    val effective = selector ?: this
    return IntentClassification.classify(effective.action, effective.data?.scheme,
        effective.type ?: resolvedType)?.let { IntentKind.valueOf(it) }
}
