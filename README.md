# Agentic Workstream System

A full-stack application that models how engineering work moves through an AI-assisted workflow.
Workstreams are created, implementation plans are attached, agents emit activity events, and
participants receive real-time updates via WebSocket.

The original exercise spec is in [SPEC.md](SPEC.md).
AI tool usage is documented in [AI_DEVELOPMENT_LOG.md](AI_DEVELOPMENT_LOG.md).

## Tech Stack

**Backend**
- **Kotlin 2.2.0** + **Ktor 3.1.3** (Netty engine)
- **Gradle** (Kotlin DSL, JVM target 24)
- **kotlinx.serialization** for JSON, **kotlinx.datetime** for timestamps
- **MockK** + Ktor `testApplication` for testing
- In-memory storage (no database)

**Frontend**
- **Vue 3.5** (Composition API, `<script setup>`)
- **Vue Router 4**
- **Vite 8** (dev server with `/api` and `/ws` proxy to port 8080)
- **Vitest 4** + **@vue/test-utils** + **happy-dom** for testing

## Running

Start both servers in separate terminals:

```bash
# Terminal 1 — backend (http://localhost:8080)
gradle run

# Terminal 2 — frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 in a browser. The Vite dev server proxies all `/api` and `/ws`
requests to the backend, so no CORS configuration is needed.

### Backend commands

```bash
gradle run           # start the server on port 8080
gradle test          # run all 81 backend tests
gradle build         # compile + test + assemble jar
```

Single test class:

```bash
gradle test --tests "digital.honeybadger.workflow.SomeTest"
```

### Frontend commands

```bash
cd frontend
npm run dev          # start Vite dev server on http://localhost:5173
npm test             # run all 64 frontend tests (single pass)
npm run test:watch   # run tests in watch mode
```

---

## Frontend

The SPA has two views:

**Workstreams list** (`/workstreams`)
- Create and browse workstreams
- Real-time **Active** badge on any workstream currently open in another browser session
- List updates in real time when workstreams are created or modified elsewhere

**Workstream detail** (`/workstreams/:id`)
- In-place editing of title and description
- Priority and status selectors
- Three tabs:
  - **Implementation Plan** — goal, non-goals, assumptions, open questions, phases, verification plan; inline edit form
  - **Readiness** — five derived gate checks (blocking questions resolved, phases complete, verification ready, ready for review, ready for PR)
  - **Activity** — chronological event feed with agent name and type badge; add-event form

All tabs update in real time via WebSocket when another agent or browser session makes changes.

---

## REST API

All request and response bodies are JSON. Timestamps are ISO-8601 strings.

### Workstreams

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/workstreams` | Create a workstream |
| `GET` | `/workstreams` | List all workstreams (includes `active` presence field) |
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

**List response** (`GET /workstreams`) returns `WorkstreamSummary` objects with an additional field:
```json
{ "active": true }
```
`active` is `true` when at least one WebSocket client currently has that workstream open.

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
{ "type": "workstream:join",      "workstreamId": "<id>" }
{ "type": "workstream:leave",     "workstreamId": "<id>" }
{ "type": "workstreams:subscribe"                        }
```

`workstream:join` / `workstream:leave` subscribe to events for a single workstream (used by the
detail page). `workstreams:subscribe` subscribes to presence and data updates for all workstreams
(used by the list page). All subscriptions are cleaned up automatically when the connection closes.

### Server → Client

```json
{ "type": "activity:created",    "data": { ...ActivityEvent } }
{ "type": "workstream:updated",  "data": { ...Workstream } }
{ "type": "plan:updated",        "data": { ...Plan } }
{ "type": "workstream:presence", "data": { "workstreamId": "<id>", "active": true } }
```

`workstream:presence` is sent to `workstreams:subscribe` clients when any client joins or leaves a
workstream room. Broadcasts are best-effort; dead sessions are evicted automatically on the first
failed send.

**Quick test with [websocat](https://github.com/vi/websocat):**

```bash
websocat ws://localhost:8080/ws
# then type:
{"type":"workstream:join","workstreamId":"<id>"}
```

---

## Architecture

### Backend

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

### Frontend

```
frontend/src/
  api/            — workstreams.ts: typed fetch wrappers for all REST endpoints
  composables/    — useWorkstreamSocket (detail page WS), useWorkstreamsSocket (list page WS)
  components/     — Badge, StringList, StringListField
  views/          — WorkstreamsView, WorkstreamDetailView
  types/          — Shared TypeScript interfaces (Workstream, Plan, ActivityEvent, …)
  utils/          — badges.ts (variant class maps), format.ts (date helpers)
  __tests__/      — Vitest test suite (utils, components, composables, views)
```

The frontend has no state management library. Each view owns its own reactive state; composables
encapsulate WebSocket lifecycle (open, send, receive, close on unmount). The API module is a thin
wrapper around `fetch` with no middleware, making it straightforward to stub in tests with
`vi.mock`.

### Test coverage

| Layer | Tests | Runner |
|-------|-------|--------|
| Backend (Kotlin) | 81 | `gradle test` |
| Frontend (TypeScript/Vue) | 64 | `npm test` |
| **Total** | **145** | |
