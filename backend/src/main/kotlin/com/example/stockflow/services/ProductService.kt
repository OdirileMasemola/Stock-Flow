package com.example.stockflow.services

import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.ProductImageUploadResponse
import com.example.stockflow.models.ProductResponse
import com.example.stockflow.models.UpdateProductRequest
import com.example.stockflow.models.LowStockCrossing
import com.example.stockflow.repositories.ProductRepository
import com.example.stockflow.services.notifications.LowStockAlertService
import com.example.stockflow.services.activity.ActivityService
import com.example.stockflow.repositories.ProductRepositoryImpl
import com.example.stockflow.services.storage.ImageFolder

class ProductService(
    private val repository: ProductRepository = ProductRepositoryImpl(),
    private val imageStorage: ProductImageStorage = ProductImageStorage(),
    private val lowStockAlerts: LowStockAlertService = LowStockAlertService(),
    private val activityService: ActivityService = ActivityService()
) {
    suspend fun getProducts(ownerUserId: Int): List<ProductResponse> = repository.getAllProducts(ownerUserId)

    suspend fun getLowStockProducts(ownerUserId: Int): List<ProductResponse> =
        repository.getLowStockProducts(ownerUserId)

    suspend fun getProduct(id: Int, ownerUserId: Int): ProductResponse {
        return repository.getProductById(id, ownerUserId)
            ?: throw NotFoundException("Product not found")
    }

    suspend fun getProductBySku(sku: String, ownerUserId: Int): ProductResponse {
        val normalized = sku.trim()
        if (normalized.isEmpty()) {
            throw BadRequestException("SKU cannot be blank")
        }
        if (normalized.length > 50) {
            throw BadRequestException("SKU must be 50 characters or fewer")
        }
        return repository.findBySku(normalized, ownerUserId)
            ?: throw NotFoundException("Product not found")
    }

    fun uploadProductImage(
        bytes: ByteArray,
        originalFileName: String?,
        contentType: String?
    ): ProductImageUploadResponse {
        val imageUrl = imageStorage.saveProductImage(bytes, originalFileName, contentType)
        return ProductImageUploadResponse(imageUrl = imageUrl)
    }

    fun uploadsRoot() = imageStorage.uploadsRoot()

    suspend fun createProduct(
        request: CreateProductRequest,
        ownerUserId: Int,
        actingUserId: Int = ownerUserId
    ): ProductResponse {
        validateProductFields(
            name = request.name,
            sku = request.sku,
            costPrice = request.costPrice,
            sellingPrice = request.sellingPrice,
            stockLevel = request.stockLevel,
            minStockLevel = request.minStockLevel,
            categoryId = request.categoryId,
            supplierId = request.supplierId,
            imageUrl = request.imageUrl,
            ownerUserId = ownerUserId
        )

        val normalizedSku = request.sku?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedSku != null && repository.findBySku(normalizedSku, ownerUserId) != null) {
            throw ConflictException("A product with this SKU already exists")
        }

        val created = repository.createProduct(request, ownerUserId)
        activityService.recordProductCreated(
            userId = actingUserId,
            productId = created.id,
            productName = created.name
        )
        // Treat create-as-low as a crossing (previous stock conceptually above min).
        if (created.stockLevel <= created.minStockLevel) {
            lowStockAlerts.notifyCrossingsAsync(
                actingUserId = actingUserId,
                shopOwnerUserId = ownerUserId,
                crossings = listOf(
                    LowStockCrossing(
                        productId = created.id,
                        productName = created.name,
                        previousStock = created.minStockLevel + 1,
                        currentStock = created.stockLevel,
                        minStockLevel = created.minStockLevel
                    )
                )
            )
        }
        return created
    }

    suspend fun updateProduct(
        id: Int,
        request: UpdateProductRequest,
        ownerUserId: Int,
        actingUserId: Int = ownerUserId
    ): ProductResponse {
        // Ensure the product exists in this shop before validating other fields
        val existing = repository.getProductById(id, ownerUserId)
            ?: throw NotFoundException("Product not found")

        validateProductFields(
            name = request.name,
            sku = request.sku,
            costPrice = request.costPrice,
            sellingPrice = request.sellingPrice,
            stockLevel = request.stockLevel,
            minStockLevel = request.minStockLevel,
            categoryId = request.categoryId,
            supplierId = request.supplierId,
            imageUrl = request.imageUrl,
            ownerUserId = ownerUserId
        )

        val normalizedSku = request.sku?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedSku != null) {
            val existingWithSku = repository.findBySku(normalizedSku, ownerUserId)
            // Allow keeping the same SKU on the same product; block other products.
            if (existingWithSku != null && existingWithSku.id != id) {
                throw ConflictException("A product with this SKU already exists")
            }
        }

        val updated = repository.updateProduct(id, request, ownerUserId)
            ?: throw NotFoundException("Product not found")

        val oldUrl = existing.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val newUrl = updated.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (oldUrl != null && oldUrl != newUrl) {
            deleteProductImageIfUnused(oldUrl)
        }

        activityService.recordProductUpdated(
            userId = actingUserId,
            productId = updated.id,
            productName = updated.name
        )

        lowStockAlerts.notifyCrossingsAsync(
            actingUserId = actingUserId,
            shopOwnerUserId = ownerUserId,
            crossings = listOf(
                LowStockCrossing(
                    productId = updated.id,
                    productName = updated.name,
                    previousStock = existing.stockLevel,
                    currentStock = updated.stockLevel,
                    minStockLevel = updated.minStockLevel
                )
            )
        )

        return updated
    }

    suspend fun deleteProduct(id: Int, ownerUserId: Int, actingUserId: Int = ownerUserId) {
        val existing = repository.getProductById(id, ownerUserId)
            ?: throw NotFoundException("Product not found")
        val deleted = repository.deleteProduct(id, ownerUserId)
        if (!deleted) {
            throw NotFoundException("Product not found")
        }
        deleteProductImageIfUnused(existing.imageUrl)
        activityService.recordProductDeleted(
            userId = actingUserId,
            productId = existing.id,
            productName = existing.name
        )
    }

    /**
     * Image URLs come from the client, so a product may point at a file another shop uploaded.
     * Only product-folder files that no product row still references are removed.
     */
    private suspend fun deleteProductImageIfUnused(imageUrl: String?) {
        val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (repository.isImageInUse(url)) return
        imageStorage.deleteOwned(url, ImageFolder.PRODUCTS)
    }

    private suspend fun validateProductFields(
        name: String,
        sku: String?,
        costPrice: Double,
        sellingPrice: Double,
        stockLevel: Int,
        minStockLevel: Int,
        categoryId: Int,
        supplierId: Int?,
        imageUrl: String?,
        ownerUserId: Int
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

        val trimmedImage = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedImage != null && trimmedImage.length > AppConfig.IMAGE_URL_MAX_LENGTH) {
            throw BadRequestException("Image URL must be ${AppConfig.IMAGE_URL_MAX_LENGTH} characters or fewer")
        }

        if (!repository.categoryExists(categoryId)) {
            throw BadRequestException("Category does not exist")
        }

        if (supplierId != null && !repository.supplierExists(supplierId, ownerUserId)) {
            throw BadRequestException("Supplier does not exist")
        }
    }
}
