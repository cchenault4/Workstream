package digital.honeybadger.workflow.adapter.inbound.http

import digital.honeybadger.workflow.application.exception.PlanNotFoundException
import digital.honeybadger.workflow.application.exception.WorkstreamNotFoundException
import digital.honeybadger.workflow.application.port.inbound.ActivityUseCase
import digital.honeybadger.workflow.application.port.inbound.PlanUseCase
import digital.honeybadger.workflow.application.port.inbound.WorkstreamUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHttpRoutes(
    workstreamUseCase: WorkstreamUseCase,
    planUseCase: PlanUseCase,
    activityUseCase: ActivityUseCase,
) {
    install(StatusPages) {
        exception<WorkstreamNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
        }
        exception<PlanNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Internal server error"))
        }
    }
    routing {
        workstreamRoutes(workstreamUseCase)
        planRoutes(planUseCase)
        activityRoutes(activityUseCase)
    }
}
