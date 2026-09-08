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

/** Thread-safe owner for the currently active XposedService session. */
class ServiceSessionRegistry {
    private val counter = AtomicLong(0L)

    @Volatile
    private var current: ServiceSession? = null

    fun bind(service: XposedService): ServiceSession = synchronized(this) {
        ServiceSession(counter.incrementAndGet(), service).also { current = it }
    }

    fun clear(service: XposedService): ServiceSession? = synchronized(this) {
        current?.takeIf { it.service === service }?.also { current = null }
    }

    fun snapshot(): ServiceSession? = current

    fun isCurrent(session: ServiceSession?): Boolean {
        if (session == null) return current == null
        val active = current ?: return false
        return active.generation == session.generation && active.service === session.service
    }
}
