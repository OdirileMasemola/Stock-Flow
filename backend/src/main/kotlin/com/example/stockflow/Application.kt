package com.example.stockflow

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.stockflow.plugins.*
import com.example.stockflow.database.*
import com.example.stockflow.config.AppConfig

fun main() {
    embeddedServer(Netty, port = AppConfig.serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize Database (Fail fast if it fails)
    DatabaseFactory.init()
    
    // Configure Plugins
    configureSecurity()
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureStatusPages()
    configureRouting()
}
