# AI Development Log

## Tools Used

- **Claude Code** (Anthropic CLI) — primary tool for the entire exercise: planning, architecture,
  code generation, test writing, and wiring.

---

## Planning Prompts

### Understanding the assignment

> *"I want to use Kotlin and Ktor. Use Gradle for builds. Put code into the namespace
> `digital.honeybadger.workflow`. Please start by restating the requirements as you understand them."*

Claude Code produced a structured restatement of the spec covering the domain model, all nine REST
endpoints, and the real-time contract. Reviewing it confirmed we shared the same understanding before
writing a line of code.

### Identifying open questions and constraints

> *"Use Ktor WebSockets. I would like to conform with a Hexagonal architecture and do TDD.
> If you don't have any more questions, please come up with a minimal implementation plan."*

This surfaced the one real open question the spec left unanswered: Socket.IO has no Ktor server
library, so we agreed upfront to use native Ktor WebSockets with a comparable room/broadcast
protocol. Having that decision logged before implementation prevented scope drift later.

### Breaking work into phases

> *"What do you think of the following file layout: [proposed package structure]"*

I proposed a revised hexagonal layout after reviewing the AI's initial plan. Claude Code confirmed
the design was cleaner, flagged the `use_case` underscore as a Kotlin naming convention violation,
and we locked the structure before any code was written. The agreed phases were:

1. Scaffold (Gradle, Ktor engine, health endpoint)
2. Domain model + ReadinessService (TDD)
3. Use cases (TDD with MockK) — outbound ports emerge from use case needs
4. In-memory persistence adapters (TDD)
5. WebSocket adapter (registry, publisher, route)
6. HTTP adapter (all nine routes, StatusPages)
7. Wire + end-to-end verification

---

## Code Generation Prompts

### Generating the domain model (Phase 2)

> *"Proceed with Phase 2 to create the domain model."*
> *"Please add some comments that explain the purpose and scope of each class."*

Claude Code generated all five domain model files and `ReadinessService`, then added KDoc comments
covering purpose, assumptions, and invariants on the second prompt. The comments on `QuestionType`
(explaining which types gate readiness and which don't) and `ReadinessState` (noting it is derived
and never stored) were particularly valuable.

### Generating use cases and letting outbound ports emerge (Phase 3)

> *"Would it be better to implement the use cases so that you know what the outbound ports
> should look like?"*

This was a planning prompt that changed the implementation order. By writing use case tests first
with MockK and letting the port interfaces emerge from what the tests actually needed, we avoided
speculative repository methods. The resulting ports (`WorkstreamRepository`, `PlanRepository`,
`ActivityRepository`, `EventPublisher`) contained exactly the operations called by the use cases —
no more.

### Reviewing architecture before implementing DTOs and inbound ports

> *"I would like to change the plan. Please add the inbound ports and DTOs (if any). Add comments
> that describe the purpose, assumptions, requirements, and invariants for each function."*

Claude Code produced the three inbound port interfaces and all request DTOs with detailed KDoc.
The comments drove several design clarifications: for example, the invariant that
`UpdateWorkstreamRequest` with all-null fields must still refresh `updatedAt`, and that
`UpsertPlanRequest` uses PUT semantics (full replace, not merge).

### Simplifying the WebSocket publisher

> *"Proceed with Phase 7."*

During wiring, Claude Code proactively refactored `WebSocketEventPublisher` from taking a
`CoroutineDispatcher` (which created a new unstructured `CoroutineScope` on every broadcast) to
taking a `CoroutineScope` so that broadcast coroutines are tied to the application lifecycle and
cancelled cleanly on shutdown. The change was noticed and fixed before I had to ask.

### Code review and fixes

> *"Please do a final review of the code."*
> *"Please fix all findings."*

Claude Code ran a multi-angle review and surfaced five findings. I asked for all of them to be
fixed. The most significant finding was that `readyForReview` and `readyForPR` were always
identical to `verificationReady` — they were hollow fields with no independent meaning. The fix
extended `ReadinessService` to accept activity events and defined proper gate semantics:
`readyForReview` requires a `VERIFICATION` activity to have been posted; `readyForPR` requires a
`REVIEW` activity. This changed the domain model and the integration test in a meaningful way.

