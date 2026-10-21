package com.odirilemasemola.stockflow.data.remote

/** Employee DTOs — field names match the Ktor JSON (camelCase). */
data class EmployeeDto(
    val id: Int,
    val fullName: String,
    val username: String,
    val email: String,
    val role: String,
    val profileImageUrl: String? = null
)

data class AddEmployeeRequest(
    val email: String,
    val role: String
)

data class UpdateEmployeeRoleRequest(
    val role: String
)

data class ShopMembershipDto(
    val userId: Int,
    val ownerUserId: Int,
    val role: String,
    val canManageEmployees: Boolean
)

/** Roles an owner can assign; values match the server's role names. */
object EmployeeRoles {
    const val STAFF = "Staff"
    const val SUPPLIER = "Supplier"
    val ASSIGNABLE = listOf(STAFF, SUPPLIER)
}
