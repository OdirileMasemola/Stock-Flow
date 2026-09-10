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
import com.example.stockflow.services.SupplierService
import com.example.stockflow.services.PurchaseOrderService
import com.example.stockflow.services.DashboardService
import com.example.stockflow.models.RegisterRequest
import com.example.stockflow.models.LoginRequest
import com.example.stockflow.models.GoogleAuthRequest
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.UpdateProductRequest
import com.example.stockflow.models.CreateSaleRequest
import com.example.stockflow.models.CreateSupplierRequest
import com.example.stockflow.models.UpdateSupplierRequest
import com.example.stockflow.models.CreatePurchaseOrderRequest
import com.example.stockflow.models.UpdatePurchaseOrderRequest
import com.example.stockflow.models.BadRequestException
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureRouting() {
    val userService = UserService()
    val roleService = RoleService()
    val productService = ProductService()
    val saleService = SaleService()
    val supplierService = SupplierService()
    val purchaseOrderService = PurchaseOrderService()
    val dashboardService = DashboardService()

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
                get("/low-stock") {
                    call.respond(productService.getLowStockProducts())
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

            route("/api/suppliers") {
                get {
                    call.respond(supplierService.getSuppliers())
                }
                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid supplier ID")
                    call.respond(supplierService.getSupplier(id))
                }
                post {
                    val request = call.receive<CreateSupplierRequest>()
                    val created = supplierService.createSupplier(request)
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid supplier ID")
                    val request = call.receive<UpdateSupplierRequest>()
                    call.respond(supplierService.updateSupplier(id, request))
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid supplier ID")
                    supplierService.deleteSupplier(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/api/purchase-orders") {
                get {
                    call.respond(purchaseOrderService.getPurchaseOrders())
                }
                get("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid purchase order ID")
                    call.respond(purchaseOrderService.getPurchaseOrder(id))
                }
                post {
                    val request = call.receive<CreatePurchaseOrderRequest>()
                    val created = purchaseOrderService.createPurchaseOrder(request)
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid purchase order ID")
                    val request = call.receive<UpdatePurchaseOrderRequest>()
                    call.respond(purchaseOrderService.updatePurchaseOrder(id, request))
                }
                post("/{id}/receive") {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid purchase order ID")
                    call.respond(purchaseOrderService.receivePurchaseOrder(id))
                }
            }

            route("/api/dashboard") {
                get("/summary") {
                    call.respond(dashboardService.getSummary())
                }
            }

            route("/api/reports") {
                get {
                    val range = call.request.queryParameters["range"]
                    call.respond(dashboardService.getReports(range))
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
