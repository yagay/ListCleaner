package com.yagay.ListCleaner.xposed

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Small cached reflection helper shared by framework/OEM adapters. All reads fail open. */
internal object ReflectionAccess {
    private data class MethodKey(val type: Class<*>, val name: String)
    private data class FieldKey(val type: Class<*>, val names: String)
    private data class MethodLookup(val value: Method?)
    private data class FieldLookup(val value: Field?)

    private val methods = ConcurrentHashMap<MethodKey, MethodLookup>()
    private val fields = ConcurrentHashMap<FieldKey, FieldLookup>()

    fun hierarchyMethods(type: Class<*>): Sequence<Method> =
        generateSequence(type as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }

    fun hierarchyFields(type: Class<*>): Sequence<Field> =
        generateSequence(type as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }

    fun noArgMethod(type: Class<*>?, name: String): Method? {
        type ?: return null
        return methods.computeIfAbsent(MethodKey(type, name)) { key ->
            MethodLookup(
                hierarchyMethods(key.type)
                    .firstOrNull { it.name == key.name && it.parameterCount == 0 }
                    ?.apply { isAccessible = true }
            )
        }.value
    }

    fun invokeNoArg(value: Any?, name: String): Any? = runCatching {
        val receiver = value ?: return@runCatching null
        noArgMethod(receiver.javaClass, name)?.invoke(receiver)
    }.getOrNull()

    fun invokeFirstNoArg(value: Any?, names: Iterable<String>): Any? {
        val receiver = value ?: return null
        names.forEach { name ->
            val result = runCatching { noArgMethod(receiver.javaClass, name)?.invoke(receiver) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    fun field(type: Class<*>?, vararg names: String): Field? {
        type ?: return null
        if (names.isEmpty()) return null
        val key = FieldKey(type, names.joinToString("\u0000"))
        return fields.computeIfAbsent(key) {
            val wanted = names.toSet()
            FieldLookup(
                hierarchyFields(type)
                    .firstOrNull { candidate -> candidate.name in wanted }
                    ?.apply { isAccessible = true }
            )
        }.value
    }

    fun readField(value: Any?, vararg names: String): Any? = runCatching {
        val receiver = value ?: return@runCatching null
        field(receiver.javaClass, *names)?.get(receiver)
    }.getOrNull()

    fun readString(value: Any?, vararg methodNames: String): String? {
        val receiver = value ?: return null
        methodNames.forEach { name ->
            (invokeNoArg(receiver, name) as? String)?.let { return it }
        }
        return null
    }
}
