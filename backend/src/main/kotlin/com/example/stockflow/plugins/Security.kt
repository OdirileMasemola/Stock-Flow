package com.example.stockflow.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.http.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.stockflow.config.AppConfig

fun Application.configureSecurity() {
    
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Access to 'api' routes"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(AppConfig.jwtSecret))
                    .withAudience(AppConfig.jwtAudience)
                    .withIssuer(AppConfig.jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
            }
        }
    }
}
