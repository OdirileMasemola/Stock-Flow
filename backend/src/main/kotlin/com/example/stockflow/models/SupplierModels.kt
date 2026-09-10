package com.example.stockflow.models

import kotlinx.serialization.Serializable

/** Supplier returned to API clients. */
@Serializable
data class SupplierResponse(
    val id: Int,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)

/** Body for creating a new supplier. */
@Serializable
data class CreateSupplierRequest(
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)

/** Body for updating an existing supplier. */
@Serializable
data class UpdateSupplierRequest(
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)
