package com.example.stockflow

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.stockflow.plugins.*
import com.example.stockflow.database.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize Database
    try {
        DatabaseFactory.init()
    } catch (e: Exception) {
        log.error("Failed to connect to database. Continuing without DB for now.", e)
    }
    
    // Configure Plugins
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureStatusPages()
    configureRouting()
}
