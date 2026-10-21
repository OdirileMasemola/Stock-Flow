package com.example.stockflow.plugins

import com.example.stockflow.models.AddEmployeeRequest
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.ShopMembershipResponse
import com.example.stockflow.models.UpdateEmployeeRoleRequest
import com.example.stockflow.services.EmployeeService
import com.example.stockflow.services.ShopAccessService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * Shop membership + owner-only employee management. Must be mounted inside authenticate("auth-jwt").
 * The shop is always resolved from the JWT user on the server.
 */
fun Route.shopRoutes(shopAccess: ShopAccessService, employees: EmployeeService) {
    route("/api/shops/me") {
        get {
            val shop = shopAccess.requireShopMember(jwtUserId(call))
            call.respond(
                ShopMembershipResponse(
                    userId = shop.userId,
                    ownerUserId = shop.ownerUserId,
                    role = shop.role,
                    canManageEmployees = shop.isOwner
                )
            )
        }
        route("/employees") {
            get {
                val owner = shopAccess.requireShopOwner(jwtUserId(call))
                call.respond(employees.list(owner))
            }
            post {
                val owner = shopAccess.requireShopOwner(jwtUserId(call))
                val request = try {
                    call.receive<AddEmployeeRequest>()
                } catch (_: Exception) {
                    throw BadRequestException("Email is required")
                }
                call.respond(HttpStatusCode.Created, employees.add(owner, request))
            }
            put("/{id}") {
                val owner = shopAccess.requireShopOwner(jwtUserId(call))
                val id = call.parameters["id"]?.toIntOrNull() ?: throw BadRequestException("Invalid employee ID")
                val request = try {
                    call.receive<UpdateEmployeeRoleRequest>()
                } catch (_: Exception) {
                    throw BadRequestException("Role is required")
                }
                call.respond(employees.updateRole(owner, id, request.role))
            }
            delete("/{id}") {
                val owner = shopAccess.requireShopOwner(jwtUserId(call))
                val id = call.parameters["id"]?.toIntOrNull() ?: throw BadRequestException("Invalid employee ID")
                employees.remove(owner, id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun jwtUserId(call: ApplicationCall): Int {
    val principal = call.principal<JWTPrincipal>() ?: throw BadRequestException("Authentication required")
    return principal.payload.getClaim("userId").asInt() ?: throw BadRequestException("Invalid authentication token")
}
