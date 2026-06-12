package digital.honeybadger.workflow

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.configurePlugins() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
    }
    install(ContentNegotiation) {
        json()
    }
}
