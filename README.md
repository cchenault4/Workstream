# Agentic Workstream Backend

A Kotlin/Ktor backend that models how engineering work moves through an AI-assisted workflow.
Workstreams are created, implementation plans are attached, agents emit activity events, and
participants receive real-time updates via WebSocket.

The original exercise spec is in [SPEC.md](SPEC.md).
AI tool usage is documented in [AI_DEVELOPMENT_LOG.md](AI_DEVELOPMENT_LOG.md).

## Tech Stack

- **Kotlin 2.2.0** + **Ktor 3.1.3** (Netty engine)
- **Gradle** (Kotlin DSL, JVM target 24)
- **kotlinx.serialization** for JSON, **kotlinx.datetime** for timestamps
- **MockK** + Ktor `testApplication` for testing
- In-memory storage (no database)

## Running

```bash
gradle run          # starts on http://localhost:8080
gradle test         # run all 81 tests
gradle build        # compile + test + assemble jar
```

Single test class:

```bash
gradle test --tests "digital.honeybadger.workflow.SomeTest"
```

## REST API

All request and response bodies are JSON. Timestamps are ISO-8601 strings.

### Workstreams

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/workstreams` | Create a workstream |
| `GET` | `/workstreams` | List all workstreams |
| `GET` | `/workstreams/{id}` | Fetch workstream by ID |
| `PATCH` | `/workstreams/{id}` | Partial update (status, title, description, priority) |

**Create request:**
```json
{
  "title": "Add JWT auth",
  "description": "Implement token-based authentication",
  "requester": "alice",
  "priority": "HIGH"
}
```

**Workstream response:**
```json
{
  "id": "3fa85f64-...",
  "title": "Add JWT auth",
  "description": "Implement token-based authentication",
  "requester": "alice",
  "priority": "HIGH",
  "status": "NEW",
  "createdAt": "2026-06-12T10:00:00Z",
  "updatedAt": "2026-06-12T10:00:00Z"
}
```

`priority` values: `LOW`, `MEDIUM`, `HIGH`
`status` values: `NEW`, `PLANNING`, `EXECUTING`, `REVIEWING`, `VERIFIED`, `BLOCKED`

### Implementation Plan

| Method | Path | Description |
|--------|------|-------------|
| `PUT` | `/workstreams/{id}/plan` | Create or replace plan (full replace) |
| `GET` | `/workstreams/{id}/plan` | Fetch current plan |
| `GET` | `/workstreams/{id}/readiness` | Fetch derived readiness state |

**Plan request/response:**
```json
{
  "goal": "Ship JWT authentication",
  "nonGoals": ["OAuth support"],
  "assumptions": ["Postgres is available"],
  "openQuestions": [
    {
      "id": "q1",
      "question": "Token expiry duration?",
      "type": "BLOCKING",
      "resolution": "24 hours per security policy"
    }
  ],
  "phases": [
    {
      "id": "p1",
      "name": "Implementation",
      "objective": "Write the auth middleware",
      "status": "COMPLETE"
    }
  ],
  "verificationPlan": ["Run auth integration tests", "Verify token expiry"]
}
```

`openQuestions.type` values: `BLOCKING`, `ASSUMABLE`, `DEFERRABLE`
`phases.status` values: `PENDING`, `IN_PROGRESS`, `COMPLETE`, `BLOCKED`

**Readiness response:**
```json
{
  "blockingQuestionsResolved": true,
  "allPhasesComplete": true,
  "verificationReady": true,
  "readyForReview": false,
  "readyForPR": false
}
```

Readiness gate semantics:

| Field | True when |
|-------|-----------|
| `blockingQuestionsResolved` | No `BLOCKING` questions without a `resolution` |
| `allPhasesComplete` | All phases have status `COMPLETE` (and at least one exists) |
| `verificationReady` | Both of the above |
| `readyForReview` | `verificationReady` AND a `VERIFICATION` activity has been posted |
| `readyForPR` | `readyForReview` AND a `REVIEW` activity has been posted |

### Activity Events

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/workstreams/{id}/activity` | Append an activity event |
| `GET` | `/workstreams/{id}/activity` | List activity events in insertion order |

**Activity request:**
```json
{
  "agentName": "Verification Runner",
  "type": "VERIFICATION",
  "message": "All 81 tests passed"
}
```

**Activity response:**
```json
{
  "id": "7b4e1f9a-...",
  "workstreamId": "3fa85f64-...",
  "agentName": "Verification Runner",
  "type": "VERIFICATION",
  "message": "All 81 tests passed",
  "createdAt": "2026-06-12T10:05:00Z"
}
```

`type` values: `CONTEXT_DISCOVERY`, `PLANNING`, `IMPLEMENTATION`, `REVIEW`, `VERIFICATION`, `HANDOFF`

### Error responses

All errors return `{"error": "<message>"}` with an appropriate status code:

| Status | Cause |
|--------|-------|
| `400` | Blank required field |
| `404` | Workstream or plan not found |
| `500` | Unexpected server error |

---

## WebSocket

Connect to `ws://localhost:8080/ws`. The server uses a simple JSON envelope protocol (not Socket.IO).

### Client → Server

```json
{ "type": "workstream:join",  "workstreamId": "<id>" }
{ "type": "workstream:leave", "workstreamId": "<id>" }
```

All subscriptions are cleaned up automatically when the connection closes.

### Server → Client

```json
{ "type": "activity:created",   "data": { ...ActivityEvent } }
{ "type": "workstream:updated", "data": { ...Workstream } }
{ "type": "plan:updated",       "data": { ...Plan } }
```

Broadcasts are best-effort. Dead sessions are evicted automatically on the first failed send.

**Quick test with [websocat](https://github.com/vi/websocat):**

```bash
websocat ws://localhost:8080/ws
# then type:
{"type":"workstream:join","workstreamId":"<id>"}
```

---

## Architecture

Hexagonal (Ports & Adapters) with manual dependency injection in `Application.kt`.

```
domain/
  model/          — Workstream, Plan, Phase, OpenQuestion, ActivityEvent, ReadinessState
  service/        — ReadinessService (pure computation, no I/O)

application/
  dto/            — Request DTOs (referenced by inbound ports)
  exception/      — WorkstreamNotFoundException, PlanNotFoundException
  port/
    inbound/      — WorkstreamUseCase, PlanUseCase, ActivityUseCase (interfaces)
    outbound/     — WorkstreamRepository, PlanRepository, ActivityRepository, EventPublisher
  usecase/        — Concrete implementations of the inbound ports

adapter/
  inbound/
    http/         — Ktor routes + StatusPages error mapping
    websocket/    — WebSocket route, session registry
  outbound/
    persistence/  — InMemory* repository implementations
    realtime/     — WebSocketEventPublisher
```

The `domain/` package has no framework or infrastructure dependencies. Outbound port interfaces
were designed to emerge from use case needs (TDD), so they contain no speculative methods.
Swapping in-memory storage for a real database requires only changes to
`adapter/outbound/persistence/` and `Application.kt`.
