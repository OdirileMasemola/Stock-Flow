package com.example.stockflow.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.http.content.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import com.example.stockflow.services.UserService
import com.example.stockflow.services.RoleService
import com.example.stockflow.services.ProductService
import com.example.stockflow.services.SaleService
import com.example.stockflow.services.SupplierService
import com.example.stockflow.services.PurchaseOrderService
import com.example.stockflow.services.DashboardService
import com.example.stockflow.services.BusinessService
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
import com.example.stockflow.models.UpdateProfileRequest
import com.example.stockflow.models.UpdateBusinessRequest
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
    val businessService = BusinessService()

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

        // Public product image files (paths stored on products as /uploads/products/...).
        staticFiles("/uploads", productService.uploadsRoot())

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
            route("/api/users/me") {
                get {
                    val userId = currentUserId(call)
                    call.respond(userService.getProfile(userId))
                }
                put {
                    val userId = currentUserId(call)
                    val request = call.receive<UpdateProfileRequest>()
                    call.respond(userService.updateProfile(userId, request))
                }
                post("/image") {
                    val multipart = call.receiveMultipart()
                    var bytes: ByteArray? = null
                    var originalName: String? = null
                    var contentType: String? = null
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                if (part.name == "image" || bytes == null) {
                                    bytes = part.provider().readRemaining().readByteArray()
                                    originalName = part.originalFileName
                                    contentType = part.contentType?.toString()
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }
                    val imageBytes = bytes
                        ?: throw BadRequestException("Missing image file. Use multipart field name \"image\".")
                    call.respond(
                        HttpStatusCode.Created,
                        userService.uploadProfileImage(imageBytes, originalName, contentType)
                    )
                }
            }

            route("/api/business") {
                get {
                    val userId = currentUserId(call)
                    call.respond(businessService.getBusinessForUser(userId))
                }
                put {
                    val userId = currentUserId(call)
                    val request = call.receive<UpdateBusinessRequest>()
                    call.respond(businessService.upsertBusiness(userId, request))
                }
                post("/image") {
                    val multipart = call.receiveMultipart()
                    var bytes: ByteArray? = null
                    var originalName: String? = null
                    var contentType: String? = null
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                if (part.name == "image" || bytes == null) {
                                    bytes = part.provider().readRemaining().readByteArray()
                                    originalName = part.originalFileName
                                    contentType = part.contentType?.toString()
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }
                    val imageBytes = bytes
                        ?: throw BadRequestException("Missing image file. Use multipart field name \"image\".")
                    call.respond(
                        HttpStatusCode.Created,
                        businessService.uploadBusinessImage(imageBytes, originalName, contentType)
                    )
                }
            }

            route("/api/products") {
                get {
                    call.respond(productService.getProducts())
                }
                get("/low-stock") {
                    call.respond(productService.getLowStockProducts())
                }
                get("/sku/{sku}") {
                    val sku = call.parameters["sku"]
                        ?: throw BadRequestException("SKU is required")
                    call.respond(productService.getProductBySku(sku))
                }
                post("/images") {
                    val multipart = call.receiveMultipart()
                    var uploadBytes: ByteArray? = null
                    var originalName: String? = null
                    var contentType: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                if (part.name == "image" || uploadBytes == null) {
                                    originalName = part.originalFileName
                                    contentType = part.contentType?.toString()
                                    uploadBytes = part.provider().readRemaining().readByteArray()
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    val bytes = uploadBytes
                        ?: throw BadRequestException("Missing image file. Use multipart field name \"image\".")
                    val response = productService.uploadProductImage(bytes, originalName, contentType)
                    call.respond(HttpStatusCode.Created, response)
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
                    val from = call.request.queryParameters["from"]
                    val to = call.request.queryParameters["to"]
                    call.respond(dashboardService.getReports(range, from, to))
                }
            }
        }

        // Public user-by-id lookup removed — use authenticated GET /api/users/me instead.
    }
}

private fun currentUserId(call: ApplicationCall): Int {
    val principal = call.principal<JWTPrincipal>()
        ?: throw BadRequestException("Authentication required")
    return principal.payload.getClaim("userId").asInt()
        ?: throw BadRequestException("Invalid authentication token")
}
