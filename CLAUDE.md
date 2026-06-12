# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Kotlin backend service** for an agentic workstream system. It models how engineering work moves through an AI-assisted workflow: workstreams are created, implementation plans are attached, agents emit activity events, and participants receive real-time updates via Socket.IO.

The full spec is in `README.md`. An `AI_DEVELOPMENT_LOG.md` documenting AI tool usage is required as part of the submission.

## Domain Model

**Workstream** — the top-level unit of work:
- Fields: `id`, `title`, `description`, `requester`, `priority` (`low|medium|high`), `status` (`new|planning|executing|reviewing|verified|blocked`), `createdAt`, `updatedAt`

**Implementation Plan** — attached to a workstream (one per workstream):
- Fields: `goal`, `nonGoals[]`, `assumptions[]`, `openQuestions[]`, `phases[]`, `verificationPlan[]`
- `openQuestions` have a `type`: `blocking|assumable|deferrable` and optional `resolution`
- `phases` have a `status`: `pending|in_progress|complete|blocked`
- Exposes derived **readiness state**: `blockingQuestionsResolved`, `allPhasesComplete`, `verificationReady`, `readyForReview`, `readyForPR`

**ActivityEvent** — emitted by agents against a workstream:
- Fields: `id`, `workstreamId`, `agentName`, `type` (`context_discovery|planning|implementation|review|verification|handoff`), `message`, `createdAt`
- Creating an event must broadcast it in real time to Socket.IO subscribers

## REST API

```
POST   /workstreams
GET    /workstreams
GET    /workstreams/:id
PATCH  /workstreams/:id
PUT    /workstreams/:id/plan
GET    /workstreams/:id/plan
GET    /workstreams/:id/readiness
POST   /workstreams/:id/activity
GET    /workstreams/:id/activity
```

## Real-Time (Socket.IO)

Clients join/leave workstream rooms and receive broadcasts:
- Client → server: `workstream:join { workstreamId }`, `workstream:leave { workstreamId }`
- Server → client: `activity:created <activityEvent>`, `workstream:updated <workstream>`, `plan:updated <plan>`

## Commands

```bash
gradle test          # run all tests
gradle test --tests "digital.honeybadger.workflow.SomeTest"  # run single test class
gradle run           # start the server (port 8080)
gradle build         # compile + test + assemble jar
```

## Tech Stack

**Kotlin 2.2.0 + Ktor 3.1.3 + Gradle 9.x**, JVM target 24, in-memory storage (no DB). Real-time via Ktor WebSockets (not Socket.IO — no server-side Socket.IO library exists for Ktor).

## Architecture

Hexagonal (Ports & Adapters):
- `domain/model/` — pure data classes and enums, no framework deps
- `domain/service/` — `ReadinessService`: pure computation of derived readiness state from a `Plan`
- `application/port/inbound/` — use case interfaces (`WorkstreamUseCase`, `PlanUseCase`, `ActivityUseCase`)
- `application/port/outbound/` — repository + event publisher interfaces
- `application/usecase/` — implements inbound ports, depends only on outbound ports
- `adapter/inbound/http/` — Ktor routes, depend on inbound port interfaces
- `adapter/inbound/websocket/` — Ktor WebSocket handler + room registry
- `adapter/outbound/persistence/` — `InMemory*` repository implementations
- `adapter/outbound/realtime/` — `WebSocketEventPublisher`

Wiring (manual DI) happens in `Application.kt`.
