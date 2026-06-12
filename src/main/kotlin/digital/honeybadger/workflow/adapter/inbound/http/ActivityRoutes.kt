package digital.honeybadger.workflow.adapter.inbound.http

import digital.honeybadger.workflow.application.dto.CreateActivityEventRequest
import digital.honeybadger.workflow.application.port.inbound.ActivityUseCase
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.activityRoutes(useCase: ActivityUseCase) {
    route("/workstreams/{id}") {
        post("/activity") {
            val request = call.receive<CreateActivityEventRequest>()
            call.respond(HttpStatusCode.Created, useCase.add(call.parameters["id"]!!, request))
        }
        get("/activity") {
            call.respond(useCase.list(call.parameters["id"]!!))
        }
    }
}
