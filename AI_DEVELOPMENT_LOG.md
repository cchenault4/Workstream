I want to use Kotlin and Ktor. Use Gradle for builds. Put code into the namespace digital.honeybadger.workflow. Please 
start by restated the requirements as you understand them.

Use Ktor WebSockets. I would like to conform with a Hexagonal architecture and do TDD. If you don't have any more 
questions, please come up with a minimal implementation plan.

I agree that inbound ports aren't really necessary, but I'd like to use them anyway.

What do you think of the following file layout: 

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

Proceed with Phase 1 to create build, test harness, Ktor engine and the rest of the scaffold.

Proceed with Phase 2 to create the domain model.