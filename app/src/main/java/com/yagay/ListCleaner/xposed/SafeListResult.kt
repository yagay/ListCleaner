package com.yagay.ListCleaner.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared fail-open adapter for PackageManager/ShortcutService list results.
 *
 * OEM builds may change ParceledListSlice constructors or accessors. Failure to understand an
 * unfamiliar result must never turn a successful framework query into an exception from our hook.
 */
internal data class SafeListResult(
    val values: List<*>,
    val rebuild: (List<*>) -> Any?,
)

internal class SafeListResultExtractor(
    private val record: (String) -> Unit,
) {
    private data class Accessor(
        val getList: Method,
        val constructor: Constructor<*>,
    )

    private val accessors = ConcurrentHashMap<Class<*>, Accessor>()

    fun extract(original: Any?): SafeListResult? = when {
        original is List<*> -> SafeListResult(original) { it }
        original == null -> null
        original.javaClass.name.endsWith("ParceledListSlice") -> extractSlice(original)
        else -> null
    }

    private fun extractSlice(original: Any): SafeListResult? {
        val accessor = runCatching {
            accessors.computeIfAbsent(original.javaClass) { clazz ->
                val getList = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
                    .firstOrNull { it.name == "getList" && it.parameterCount == 0 }
                    ?.apply { isAccessible = true }
                    ?: throw NoSuchMethodException("${clazz.name}#getList()")
                val constructor = clazz.declaredConstructors.firstOrNull { ctor ->
                    ctor.parameterTypes.size == 1 &&
                        List::class.java.isAssignableFrom(ctor.parameterTypes[0])
                }?.apply { isAccessible = true }
                    ?: throw NoSuchMethodException("${clazz.name}(List)")
                Accessor(getList, constructor)
            }
        }.onFailure {
            record("LIST_RESULT_ACCESSOR_UNAVAILABLE class=${original.javaClass.name} error=${it.javaClass.name}")
        }.getOrNull() ?: return null

        val values = runCatching {
            accessor.getList.invoke(original) as? List<*>
        }.onFailure {
            record("LIST_RESULT_READ_FAILED class=${original.javaClass.name} error=${it.javaClass.name}")
        }.getOrNull() ?: return null

        return SafeListResult(values) { replacement ->
            runCatching { accessor.constructor.newInstance(replacement) }
                .onFailure {
                    record("LIST_RESULT_REBUILD_FAILED class=${original.javaClass.name} error=${it.javaClass.name}")
                }
                .getOrElse { original }
        }
    }
}
