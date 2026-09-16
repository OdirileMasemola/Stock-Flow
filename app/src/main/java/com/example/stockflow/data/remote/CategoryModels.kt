package com.example.stockflow.data.remote

/**
 * Category returned by the StockFlow API.
 */
data class CategoryDto(
    val id: Int,
    val name: String,
    val description: String? = null
)

data class CreateCategoryRequest(
    val name: String,
    val description: String? = null
)
