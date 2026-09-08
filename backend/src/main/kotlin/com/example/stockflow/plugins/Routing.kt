package com.example.stockflow.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.*
import com.example.stockflow.services.UserService
import com.example.stockflow.services.RoleService
import com.example.stockflow.models.RegisterRequest
import com.example.stockflow.models.LoginRequest
import com.example.stockflow.models.GoogleAuthRequest
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureRouting() {
    val userService = UserService()
    val roleService = RoleService()

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

        get("/api/roles") {
            call.respond(roleService.listRoles())
        }

        route("/api/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()
                val response = userService.registerUser(request)
                call.respond(HttpStatusCode.Created, response)
            }
            post("/login") {
                val request = call.receive<LoginRequest>()
                val response = userService.authenticateUser(request)
                call.respond(HttpStatusCode.OK, response)
            }
            post("/google") {
                val request = call.receive<GoogleAuthRequest>()
                val response = userService.authenticateWithGoogle(request)
                call.respond(HttpStatusCode.OK, response)
            }
            
            authenticate("auth-jwt") {
                get("/test") {
                    val principal = call.principal<JWTPrincipal>()
                    val username = principal!!.payload.getClaim("username").asString()
                    val userId = principal.payload.getClaim("userId").asInt()
                    val roleId = principal.payload.getClaim("roleId").asInt()
                    
                    call.respond(mapOf(
                        "message" to "Authentication successful!",
                        "user" to mapOf(
                            "id" to userId,
                            "username" to username,
                            "roleId" to roleId
                        )
                    ))
                }
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
