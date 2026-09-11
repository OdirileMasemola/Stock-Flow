package com.example.stockflow.data.remote

/**
 * Product returned by the StockFlow API.
 * Field names match the Ktor JSON (camelCase).
 */
data class ProductDto(
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

data class ProductImageUploadResponse(
    val imageUrl: String
)
