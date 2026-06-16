package digital.honeybadger.workflow

import digital.honeybadger.workflow.adapter.inbound.http.configureHttpRoutes
import digital.honeybadger.workflow.adapter.inbound.websocket.DefaultWebSocketSessionRegistry
import digital.honeybadger.workflow.adapter.inbound.websocket.configureWebSocketRoutes
import digital.honeybadger.workflow.adapter.outbound.persistence.InMemoryActivityRepository
import digital.honeybadger.workflow.adapter.outbound.persistence.InMemoryPlanRepository
import digital.honeybadger.workflow.adapter.outbound.persistence.InMemoryWorkstreamRepository
import digital.honeybadger.workflow.adapter.outbound.realtime.WebSocketEventPublisher
import digital.honeybadger.workflow.application.usecase.ActivityService
import digital.honeybadger.workflow.application.usecase.PlanService
import digital.honeybadger.workflow.application.usecase.WorkstreamService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Outbound adapters
    val workstreamRepo = InMemoryWorkstreamRepository()
    val planRepo = InMemoryPlanRepository()
    val activityRepo = InMemoryActivityRepository()
    val sessionRegistry = DefaultWebSocketSessionRegistry()
    // Application implements CoroutineScope — broadcast coroutines are cancelled on shutdown.
    val publisher = WebSocketEventPublisher(registry = sessionRegistry, scope = this)

    // Use cases
    val workstreamService = WorkstreamService(workstreamRepo, publisher)
    val planService = PlanService(workstreamRepo, planRepo, publisher, activityRepo)
    val activityService = ActivityService(workstreamRepo, activityRepo, publisher)

    // Plugins, routes, WebSocket
    configurePlugins()
    configureRouting()
    configureHttpRoutes(workstreamService, planService, activityService, sessionRegistry)
    configureWebSocketRoutes(sessionRegistry, scope = this, workstreamUseCase = workstreamService, publisher = publisher)
}
