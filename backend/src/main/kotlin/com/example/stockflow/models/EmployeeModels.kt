package com.example.stockflow.models

import kotlinx.serialization.Serializable

/** A person who works for the caller's shop. Never includes password hashes or tokens. */
@Serializable
data class EmployeeResponse(
    val id: Int,
    val fullName: String,
    val username: String,
    val email: String,
    val role: String,
    val profileImageUrl: String? = null
)

/** Owner adds an existing StockFlow account (registered as Staff or Supplier) by email. */
@Serializable
data class AddEmployeeRequest(
    val email: String,
    val role: String = "Staff"
)

@Serializable
data class UpdateEmployeeRoleRequest(
    val role: String
)

/** GET /api/shops/me: what the signed-in account can do in its shop. */
@Serializable
data class ShopMembershipResponse(
    val userId: Int,
    val ownerUserId: Int,
    val role: String,
    val canManageEmployees: Boolean
)
