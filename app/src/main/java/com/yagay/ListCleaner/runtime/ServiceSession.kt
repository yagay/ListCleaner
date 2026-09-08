package com.yagay.ListCleaner.runtime

import io.github.libxposed.service.XposedService
import java.util.concurrent.atomic.AtomicLong

/**
 * Identity token for one concrete XposedService binding.
 *
 * The generation is intentionally independent from object identity: delayed callbacks from an
 * older binding can never become current again even if a framework implementation happens to
 * reuse the same service object.
 */
data class ServiceSession(
    val generation: Long,
    val service: XposedService
)

/** Small generic generation registry so reconnect ordering can be unit-tested without libxposed mocks. */
internal class GenerationRegistry<T : Any> {
    data class Token<T : Any>(val generation: Long, val value: T)

    private val counter = AtomicLong(0L)

    @Volatile
    private var current: Token<T>? = null

    fun bind(value: T): Token<T> = synchronized(this) {
        Token(counter.incrementAndGet(), value).also { current = it }
    }

    fun clear(value: T): Token<T>? = synchronized(this) {
        current?.takeIf { it.value === value }?.also { current = null }
    }

    fun snapshot(): Token<T>? = current

    fun isCurrent(token: Token<T>?): Boolean {
        if (token == null) return current == null
        val active = current ?: return false
        return active.generation == token.generation && active.value === token.value
    }
}

/** Thread-safe owner for the currently active XposedService session. */
class ServiceSessionRegistry {
    private val registry = GenerationRegistry<XposedService>()

    fun bind(service: XposedService): ServiceSession {
        val token = registry.bind(service)
        return ServiceSession(token.generation, token.value)
    }

    fun clear(service: XposedService): ServiceSession? = registry.clear(service)?.let {
        ServiceSession(it.generation, it.value)
    }

    fun snapshot(): ServiceSession? = registry.snapshot()?.let {
        ServiceSession(it.generation, it.value)
    }

    fun isCurrent(session: ServiceSession?): Boolean = registry.isCurrent(
        session?.let { GenerationRegistry.Token(it.generation, it.service) }
    )
}
