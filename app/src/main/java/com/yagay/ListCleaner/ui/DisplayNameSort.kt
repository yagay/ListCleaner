package com.yagay.ListCleaner.ui

import java.text.Collator
import java.util.Locale

/**
 * Android's Chinese collator orders Han characters by Mandarin pinyin while
 * retaining alphabetical comparisons for Latin names. Create one per sort,
 * rather than creating it in every comparator invocation.
 */
internal fun displayNameComparator(): Comparator<String> {
    val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE).apply {
        strength = Collator.PRIMARY
    }
    return Comparator { first, second ->
        collator.compare(first, second)
    }
}