---

## Human Overrides

### 1. Inbound ports were omitted from the initial plan

The AI stated that inbound port interfaces "aren't really necessary" at this scale and omitted them
from the initial plan. I insisted on including them anyway for architectural consistency. The AI
added them without pushback. The interfaces turned out to be valuable: they gave the HTTP adapter a
clean dependency target and made the application layer's API explicit and documentable.

### 2. Ports placed in the wrong package

The AI's initial proposal put ports in `domain/port/`. I redirected them to `application/port/`
so that the `domain/` package stays a pure model layer with no awareness of ports, adapters, or
application concerns. This is a stricter interpretation of hexagonal architecture that I preferred.

### 3. DTOs placed in the adapter layer

The AI proposed putting request DTOs in `adapter/inbound/http/dto/`. I redirected them to
`application/dto/` so that the inbound port interfaces (which reference DTOs as method parameters)
could reference them without creating an upward adapter → application dependency. The AI accepted
the correction and explained the implication correctly.

### 4. `WsServerMessage` placed in the inbound package

The AI created `WsServerMessage` alongside `WsClientMessage` in `adapter/inbound/websocket/`.
I pointed out that a server-to-client message belongs in the outbound package. The AI split
the file and moved `WsServerMessage` to `adapter/outbound/realtime/` where it is used by
`WebSocketEventPublisher`.

### 5. File named `WsMessages.kt` instead of `WsClientMessage.kt`

After the split, the inbound file retained the name `WsMessages.kt` even though it only contained
`WsClientMessage`. I asked for it to be renamed to match its contents.

### 6. Implementation order of use cases vs. outbound ports

The original plan defined outbound port interfaces before implementing use cases. I proposed
reversing this so the ports would emerge from what the use cases actually needed. The AI agreed,
explaining that this avoids speculative interface methods. The reversal resulted in leaner,
more accurate port contracts.

---

## Verification

### Automated tests

All code was written test-first. The test suite covers:

| Layer | Type | Count |
|-------|------|-------|
| `ReadinessService` | Unit | 14 |
| `WorkstreamService` | Unit (MockK) | 7 |
| `PlanService` | Unit (MockK) | 11 |
| `ActivityService` | Unit (MockK) | 5 |
| In-memory repositories | Unit | 11 |
| `WebSocketSessionRegistry` | Unit (MockK) | 6 |
| `WebSocketEventPublisher` | Unit (MockK) | 3 |
| WebSocket route | Integration (testApplication) | 3 |
| HTTP routes | Integration (testApplication) | 19 |
| End-to-end lifecycle | Integration (testApplication) | 2 |
| **Total** | | **81** |

All 81 tests pass on every commit via `gradle test`.

### Manual smoke test

After wiring in Phase 7, the server was started with `gradle run` and verified manually:

```bash
# Health check
curl http://localhost:8080/health
# → {"status":"ok"}

# Create workstream
curl -X POST http://localhost:8080/workstreams \
  -H "Content-Type: application/json" \
  -d '{"title":"Test","description":"desc","requester":"alice","priority":"HIGH"}'
# → {"id":"...","status":"NEW","createdAt":"...","updatedAt":"..."}
```

### Known limitations

- **WebSocket broadcast timing in tests**: the `WebSocketClient receives activity:created broadcast`
  integration test uses a `delay(50)` to allow the server handler coroutine to process the join
  frame before the HTTP post triggers a broadcast. This is a pragmatic workaround; a production
  system would use explicit acknowledgement or a subscription confirmation message.
- **In-memory storage**: all state is lost on restart. Replacing the three `InMemory*` repository
  implementations with database-backed ones requires no changes outside `adapter/outbound/persistence/`
  and `Application.kt`.

---

## Phase 2: Frontend SPA

After the backend was complete, a Vue 3 + Vite single-page application was built using Claude Code
as the sole implementation tool. All architectural decisions, feature choices, and UI direction came
from human prompts; Claude Code translated them into working code.

### Tech stack

