package com.example.stockflow.data.remote

/**
 * Supplier returned by the StockFlow API.
 */
data class SupplierDto(
    val id: Int,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)

data class CreateSupplierRequest(
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)

data class UpdateSupplierRequest(
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null
)
