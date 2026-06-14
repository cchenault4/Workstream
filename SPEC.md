# Agentic Workstream Backend

## Objective
Design and implement a backend service for an agentic workstream system using the
language and framework(s) of your choice.

The service should model how work moves through an AI-assisted engineering workflow: a
workstream is created, an implementation plan is attached, agents emit activity events, and
participants receive real-time updates as the work progresses.

This exercise is intentionally designed to be completed with heavy use of AI development tools
such as Claude Code, Codex, Cursor, GitHub Copilot, or similar agents. We are evaluating
both the final implementation and your ability to use AI-first development practices: planning,
decomposition, code generation, review, testing, and verification.

## Scenario
Imagine a lightweight backend for a system inspired by Symphony Alpha.

A user creates a workstream for a piece of engineering work. The workstream may have:
- an implementation plan
- open questions
- phases
- agent activity
- review events
- verification events
- real-time subscribers watching progress

Example activity:
- Context Scout identified relevant files
- Plan Critic flagged a blocking question
- Implementation Agent completed Phase 1
- Diff Prosecutor found scope drift
- Verification Runner passed unit tests

Your task is to build the backend that supports this workflow.

## Functional Requirements

1. **Workstreams**

   Support REST API endpoints to:
   - create a workstream
   - list workstreams
   - fetch a workstream by ID
   - update workstream status

   A workstream should include fields similar to:
   ```
   {
     id: string
     title: string
     description: string
     requester: string
     priority: "low" | "medium" | "high"
     status: "new" | "planning" | "executing" | "reviewing" |
             "verified" | "blocked"
     createdAt: string
     updatedAt: string
   }
   ```

2. **Implementation Plans**

   Support adding and fetching an implementation plan for a workstream.

   A plan should include:
   ```
   {
     goal: string
     nonGoals: string[]
     assumptions: string[]
     openQuestions: {
       id: string
       question: string
       type: "blocking" | "assumable" | "deferrable"
       resolution?: string
     }[]
     phases: {
       id: string
       name: string
       objective: string
       status: "pending" | "in_progress" | "complete" | "blocked"
     }[]
     verificationPlan: string[]
   }
   ```

   The backend should expose derived readiness state, such as:
   ```
   {
     blockingQuestionsResolved: boolean
     allPhasesComplete: boolean
     verificationReady: boolean
     readyForReview: boolean
     readyForPR: boolean
   }
   ```

3. **Agent Activity Events**

   Support REST API endpoints to:
   - add an activity event to a workstream
   - fetch activity events for a workstream

   An activity event should include:
   ```
   {
     id: string
     workstreamId: string
     agentName: string
     type:
       | "context_discovery"
       | "planning"
       | "implementation"
       | "review"
       | "verification"
       | "handoff"
     message: string
     createdAt: string
   }
   ```

   When a new activity event is created, broadcast it in real time to all Socket.IO clients subscribed
   to that workstream.

4. **Real-Time Workstream Updates**

   Use Socket.IO to support:
   - joining a workstream room
   - leaving a workstream room
   - broadcasting new activity events
   - broadcasting workstream status changes
   - broadcasting plan/phase updates if implemented

   Example events:
   ```
   client.emit("workstream:join", { workstreamId })
   client.emit("workstream:leave", { workstreamId })
   ```

   ```
   server.emit("activity:created", activityEvent)
   server.emit("workstream:updated", workstream)
   server.emit("plan:updated", plan)
   ```

## Suggested REST API
You may change naming if you prefer, but document your choices.
```
POST /workstreams
GET  /workstreams
GET  /workstreams/:id
PATCH /workstreams/:id
PUT  /workstreams/:id/plan
GET  /workstreams/:id/plan
GET  /workstreams/:id/readiness
POST /workstreams/:id/activity
GET  /workstreams/:id/activity
```

## AI-First Development Requirement
Please include a file named:
```
AI_DEVELOPMENT_LOG.md
```
This file is part of the evaluation.

Document how you used AI to complete the exercise. Include:

1. **Tools used**

   Example:
   ```
   ## Tools Used
   - Claude Code
   - Codex
   - Cursor
   - ChatGPT
   - GitHub Copilot
   ```

2. **Planning prompts**

   Include at least one prompt used to:
   - understand the assignment
   - create an implementation plan
   - identify open questions
   - break the work into phases

3. **Code generation / review prompts**

   Include at least one prompt used to:
   - generate code
   - review a diff
   - find missing tests
   - simplify the implementation
   - improve validation/error handling

4. **Human overrides**

   Describe where you disagreed with, corrected, or constrained the AI.

   Examples:
   - AI overbuilt the architecture.
   - AI missed an edge case.
   - AI suggested a dependency you rejected.
   - AI wrote shallow tests that you improved.
   - AI broadened scope and you narrowed it.
   - AI failed to model readiness correctly.

5. **Verification**

   Describe how you verified the result.

   Examples:
   - unit tests
   - integration tests
   - Socket.IO test client
   - manual API calls
   - lint/typecheck
   - known limitations

6. **Submission**

   Please check all the code and .md file documentation into a new public github repository and
   send us the link to the repo when you're satisfied with your results.