- **Vue 3.5** with `<script setup>` and Composition API
- **Vue Router 4**
- **Vite 8** (dev server with `/api` and `/ws` proxy to port 8080)
- **Vitest 4** + **@vue/test-utils** + **happy-dom** for testing
- No CSS framework — hand-written scoped styles and a shared `style.css`

### Feature development

#### Workstreams list (`WorkstreamsView`)

> *"Now let's add non-goals."*
> *"Now let's add the verification plan."*

The list view and create form were built incrementally through short, directive prompts. Each prompt
extended the existing component rather than replacing it.

#### Workstream detail (`WorkstreamDetailView`)

The detail view was the most iterative part of the frontend. Key prompts and their outcomes:

> *"Now I want to add three tabs to the WorkstreamDetail page: Implementation Plan, Readiness and Activity."*

Claude Code restructured the flat detail page into a tabbed layout. The plan form and display,
readiness gate grid, and activity feed each became a separate tab panel. Readiness was extracted
from its previous location inside the plan card and given its own dedicated tab.

> *"Please move the connection notification to the Workflows page. When someone is in Workflow
> detail, they already know they are connected. I'm not sure what the text should be, but let's
> change it from 'Live' to 'Active'."*

The presence indicator was moved from the detail page to the workstreams list table. Claude Code
surfaced "Active" as the label and placed it as a badge in a dedicated column next to the title.

#### Real-time presence and list updates

The presence system required three implementation iterations before working correctly:

1. **Attempt 1** — `broadcastAll` / `allSessions`: sessions were iterated from the registry but
   the receiving component never received frames. Root cause: the helper used a different session
   collection path than the proven `broadcast(room, message)` path.
2. **Attempt 2** — `scope.launch { broadcastAll }`: wrapping in a coroutine fixed the suspension
   issue but broadcasts still did not arrive. The underlying `allSessions` iteration was still
   broken.
3. **Attempt 3** — Dedicated internal room (`__workstreams__`): clients sending
   `workstreams:subscribe` join a special room. The publisher calls `broadcast("__workstreams__",
   message)`, reusing the proven room-broadcast mechanism. This worked immediately.

This experience illustrates a common pattern with AI-assisted debugging: when a fix attempt does
not work, the most productive next step is often to re-examine the fundamental mechanism rather
than patch around the symptom.

> *"You can replace presence with workstreams."*

After the system was working with a `__presence__` room, the human directed renaming to
`__workstreams__` and `workstreams:subscribe` to better reflect that the channel carries both
presence and data updates. Claude Code propagated the rename across the backend route handler,
frontend composable, and all references in a single pass.

#### Active field in REST list endpoint

> *"Now that we have Active being updated in realtime on the Workstreams page, let's update all
> of the data in Workstreams in realtime. Also, let's add active to the REST call to get workstreams."*

Claude Code introduced a `WorkstreamSummary` DTO with an `active: Boolean` field derived at
request time from `registry.activeRooms()`. The list endpoint now returns live presence state
without touching the domain model. The frontend initialises `activeWorkstreamIds` from this field
on load, then keeps it current via WebSocket.

#### Duplicate-on-create race condition

Two instances of the same race condition appeared at different times:

1. **Activity duplication**: posting an activity event via the form caused it to appear twice —
   once from the immediate HTTP response and once from the WebSocket broadcast that fired while
   the POST was still in flight. Fixed with an `id`-based dedup check before `unshift`.
2. **Workstream creation duplication**: the same race existed in the create-workstream form. The
   same dedup pattern was applied.

### Component and composable extraction

> *"What do you think about creating some components?"*
> *"There is a WebSocket in WorkstreamsView and some low level detail around that that I'd like to
> move down. Please move that to useWorkStreamSocket."*

Claude Code proposed a short list of extraction candidates; the human selected which to proceed
with. The following were extracted:

| What | Where | Why |
|------|-------|-----|
| `Badge.vue` | `components/` | Badge markup was duplicated across both views |
| `StringList.vue` | `components/` | Read-only string list used for non-goals, assumptions, verification plan |
| `StringListField.vue` | `components/` | Editable string list (add/remove rows) used in the plan form |
| `useWorkstreamSocket` | `composables/` | Detail page WS lifecycle (join/leave, message dispatch) |
| `useWorkstreamsSocket` | `composables/` | List page WS subscription (presence + data updates) |
| `utils/badges.ts` | `utils/` | Badge variant class maps shared between views |
| `utils/format.ts` | `utils/` | `formatDate` / `formatDateTime` shared between views |

