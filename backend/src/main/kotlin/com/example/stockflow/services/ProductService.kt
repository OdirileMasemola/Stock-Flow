package com.example.stockflow.services

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.ProductResponse
import com.example.stockflow.models.UpdateProductRequest
import com.example.stockflow.repositories.ProductRepository
import com.example.stockflow.repositories.ProductRepositoryImpl

class ProductService(
    private val repository: ProductRepository = ProductRepositoryImpl()
) {
    suspend fun getProducts(): List<ProductResponse> = repository.getAllProducts()

    suspend fun getProduct(id: Int): ProductResponse {
        return repository.getProductById(id)
            ?: throw NotFoundException("Product not found")
    }

    suspend fun createProduct(request: CreateProductRequest): ProductResponse {
        validateProductFields(
            name = request.name,
            sku = request.sku,
            costPrice = request.costPrice,
            sellingPrice = request.sellingPrice,
            stockLevel = request.stockLevel,
            minStockLevel = request.minStockLevel,
            categoryId = request.categoryId,
            supplierId = request.supplierId
        )

        val normalizedSku = request.sku?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedSku != null && repository.findBySku(normalizedSku) != null) {
            throw ConflictException("A product with this SKU already exists")
        }

        return repository.createProduct(request)
    }

    suspend fun updateProduct(id: Int, request: UpdateProductRequest): ProductResponse {
        // Ensure the product exists before validating other fields
        repository.getProductById(id)
            ?: throw NotFoundException("Product not found")

        validateProductFields(
            name = request.name,
            sku = request.sku,
            costPrice = request.costPrice,
            sellingPrice = request.sellingPrice,
            stockLevel = request.stockLevel,
            minStockLevel = request.minStockLevel,
            categoryId = request.categoryId,
            supplierId = request.supplierId
        )

        val normalizedSku = request.sku?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedSku != null) {
            val existingWithSku = repository.findBySku(normalizedSku)
            // Allow keeping the same SKU on the same product; block other products.
            if (existingWithSku != null && existingWithSku.id != id) {
                throw ConflictException("A product with this SKU already exists")
            }
        }

        return repository.updateProduct(id, request)
            ?: throw NotFoundException("Product not found")
    }

    suspend fun deleteProduct(id: Int) {
        val deleted = repository.deleteProduct(id)
        if (!deleted) {
            throw NotFoundException("Product not found")
        }
    }

    private suspend fun validateProductFields(
        name: String,
        sku: String?,
        costPrice: Double,
        sellingPrice: Double,
        stockLevel: Int,
        minStockLevel: Int,
        categoryId: Int,
        supplierId: Int?
    ) {
        if (name.isBlank()) {
            throw BadRequestException("Product name cannot be blank")
        }
        if (name.trim().length > 100) {
            throw BadRequestException("Product name must be 100 characters or fewer")
        }

        val trimmedSku = sku?.trim()
        if (trimmedSku != null && trimmedSku.isNotEmpty()) {
            if (trimmedSku.length > 50) {
                throw BadRequestException("SKU must be 50 characters or fewer")
            }
            // Keep SKUs simple: letters, digits, dash, underscore
            if (!trimmedSku.matches(Regex("^[A-Za-z0-9_-]+$"))) {
                throw BadRequestException("SKU may only contain letters, numbers, dashes, and underscores")
            }
        }

        if (costPrice < 0) {
            throw BadRequestException("Cost price cannot be negative")
        }
        if (sellingPrice < 0) {
            throw BadRequestException("Selling price cannot be negative")
        }
        if (stockLevel < 0) {
            throw BadRequestException("Stock level cannot be negative")
        }
        if (minStockLevel < 0) {
            throw BadRequestException("Minimum stock level cannot be negative")
        }

        if (!repository.categoryExists(categoryId)) {
            throw BadRequestException("Category does not exist")
        }

        if (supplierId != null && !repository.supplierExists(supplierId)) {
            throw BadRequestException("Supplier does not exist")
        }
    }
}
