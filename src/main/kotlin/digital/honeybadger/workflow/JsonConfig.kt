package digital.honeybadger.workflow

import kotlinx.serialization.json.Json

/**
 * Single shared Json instance used across all inbound parsing and outbound serialization.
 * [ignoreUnknownKeys] is true so that clients may send extra fields without breaking deserialization.
 */
val appJson = Json { ignoreUnknownKeys = true }