### Human overrides — frontend

**1. UI layout and visual design decisions**

All layout decisions were human-directed: the three-line header (title + meta / description /
priority+status), the width cap on the status and priority selectors, column sizing in the
workstreams table, and the tab layout for the detail view. Claude Code implemented each directive
but did not propose the visual structure unprompted.

**2. Naming: "Active" not "Live"**

Claude Code used "Live" in the initial presence badge. The human specified "Active" as the correct
label. The rename was applied to the badge text, column header, and composable variable names.

**3. API method naming convention**

> *"Let's add the word Workstream to the api calls for workstreams. For now, just change list,
> create, get and update."*

Claude Code had named the API methods `list`, `create`, `get`, `update`. The human directed
renaming to `listWorkstreams`, `createWorkstream`, `getWorkstream`, `updateWorkstream` to make
call sites self-documenting. Claude Code applied the rename across all call sites.

**4. Scope of component extraction**

When Claude Code proposed abstracting Open Questions and Phases into components alongside the
string-list components, the human chose to scope the extraction to the simpler string lists only:

> *"Let's skip OpenQuestions and Phases for now. I may come back to them."*

This prevented premature abstraction of the more complex structures.

### Frontend test suite

> *"We need tests for the front end. How do you recommend creating a comprehensive test package?"*
> *"Yes, go ahead."*

Claude Code proposed the stack (Vitest + @vue/test-utils + happy-dom, with `vi.mock` for the API
layer rather than MSW), explained the tradeoff, and implemented the full suite after human
approval. 64 tests across 8 files:

| Layer | Files | Tests | What is covered |
|-------|-------|-------|-----------------|
| Utils | 2 | 8 | Badge class maps, date formatting |
| Components | 3 | 12 | Badge rendering, StringList empty/non-empty, StringListField add/remove |
| Composables | 1 | 13 | WS join/leave protocol, all message type dispatch, malformed frame handling, dedup of presence ids |
| Views | 2 | 31 | Load/error states, create form, navigation, dedup race conditions, real-time update handlers, tab switching, plan/readiness/activity display, edit flow |

The composable tests use a `MockWebSocket` class that replaces the global `WebSocket` constructor.
The view tests mock the composable module and capture the registered handlers, allowing tests to
simulate WebSocket events directly without a real connection.

The race-condition dedup tests use deferred promises (a `Promise` whose `resolve` is held outside
the mock) to control the order in which the HTTP response and the WebSocket broadcast are processed,
verifying that only one item appears regardless of which arrives first.

### Verification — combined (after Phase 2)

| Layer | Tests | Runner |
|-------|-------|--------|
| Backend (Kotlin) | 81 | `gradle test` |
| Frontend (TypeScript/Vue) | 64 | `npm test` |
| **Total** | **145** | |

---

## Phase 3: Presence Architecture Refactoring

After the frontend was complete, a code review identified that presence tracking had leaked business
logic into the adapter layer. Claude Code was used to design and execute a targeted refactoring
under human direction.

### Identifying the violation

> *"Part of the function of this project is that we are tracking whether someone is present in a
> Workstream. We are using a hexagonal architecture, but the presence behavior is not represented
> in a use case. This results in some business logic in the WebSocketRoutes.kt file. Not good.
> Please rethink the inbound ports to correct this."*

Claude Code identified the specific violations: a private `broadcastPresence` function in the
WebSocket route that validated workstream existence, computed the `active` boolean, and called the
publisher; an inline `registry.roomSize(wid) > 0` business rule; and direct dependencies on both
`WorkstreamUseCase` and `EventPublisher` from within an adapter.

The proposed fix: a new `PresenceUseCase` inbound port, a `PresenceRegistry` outbound port, and a
`PresenceService` implementation.

### Human overrides — presence refactoring

**1. `joinedRooms` belongs in the use case, not the route**

