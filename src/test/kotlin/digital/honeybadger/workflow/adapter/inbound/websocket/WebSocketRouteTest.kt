package digital.honeybadger.workflow.adapter.inbound.websocket

import digital.honeybadger.workflow.application.port.inbound.WorkstreamUseCase
import digital.honeybadger.workflow.application.port.outbound.EventPublisher
import digital.honeybadger.workflow.configurePlugins
import digital.honeybadger.workflow.domain.model.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import kotlin.test.Test

class WebSocketRouteTest {

    private val now = Instant.parse("2024-06-01T12:00:00Z")
    private val testWorkstream = Workstream("ws-1", "Title", "Desc", "alice", Priority.HIGH, WorkstreamStatus.NEW, now, now)

    private fun mockDeps(): Pair<WorkstreamUseCase, EventPublisher> {
        val useCase = mockk<WorkstreamUseCase>()
        val publisher = mockk<EventPublisher>(relaxed = true)
        every { useCase.getById(any()) } returns testWorkstream
        return useCase to publisher
    }

    @Test
    fun `websocket endpoint accepts connections`() = testApplication {
        val (useCase, publisher) = mockDeps()
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry, CoroutineScope(Dispatchers.Default), useCase, publisher)
        }
        createClient { install(WebSockets) }.webSocket("/ws") {
            // connection established without error
        }
    }

    @Test
    fun `join and leave messages are processed without error`() = testApplication {
        val (useCase, publisher) = mockDeps()
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry, CoroutineScope(Dispatchers.Default), useCase, publisher)
        }
        createClient { install(WebSockets) }.webSocket("/ws") {
            send(Frame.Text("""{"type":"workstream:join","workstreamId":"ws-1"}"""))
            send(Frame.Text("""{"type":"workstream:leave","workstreamId":"ws-1"}"""))
        }
        // Join calls publishPresenceUpdate with active=true; leave calls it with active=false
        verify { publisher.publishPresenceUpdate(any(), true) }
        verify { publisher.publishPresenceUpdate(any(), false) }
    }

    @Test
    fun `unknown message type is silently ignored`() = testApplication {
        val (useCase, publisher) = mockDeps()
        val registry = DefaultWebSocketSessionRegistry()
        application {
            configurePlugins()
            configureWebSocketRoutes(registry, CoroutineScope(Dispatchers.Default), useCase, publisher)
        }
        createClient { install(WebSockets) }.webSocket("/ws") {
            send(Frame.Text("""{"type":"unknown:event","workstreamId":"ws-1"}"""))
        }
    }
}
