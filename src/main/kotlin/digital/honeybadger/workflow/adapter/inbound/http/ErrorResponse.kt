package digital.honeybadger.workflow.adapter.inbound.http

import kotlinx.serialization.Serializable

/** Uniform error body returned by all HTTP error responses. */
@Serializable
data class ErrorResponse(val error: String)
