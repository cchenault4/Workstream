# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Full-stack agentic workstream system: a **Kotlin/Ktor backend** and a **Vue 3 SPA frontend**.
Engineering work moves through an AI-assisted workflow — workstreams are created, implementation
plans are attached, agents emit activity events, and participants receive real-time updates via
WebSocket.

The full spec is in `SPEC.md`. `README.md` is the human-facing docs. `AI_DEVELOPMENT_LOG.md`
documents AI tool usage and is required as part of the submission.

## Domain Model

**Workstream** — top-level unit of work:
- Fields: `id`, `title`, `description`, `requester`, `priority` (`LOW|MEDIUM|HIGH`), `status` (`NEW|PLANNING|EXECUTING|REVIEWING|VERIFIED|BLOCKED`), `createdAt`, `updatedAt`

**Implementation Plan** — one per workstream:
- Fields: `goal`, `nonGoals[]`, `assumptions[]`, `openQuestions[]`, `phases[]`, `verificationPlan[]`
- `openQuestions.type`: `BLOCKING|ASSUMABLE|DEFERRABLE`; optional `resolution`
- `phases.status`: `PENDING|IN_PROGRESS|COMPLETE|BLOCKED`
- Derived readiness: `blockingQuestionsResolved`, `allPhasesComplete`, `verificationReady`, `readyForReview`, `readyForPR`

**ActivityEvent** — emitted by agents against a workstream:
- Fields: `id`, `workstreamId`, `agentName`, `type` (`CONTEXT_DISCOVERY|PLANNING|IMPLEMENTATION|REVIEW|VERIFICATION|HANDOFF`), `message`, `createdAt`
- Creating an event broadcasts it in real time to WebSocket subscribers

## REST API

```
POST   /workstreams          → Workstream
GET    /workstreams          → WorkstreamSummary[]  (includes active: Boolean)
GET    /workstreams/:id      → Workstream
PATCH  /workstreams/:id      → Workstream
PUT    /workstreams/:id/plan → Plan
GET    /workstreams/:id/plan → Plan
GET    /workstreams/:id/readiness → ReadinessState
POST   /workstreams/:id/activity  → ActivityEvent
GET    /workstreams/:id/activity  → ActivityEvent[]
```

`GET /workstreams` returns `WorkstreamSummary` (not `Workstream`) — a DTO in
`application/dto/WorkstreamSummary.kt` that adds `active: Boolean`. The `active` flag is resolved
by `WorkstreamUseCase.list()`, which queries `PresenceRegistry` internally. The domain `Workstream`
model has no `active` field.

## Real-Time (Ktor WebSockets — not Socket.IO)

There is no Socket.IO library for Ktor. Real-time uses native Ktor WebSockets with a JSON envelope
protocol. `WebSocketSessionRegistry` manages broadcast sessions; `WebSocketEventPublisher` sends
frames; `PresenceUseCase` owns all presence business logic.

**Client → server:**
```json
{ "type": "workstream:join",      "workstreamId": "<id>" }   // detail page: subscribe to one workstream
{ "type": "workstream:leave",     "workstreamId": "<id>" }   // detail page: unsubscribe
{ "type": "workstreams:subscribe"                        }   // list page: subscribe to all presence + updates
```

**Server → client:**
```json
{ "type": "activity:created",    "data": { ...ActivityEvent } }
{ "type": "workstream:updated",  "data": { ...Workstream, "active": true } }
{ "type": "plan:updated",        "data": { ...Plan } }
```

`workstream:updated` carries the `active` flag on every broadcast — both for data mutations and
for presence changes (join/leave). The list page uses this to drive the "Active" badge in real time.

**Internal rooms:** `__workstreams__` is an internal room (identified by the `__` prefix). The
publisher broadcasts `workstream:updated` to both the workstream's own room and `__workstreams__`
so list-page subscribers also receive data updates and presence changes.

**Presence flow:** On `workstream:join`, the WebSocket route registers the session in
`WebSocketSessionRegistry` (for broadcasting) and calls `PresenceUseCase.workstreamJoined`, which
increments the `PresenceRegistry` count and publishes `workstream:updated` with `active=true`.
On `workstream:leave` or disconnect, the reverse happens — `PresenceUseCase.workstreamLeft`
decrements the count and publishes with the post-leave active state. `WebSocketSessionRegistry`
also tracks which rooms each session occupies (via `leaveAll`) so disconnect cleanup requires no
local state in the route handler.

## Backend Commands

Run from the project root (`/Users/chrischenault/IdeaProjects/Workstream`), **not** from `frontend/`:

```bash
gradle test          # run all 85 backend tests
gradle test --tests "digital.honeybadger.workflow.SomeTest"
gradle run           # start server on port 8080
gradle build         # compile + test + assemble jar
gradle clean run     # clean rebuild before starting (use when stale binary suspected)
```

