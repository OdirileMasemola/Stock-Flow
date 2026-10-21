package com.example.stockflow.services

import com.example.stockflow.models.AddEmployeeRequest
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.EmployeeResponse
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.repositories.EmployeeStore
import com.example.stockflow.repositories.EmployeeStoreImpl

/**
 * Owner-managed staff list. Every method takes the owner's [ShopContext] from
 * [ShopAccessService.requireShopOwner]; the shop id is never read from the request.
 *
 * Staff are existing StockFlow accounts registered as Staff or Supplier. Linking an account moves
 * its shop context to the owner's shop, so accounts that already hold their own shop records are
 * refused rather than having those records hidden.
 */
class EmployeeService(
    private val store: EmployeeStore = EmployeeStoreImpl()
) {
    companion object {
        /** Roles an owner can assign. Owner is never assignable through this API. */
        val ASSIGNABLE_ROLES = listOf("Staff", "Supplier")
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }

    suspend fun list(owner: ShopContext): List<EmployeeResponse> =
        store.listForShop(owner.ownerUserId).map { it.toResponse() }

    suspend fun add(owner: ShopContext, request: AddEmployeeRequest): EmployeeResponse {
        val email = request.email.trim()
        if (email.isEmpty() || email.length > 100 || !EMAIL_PATTERN.matches(email)) {
            throw BadRequestException("Enter a valid email address")
        }
        val role = normalizeRole(request.role)
        val account = store.findByEmail(email)
        if (account == null || account.closed) {
            throw NotFoundException("No StockFlow account uses that email. Ask them to sign up as Staff first.")
        }
        if (account.id == owner.ownerUserId) {
            throw BadRequestException("You are already the owner of this shop")
        }
        if (account.shopOwnerUserId == owner.ownerUserId) {
            throw ConflictException("This person is already on your team")
        }
        if (account.shopOwnerUserId != null) {
            throw ConflictException("This account already works for another shop")
        }
        if (ASSIGNABLE_ROLES.none { it.equals(account.role, ignoreCase = true) }) {
            throw BadRequestException("Only accounts registered as Staff or Supplier can be added")
        }
        if (store.hasOwnShopRecords(account.id)) {
            throw ConflictException("This account already has its own shop records and cannot join another shop")
        }
        val roleId = store.roleIdByName(role) ?: throw BadRequestException("Unknown role")
        if (!store.link(account.id, owner.ownerUserId, roleId)) {
            throw ConflictException("This account already works for another shop")
        }
        return store.findById(account.id)!!.toResponse()
    }

    suspend fun updateRole(owner: ShopContext, employeeId: Int, rawRole: String): EmployeeResponse {
        val role = normalizeRole(rawRole)
        val account = requireEmployee(owner, employeeId)
        val roleId = store.roleIdByName(role) ?: throw BadRequestException("Unknown role")
        if (!store.updateRole(account.id, owner.ownerUserId, roleId)) {
            throw NotFoundException("Employee not found")
        }
        return store.findById(account.id)!!.toResponse()
    }

    suspend fun remove(owner: ShopContext, employeeId: Int) {
        requireEmployee(owner, employeeId)
        if (!store.unlink(employeeId, owner.ownerUserId)) {
            throw NotFoundException("Employee not found")
        }
    }

    private suspend fun requireEmployee(owner: ShopContext, employeeId: Int) =
        store.findById(employeeId)
            ?.takeIf { it.shopOwnerUserId == owner.ownerUserId && !it.closed }
            ?: throw NotFoundException("Employee not found")

    private fun normalizeRole(raw: String): String =
        ASSIGNABLE_ROLES.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
            ?: throw BadRequestException("Role must be Staff or Supplier")
}
