package digital.honeybadger.workflow.adapter.inbound.http

import digital.honeybadger.workflow.adapter.inbound.websocket.WebSocketSessionRegistry
import digital.honeybadger.workflow.application.dto.CreateWorkstreamRequest
import digital.honeybadger.workflow.application.dto.UpdateWorkstreamRequest
import digital.honeybadger.workflow.application.port.inbound.WorkstreamUseCase
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.workstreamRoutes(useCase: WorkstreamUseCase, registry: WebSocketSessionRegistry) {
    route("/workstreams") {
        post {
            val request = call.receive<CreateWorkstreamRequest>()
            call.respond(HttpStatusCode.Created, useCase.create(request))
        }
        get {
            val active = registry.activeRooms()
            call.respond(useCase.list().map { it.toSummary(it.id in active) })
        }
        route("/{id}") {
            get {
                call.respond(useCase.getById(call.parameters["id"]!!))
            }
            patch {
                val request = call.receive<UpdateWorkstreamRequest>()
                call.respond(useCase.update(call.parameters["id"]!!, request))
            }
        }
    }
}
