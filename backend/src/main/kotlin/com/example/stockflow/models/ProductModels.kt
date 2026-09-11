package com.example.stockflow.models

import kotlinx.serialization.Serializable

/**
 * Product returned to API clients.
 * Includes optional categoryName when a join to categories succeeds.
 */
@Serializable
data class ProductResponse(
    val id: Int,
    val name: String,
    val sku: String? = null,
    val costPrice: Double,
    val sellingPrice: Double,
    val stockLevel: Int,
    val minStockLevel: Int,
    val categoryId: Int,
    val categoryName: String? = null,
    val supplierId: Int? = null,
    val imageUrl: String? = null
)

/** Body for creating a new product. */
@Serializable
data class CreateProductRequest(
    val name: String,
    val sku: String? = null,
    val costPrice: Double,
    val sellingPrice: Double,
    val stockLevel: Int = 0,
    val minStockLevel: Int = 5,
    val categoryId: Int,
    val supplierId: Int? = null,
    val imageUrl: String? = null
)

/** Body for updating an existing product (full replace of editable fields). */
@Serializable
data class UpdateProductRequest(
    val name: String,
    val sku: String? = null,
    val costPrice: Double,
    val sellingPrice: Double,
    val stockLevel: Int,
    val minStockLevel: Int,
    val categoryId: Int,
    val supplierId: Int? = null,
    val imageUrl: String? = null
)

/** Response after uploading a product image file. */
@Serializable
data class ProductImageUploadResponse(
    val imageUrl: String
)

/** Thrown when a requested resource does not exist (maps to HTTP 404). */
class NotFoundException(message: String) : RuntimeException(message)