Claude Code's initial implementation moved the broadcast logic into `PresenceService` but left
`joinedRooms: MutableSet<String>` as local state in the WebSocket route handler. The human
directed this to move to `PresenceRegistry` through `PresenceUseCase`:

> *"The WebSocketRoutes.kt file should not be keeping track of joinedRooms. It should be storing
> this information in the PresenceRegistry through the PresenceUseCase. The WebSocketSessionRegistry
> can keep track of the sessions, but it should not be keeping track of the active workstreams. It
> should defer to the use case for that."*

This also drove the removal of `activeRooms()` and `roomSize()` from `WebSocketSessionRegistry` —
those methods were presence concerns that belonged in `PresenceRegistry`.

**2. Do not add new tracking mechanisms — only refactor existing ones**

Claude Code proposed a `PresenceRegistry` with session-to-workstream reverse mapping
(`join(sessionId, workstreamId)`, `leaveAll(sessionId)`). The human rejected this as scope creep:

> *"We are only keeping track of how many sessions are associated with each workstream.
> Let's stick with refactoring and not add more functionality."*

The final `PresenceRegistry` outbound port tracks only subscriber counts (`join(workstreamId)`,
`leave(workstreamId)`, `isActive`, `activeWorkstreamIds`). For disconnect cleanup, `leaveAll` was
added to `WebSocketSessionRegistry` — it already held the session-to-room data needed to answer
"which rooms was this session in?" at the adapter level, without the application layer needing to
track it. This was accepted after a productive clarifying exchange about why some tracking is
unavoidable.

**3. `PresenceRegistry` must not be accessed directly from adapters**

After the initial implementation, Claude Code had `WebSocketEventPublisher` (an outbound adapter)
calling `presenceRegistry.isActive()` to compute the `active` flag, and `WorkstreamRoutes` (an
inbound adapter) calling `presenceRegistry.activeWorkstreamIds()` for the list endpoint. The human
identified both as violations:

> *"Since we decided that presence was part of the application, the PresenceRegistry should not be
> accessed directly by the adapters. It should only be accessed by the use cases."*

Fixes:
- `EventPublisher.publishWorkstreamUpdate` gained an explicit `active: Boolean` parameter; the
  publisher became a pure transport adapter that sends what it is told
- `WorkstreamService` received `PresenceRegistry` and passes `presenceRegistry.isActive(id)` when
  calling `publishWorkstreamUpdate`
- `PresenceUseCase` gained `activeWorkstreamIds()` so the HTTP route could call a use case instead
  of the outbound port

**4. `WorkstreamUseCase.list()` should return `WorkstreamSummary`**

> *"Let's remove the presenceUseCase from WorkstreamRoutes and have WorkstreamUseCase.list()
> return WorkstreamSummaries."*

`WorkstreamSummary` was in `adapter/inbound/http/` — correct at the time, but once a use case
returned it, it needed to move to `application/dto/` to avoid an upward adapter dependency. Claude
Code identified this and moved the DTO before updating the use case signature. The route was
reduced to a single `call.respond(useCase.list())` with no presence dependency at all. The
`activeWorkstreamIds()` method on `PresenceUseCase`, added in the previous step, was then removed
since nothing called it anymore.

### What the refactoring produced

The final state:
- `PresenceRegistry` (outbound port) — counts only; accessed exclusively from use cases
- `PresenceUseCase` (inbound port) — `workstreamJoined` / `workstreamLeft`; called by the WebSocket
  route with no business logic remaining in the route handler
- `PresenceService` — increments/decrements counts, resolves workstream, publishes updates
- `WebSocketSessionRegistry` — broadcast sessions only; `leaveAll` supports disconnect cleanup
  without any state in the route handler
- `EventPublisher.publishWorkstreamUpdate(workstream, active: Boolean)` — explicit `active`
  parameter; no outbound port is queried inside the adapter
- `WorkstreamUseCase.list()` → `List<WorkstreamSummary>`; presence resolved internally by
  `WorkstreamService`

### Verification — after Phase 3

| Layer | Tests | Runner |
|-------|-------|--------|
| Backend (Kotlin) | 85 | `gradle test` |
| Frontend (TypeScript/Vue) | 64 | `npm test` |
| **Total** | **149** | |
