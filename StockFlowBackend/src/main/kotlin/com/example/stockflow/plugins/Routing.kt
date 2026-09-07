package com.example.stockflow.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.*
import com.example.stockflow.services.UserService
import com.example.stockflow.models.RegisterRequest

fun Application.configureRouting() {
    val userService = UserService()

    routing {
        get("/") {
            call.respondText("StockFlow API is running!")
        }
        get("/api/health") {
            call.respond(mapOf("status" to "ok", "service" to "StockFlow API"))
        }
        get("/health") {
            call.respond(mapOf("status" to "up"))
        }

        route("/api/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()
                val response = userService.registerUser(request)
                call.respond(HttpStatusCode.Created, response)
            }
        }

        get("/users/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                return@get
            }
            val user = userService.getUser(id)
            if (user != null) {
                call.respond(user)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            }
        }
    }
}
