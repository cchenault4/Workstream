package digital.honeybadger.workflow.adapter.outbound.persistence

import digital.honeybadger.workflow.application.port.outbound.PresenceRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InMemoryPresenceRegistry : PresenceRegistry {

    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    override fun join(workstreamId: String) {
        counts.computeIfAbsent(workstreamId) { AtomicInteger(0) }.incrementAndGet()
    }

    override fun leave(workstreamId: String) {
        counts[workstreamId]?.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    override fun isActive(workstreamId: String): Boolean =
        (counts[workstreamId]?.get() ?: 0) > 0

    override fun activeWorkstreamIds(): Set<String> =
        counts.entries.filter { it.value.get() > 0 }.map { it.key }.toSet()
}
