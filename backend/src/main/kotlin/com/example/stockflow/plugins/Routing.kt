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
import com.example.stockflow.services.CategoryService
import com.example.stockflow.services.PurchaseOrderService
import com.example.stockflow.services.DashboardService
import com.example.stockflow.services.BusinessService
import com.example.stockflow.services.ShopAccessService
import com.example.stockflow.services.notifications.DeviceTokenService
import com.example.stockflow.services.activity.ActivityService
import com.example.stockflow.models.RegisterDeviceTokenRequest
import com.example.stockflow.models.UnregisterDeviceTokenRequest
import com.example.stockflow.models.RegisterRequest
import com.example.stockflow.models.LoginRequest
import com.example.stockflow.models.GoogleAuthRequest
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.UpdateProductRequest
import com.example.stockflow.models.CreateSaleRequest
import com.example.stockflow.models.CreateSupplierRequest
import com.example.stockflow.models.UpdateSupplierRequest
import com.example.stockflow.models.CreateCategoryRequest
import com.example.stockflow.models.CreatePurchaseOrderRequest
import com.example.stockflow.models.UpdatePurchaseOrderRequest
import com.example.stockflow.models.UpdateProfileRequest
import com.example.stockflow.models.UpdateBusinessRequest
import com.example.stockflow.models.DeleteAccountRequest
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.config.AppConfig
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureRouting() {
    val userService = UserService()
    val roleService = RoleService()
    val productService = ProductService()
    val saleService = SaleService()
    val supplierService = SupplierService()
    val categoryService = CategoryService()
    val purchaseOrderService = PurchaseOrderService()
    val dashboardService = DashboardService()
    val businessService = BusinessService()
    val deviceTokenService = DeviceTokenService()
    val activityService = ActivityService()
    val shopAccess = ShopAccessService()

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
        get("/account-deletion") {
            call.respondText(AccountDeletionPage.html, ContentType.Text.Html)
        }
        get("/privacy-policy") {
            call.respondText(PrivacyPolicyPage.html, ContentType.Text.Html)
        }

        // Local-disk images only. Cloud (Supabase) URLs are absolute and served by Supabase CDN.
        if (AppConfig.isLocalStorage) {
            staticFiles("/uploads", productService.uploadsRoot())
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
                delete {
                    val userId = currentUserId(call)
                    val request = try {
                        call.receive<DeleteAccountRequest>()
                    } catch (_: Exception) {
                        throw BadRequestException("Confirmation is required")
                    }
                    userService.deleteAccount(userId, request.confirmation)
                    call.respond(HttpStatusCode.NoContent)
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
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    call.respond(productService.getProducts(shop.ownerUserId))
                }
                get("/low-stock") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    call.respond(productService.getLowStockProducts(shop.ownerUserId))
                }
                get("/sku/{sku}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val sku = call.parameters["sku"]
                        ?: throw BadRequestException("SKU is required")
                    call.respond(productService.getProductBySku(sku, shop.ownerUserId))
                }
                post("/images") {
                    shopAccess.requireShopMember(currentUserId(call))
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
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid product ID")
                    call.respond(productService.getProduct(id, shop.ownerUserId))
                }
                post {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val request = call.receive<CreateProductRequest>()
                    val created = productService.createProduct(request, shop.ownerUserId, actingUserId = shop.userId)
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid product ID")
                    val request = call.receive<UpdateProductRequest>()
                    call.respond(
                        productService.updateProduct(id, request, shop.ownerUserId, actingUserId = shop.userId)
                    )
                }
                delete("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid product ID")
                    productService.deleteProduct(id, shop.ownerUserId, actingUserId = shop.userId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/api/sales") {
                get {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    call.respond(saleService.getSales(shop.ownerUserId))
                }
                get("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid sale ID")
                    call.respond(saleService.getSale(id, shop.ownerUserId))
                }
                post {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val request = call.receive<CreateSaleRequest>()
                    val created = saleService.createSale(shop.userId, shop.ownerUserId, request)
                    call.respond(HttpStatusCode.Created, created)
                }
            }

            route("/api/suppliers") {
                get {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    call.respond(supplierService.getSuppliers(shop.ownerUserId))
                }
                get("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid supplier ID")
                    call.respond(supplierService.getSupplier(id, shop.ownerUserId))
                }
                post {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val request = call.receive<CreateSupplierRequest>()
                    val created = supplierService.createSupplier(request, shop.ownerUserId)
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid supplier ID")
                    val request = call.receive<UpdateSupplierRequest>()
                    call.respond(supplierService.updateSupplier(id, request, shop.ownerUserId))
                }
                delete("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid supplier ID")
                    supplierService.deleteSupplier(id, shop.ownerUserId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/api/categories") {
                get {
                    call.respond(categoryService.getCategories())
                }
                post {
                    shopAccess.requireShopMember(currentUserId(call))
                    val request = call.receive<CreateCategoryRequest>()
                    val result = categoryService.findOrCreate(request)
                    val status = if (result.created) HttpStatusCode.Created else HttpStatusCode.OK
                    call.respond(status, result.category)
                }
            }

            route("/api/purchase-orders") {
                get {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    call.respond(purchaseOrderService.getPurchaseOrders(shop.ownerUserId))
                }
                get("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid purchase order ID")
                    call.respond(purchaseOrderService.getPurchaseOrder(id, shop.ownerUserId))
                }
                post {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val request = call.receive<CreatePurchaseOrderRequest>()
                    val created = purchaseOrderService.createPurchaseOrder(request, shop.ownerUserId)
                    call.respond(HttpStatusCode.Created, created)
                }
                put("/{id}") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid purchase order ID")
                    val request = call.receive<UpdatePurchaseOrderRequest>()
                    call.respond(purchaseOrderService.updatePurchaseOrder(id, request, shop.ownerUserId))
                }
                post("/{id}/receive") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: throw BadRequestException("Invalid purchase order ID")
                    call.respond(purchaseOrderService.receivePurchaseOrder(id, shop.ownerUserId))
                }
            }

            route("/api/notifications") {
                post("/device-token") {
                    val userId = currentUserId(call)
                    val request = call.receive<RegisterDeviceTokenRequest>()
                    val response = deviceTokenService.register(userId, request)
                    call.respond(HttpStatusCode.OK, response)
                }
                delete("/device-token") {
                    val userId = currentUserId(call)
                    val request = call.receive<UnregisterDeviceTokenRequest>()
                    deviceTokenService.unregister(userId, request)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/api/activity") {
                get {
                    val userId = currentUserId(call)
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                    call.respond(activityService.listRecentForUser(userId, limit))
                }
            }

            route("/api/dashboard") {
                get("/summary") {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    call.respond(dashboardService.getSummary(shop.ownerUserId))
                }
            }

            route("/api/reports") {
                get {
                    val shop = shopAccess.requireShopMember(currentUserId(call))
                    val range = call.request.queryParameters["range"]
                    val from = call.request.queryParameters["from"]
                    val to = call.request.queryParameters["to"]
                    call.respond(dashboardService.getReports(shop.ownerUserId, range, from, to))
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
