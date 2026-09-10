package com.example.stockflow.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.*
import com.example.stockflow.services.UserService
import com.example.stockflow.services.RoleService
import com.example.stockflow.services.ProductService
import com.example.stockflow.services.SaleService
import com.example.stockflow.models.RegisterRequest
import com.example.stockflow.models.LoginRequest
import com.example.stockflow.models.GoogleAuthRequest
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.UpdateProductRequest
import com.example.stockflow.models.CreateSaleRequest
import com.example.stockflow.models.BadRequestException
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureRouting() {
    val userService = UserService()
    val roleService = RoleService()
    val productService = ProductService()
    val saleService = SaleService()

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

        // Product CRUD + Sales/POS — require a valid StockFlow JWT
        authenticate("auth-jwt") {
            route("/api/products") {
                get {
                    call.respond(productService.getProducts())
                }
                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid product ID")
                    call.respond(productService.getProduct(id))
                }
                post {
                    val request = call.receive<CreateProductRequest>()
                    val created = productService.createProduct(request)
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid product ID")
                    val request = call.receive<UpdateProductRequest>()
                    call.respond(productService.updateProduct(id, request))
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid product ID")
                    productService.deleteProduct(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/api/sales") {
                get {
                    call.respond(saleService.getSales())
                }
                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid sale ID")
                    call.respond(saleService.getSale(id))
                }
                post {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw BadRequestException("Authentication required")
                    val userId = principal.payload.getClaim("userId").asInt()
                    val request = call.receive<CreateSaleRequest>()
                    val created = saleService.createSale(userId, request)
                    call.respond(HttpStatusCode.Created, created)
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
