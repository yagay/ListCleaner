package com.yagay.ListCleaner

import android.content.Context
import java.util.Locale

/**
 * Central application localization bridge.
 *
 * Existing UI text can keep using the source Chinese text while translations are supplied by
 * Android locale resources. Each locale may override R.array.locale_overrides with entries in the
 * form `source|||translation`. Missing entries fall back to the built-in English catalogue passed
 * by the caller, so every unsupported system language remains English.
 *
 * New code should prefer normal Android string resources/stringResource where practical. This
 * bridge keeps legacy Compose, Root and runtime-generated messages on the same locale pipeline and
 * makes additional languages possible without touching business logic.
 */
internal object AppLanguage {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun isChinese(): Boolean =
        Locale.getDefault().language.equals("zh", ignoreCase = true)

    private fun resourceOverrides(): Map<String, String> {
        val context = appContext ?: return emptyMap()
        return runCatching {
            context.resources.getStringArray(R.array.locale_overrides)
                .mapNotNull { item ->
                    val split = item.indexOf("|||")
                    if (split <= 0) null
                    else item.substring(0, split) to item.substring(split + 3)
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    fun translate(text: String, english: Map<String, String>): String {
        if (isChinese()) return text
        val overrides = resourceOverrides()
        overrides[text]?.let { return it }
        english[text]?.let { return it }

        var result = text
        val keys = (english.keys + overrides.keys)
            .asSequence()
            .filter { it.length >= 2 && it != text }
            .distinct()
            .sortedByDescending(String::length)

        for (source in keys) {
            val target = overrides[source] ?: english[source] ?: continue
            result = result.replace(source, target)
        }
        return result
    }

    fun translate(text: String, english: List<Pair<String, String>>): String =
        translate(text, english.toMap())

    fun pick(chinese: String, english: String): String {
        if (isChinese()) return chinese
        return resourceOverrides()[chinese] ?: english
    }
}
