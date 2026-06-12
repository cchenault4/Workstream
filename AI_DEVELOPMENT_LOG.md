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

Proceed with Phase 1 to create build, test harness, Ktor engine and the rest of the scaffold.

    Note: Tests passed.

Proceed with Phase 2 to create the domain model.

    Note: Tests passed.

I would like to change the plan. Please add the input ports and DTOs (if any). Add comments that describe the purpose, 
assumptions, requirements and invariants for each function.

    Note: The DTOs were going to be put into the wrong directory.

The DTOs should be visible to the inbound ports. They should be part of the application, not the adapters.  

    Note: Tests passed.
