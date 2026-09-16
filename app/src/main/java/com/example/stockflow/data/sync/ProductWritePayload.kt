package com.example.stockflow.data.sync

import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.UpdateProductRequest
import com.google.gson.Gson

/**
 * JSON payload stored in the write queue for product CREATE/UPDATE.
 * Contains no secrets — only product fields the API accepts.
 */
data class ProductWritePayload(
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
) {
    fun toCreateRequest() = CreateProductRequest(
        name = name,
        sku = sku,
        costPrice = costPrice,
        sellingPrice = sellingPrice,
        stockLevel = stockLevel,
        minStockLevel = minStockLevel,
        categoryId = categoryId,
        supplierId = supplierId,
        imageUrl = imageUrl
    )

    fun toUpdateRequest() = UpdateProductRequest(
        name = name,
        sku = sku,
        costPrice = costPrice,
        sellingPrice = sellingPrice,
        stockLevel = stockLevel,
        minStockLevel = minStockLevel,
        categoryId = categoryId,
        supplierId = supplierId,
        imageUrl = imageUrl
    )

    fun toProductDto(id: Int) = ProductDto(
        id = id,
        name = name,
        sku = sku,
        costPrice = costPrice,
        sellingPrice = sellingPrice,
        stockLevel = stockLevel,
        minStockLevel = minStockLevel,
        categoryId = categoryId,
        categoryName = categoryName,
        supplierId = supplierId,
        imageUrl = imageUrl
    )

    companion object {
        private val gson = Gson()

        fun fromCreate(request: CreateProductRequest, categoryName: String? = null) =
            ProductWritePayload(
                name = request.name,
                sku = request.sku,
                costPrice = request.costPrice,
                sellingPrice = request.sellingPrice,
                stockLevel = request.stockLevel,
                minStockLevel = request.minStockLevel,
                categoryId = request.categoryId,
                categoryName = categoryName,
                supplierId = request.supplierId,
                imageUrl = request.imageUrl
            )

        fun fromUpdate(request: UpdateProductRequest, categoryName: String? = null) =
            ProductWritePayload(
                name = request.name,
                sku = request.sku,
                costPrice = request.costPrice,
                sellingPrice = request.sellingPrice,
                stockLevel = request.stockLevel,
                minStockLevel = request.minStockLevel,
                categoryId = request.categoryId,
                categoryName = categoryName,
                supplierId = request.supplierId,
                imageUrl = request.imageUrl
            )

        fun fromDto(dto: ProductDto) = ProductWritePayload(
            name = dto.name,
            sku = dto.sku,
            costPrice = dto.costPrice,
            sellingPrice = dto.sellingPrice,
            stockLevel = dto.stockLevel,
            minStockLevel = dto.minStockLevel,
            categoryId = dto.categoryId,
            categoryName = dto.categoryName,
            supplierId = dto.supplierId,
            imageUrl = dto.imageUrl
        )

        fun toJson(payload: ProductWritePayload): String = gson.toJson(payload)

        fun fromJson(json: String): ProductWritePayload =
            gson.fromJson(json, ProductWritePayload::class.java)
    }
}
