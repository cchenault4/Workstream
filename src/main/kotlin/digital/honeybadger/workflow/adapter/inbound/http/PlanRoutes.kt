package digital.honeybadger.workflow.adapter.inbound.http

import digital.honeybadger.workflow.application.dto.UpsertPlanRequest
import digital.honeybadger.workflow.application.port.inbound.PlanUseCase
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.planRoutes(useCase: PlanUseCase) {
    route("/workstreams/{id}") {
        put("/plan") {
            val request = call.receive<UpsertPlanRequest>()
            call.respond(useCase.upsert(call.parameters["id"]!!, request))
        }
        get("/plan") {
            call.respond(useCase.get(call.parameters["id"]!!))
        }
        get("/readiness") {
            call.respond(useCase.readiness(call.parameters["id"]!!))
        }
    }
}
