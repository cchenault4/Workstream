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
