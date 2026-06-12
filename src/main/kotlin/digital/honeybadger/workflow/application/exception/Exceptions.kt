package digital.honeybadger.workflow.application.exception

/** Thrown when a workstream ID does not correspond to any known workstream. */
class WorkstreamNotFoundException(id: String) :
    RuntimeException("Workstream not found: $id")

/**
 * Thrown when a plan is requested for a workstream that has not yet had one attached.
 * Callers should attach a plan via the upsert operation before querying it.
 */
class PlanNotFoundException(workstreamId: String) :
    RuntimeException("No plan found for workstream: $workstreamId")