## Frontend Commands

Run from `frontend/`:

```bash
npm run dev          # Vite dev server on http://localhost:5173
npm test             # run all 63 tests (single pass)
npm run test:watch   # run tests in watch mode
```

The Vite dev server proxies `/api/*` → `http://localhost:8080` (strips `/api` prefix) and
`/ws` → `ws://localhost:8080` so the frontend talks directly to the backend with no CORS config.

## Backend Architecture

Hexagonal (Ports & Adapters). Manual DI in `Application.kt:module()`:

1. In-memory repositories, `DefaultWebSocketSessionRegistry`, and `InMemoryPresenceRegistry` created
2. `WebSocketEventPublisher` created with the `Application` as its `CoroutineScope`
3. Use cases created with repositories, `PresenceRegistry`, and publisher
4. `configurePlugins()` → `configureRouting()` → `configureHttpRoutes(...)` → `configureWebSocketRoutes(...)`

```
domain/model/           — pure data classes/enums, no framework deps
domain/service/         — ReadinessService: pure computation, no I/O
application/dto/        — WorkstreamSummary and other request/response DTOs
application/port/inbound/  — WorkstreamUseCase, PlanUseCase, ActivityUseCase, PresenceUseCase
application/port/outbound/ — repository + EventPublisher + PresenceRegistry interfaces
application/usecase/    — implements inbound ports, depends only on outbound ports
adapter/inbound/http/   — Ktor routes + StatusPages
adapter/inbound/websocket/ — WebSocket route, WebSocketSessionRegistry / DefaultWebSocketSessionRegistry
adapter/outbound/persistence/ — InMemory* repositories + InMemoryPresenceRegistry
adapter/outbound/realtime/    — WebSocketEventPublisher
```

**Key boundary rules:**
- Adapters call inbound ports (use cases) only — never outbound ports directly
- `PresenceRegistry` is an outbound port accessed only from use cases (`PresenceService`,
  `WorkstreamService`), not from adapters
- `EventPublisher.publishWorkstreamUpdate(workstream, active: Boolean)` takes an explicit `active`
  value; the publisher is a pure transport adapter and does not query presence state itself
- `WebSocketSessionRegistry` is adapter-internal infrastructure for broadcasting; it does not
  determine active workstreams (that is `PresenceRegistry`'s job)

## Frontend Architecture

```
frontend/src/
  api/workstreams.ts      — typed fetch wrappers for all REST endpoints
  composables/
    useWorkstreamSocket.ts — useWorkstreamSocket (detail page) + useWorkstreamsSocket (list page)
  components/
    Badge.vue              — <span class="badge" :class="variant">{{ label }}</span>
    StringList.vue         — read-only bulleted list; renders nothing when items is empty
    StringListField.vue    — editable list with add/remove; uses defineModel
  views/
    WorkstreamsView.vue    — list + create form; uses useWorkstreamsSocket
    WorkstreamDetailView.vue — header + 3 tabs (plan / readiness / activity)
  types/workstream.ts     — all shared TypeScript interfaces
  utils/badges.ts         — PRIORITY_CLASS, STATUS_CLASS, etc. badge variant maps
  utils/format.ts         — formatDate, formatDateTime
  __tests__/              — Vitest suite (mocks/websocket.ts, utils, components, composables, views)
```

**WebSocket composables:**
- `useWorkstreamSocket(id, handlers)` — joins/leaves a single workstream room; calls
  `onActivity`, `onWorkstreamUpdated`, `onPlanUpdated` handlers; sends `workstream:leave` on unmount
- `useWorkstreamsSocket(handlers?)` — sends `workstreams:subscribe`; updates `activeWorkstreamIds`
  ref on `workstream:updated` messages with `active` field; calls `onWorkstreamUpdated` handler

**Dedup pattern:** Both activity creation and workstream creation have a race condition where the
WebSocket broadcast arrives before the POST response. The guard is:
```typescript
if (!list.value.some(item => item.id === incoming.id)) {
  list.value.unshift(incoming)
}
```
Applied in `addActivity()`, `submit()` (WorkstreamsView), and the WS `onActivity` handler.

## Tech Stack Summary

| | Backend | Frontend |
|---|---|---|
| Language | Kotlin 2.2.0 | TypeScript |
| Framework | Ktor 3.1.3 (Netty) | Vue 3.5 + Vite 8 |
| Build | Gradle 9.x (Kotlin DSL) | npm |
| Testing | MockK + testApplication (85 tests) | Vitest + @vue/test-utils (63 tests) |
| Storage | In-memory (no DB) | — |
| Real-time | Ktor WebSockets | Native WebSocket API |
