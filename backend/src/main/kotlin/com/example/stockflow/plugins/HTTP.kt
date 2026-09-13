package com.example.stockflow.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import com.example.stockflow.config.AppConfig

fun Application.configureHTTP() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        
        // Production Hosts
        if (AppConfig.corsAllowedHosts.contains("*")) {
            anyHost()
        } else {
            AppConfig.corsAllowedHosts.forEach { host ->
                allowHost(host, schemes = listOf("http", "https"))
            }
        }

        // Native Android clients ignore CORS. Restrict browser origins for local tooling.
        allowHost("localhost", schemes = listOf("http", "https"))
        allowHost("127.0.0.1", schemes = listOf("http", "https"))
        allowHost("10.0.2.2", schemes = listOf("http"))
    }
}
