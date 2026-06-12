I want to use Kotlin and Ktor. Use Gradle for builds. Put code into the namespace digital.honeybadger.workflow. Please 
start by restated the requirements as you understand them.

Use Ktor WebSockets. I would like to conform with a Hexagonal architecture and do TDD. If you don't have any more 
questions, please come up with a minimal implementation plan.

    Note: After review, I wanted to change the file layout. 
    Note: I should have reviewed the plan more carefully because I changed it later

I agree that inbound ports aren't really necessary, but I'd like to use them anyway.

What do you think of the following file layout: 

```text
domain/                                                                                                                                                                                              
    model/   — Workstream, Plan, Phase, OpenQuestion, ActivityEvent, ReadinessState                                                                                                                                                                   
    service/ — ReadinessService (pure readiness computation)
application/                                                                                                                                                                                                                                         
    port/                                                                                                                                                                                                                                            
        inbound/                                                                                                                                                                                                                                      
        outbound/   — WorkstreamRepository, PlanRepository, ActivityRepository, EvtPub                                                                                                                                                                
    use_case/    — use case classes (one per resource group), depend only on domain
adapter/                                                                                                                                                                                                                                           
      inbound/                                                                                                                                                                                                                                         
        http/       — Ktor route definitions                                                                                                                                                                                                           
        websocket/  — Ktor WebSocket handler + room management                                                                                                                                                                                         
      outbound/                                                                                                                                                                                                                                        
        persistence/ — InMemory* repository implementations                                                                                                                                                                                            
        realtime/    — WebSocketEventPublisher                                                                                                                                                                                                           websocket/  — Ktor WebSocket handler + room management                                                                                                                                                                                        
```
⏺ It's cleaner than my original proposal. Moving ports to application/ keeps the domain/ layer truly pure — just models
and domain logic, no awareness of ports or adapters at all. That's a stricter and more defensible hexagonal boundary.

One minor note: Kotlin package naming convention avoids underscores, so use_case would typically be usecase 
(or usecases). Everything else looks good to me.

Proceed with Phase 1 to create build, test harness, Ktor engine and the rest of the scaffold.

    Note: Tests passed.

Proceed with Phase 2 to create the domain model.

    Note: Tests passed.

Please add some comments that explain the purpose and scope of each class.

I would like to change the plan. Please add the input ports and DTOs (if any). Add comments that describe the purpose, 
assumptions, requirements and invariants for each function.

    Note: The DTOs were going to be put into the wrong directory.

The DTOs should be visible to the inbound ports. They should be part of the application, not the adapters.  

    Note: Tests passed.

Would it be better to implement the Use Cases so that you know what the Outbound Ports should look like?

⏺ Yes, that's the better order. It's the TDD "let the design emerge" principle — writing the use case implementations 
(against mocked dependencies) will reveal exactly which repository methods are needed and nothing more.
Defining outbound ports upfront risks speculative methods that no use case ever calls.

Revised order:

┌───────────┬────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│   Phase   │                                                              What                                                              │
├───────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ ~~3~~ → 3 │ Write use case tests (MockK), implement use cases, outbound port interfaces emerge from what the implementations actually call │
├───────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ ~~4~~ → 4 │ Implement in-memory adapters against those interfaces                                                                          │
└───────────┴────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

The only adjustment: we define the outbound port interfaces alongside the use case implementations as we hit them, rather than in a separate prior step. The tests stay the same — MockK mocks an interface, so the
interface just needs to exist by the time the test compiles.

Proceed

    Note: Tests passed.

I need to review the code before proceeding to the adapters