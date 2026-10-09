package com.example.stockflow.services

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.CreatePurchaseOrderItemRequest
import com.example.stockflow.models.CreatePurchaseOrderRequest
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.PurchaseOrderResponse
import com.example.stockflow.models.UpdatePurchaseOrderRequest
import com.example.stockflow.repositories.PurchaseOrderLineInput
import com.example.stockflow.repositories.PurchaseOrderRepository
import com.example.stockflow.repositories.PurchaseOrderRepositoryImpl
import com.example.stockflow.repositories.parseOptionalDateTime
import java.math.BigDecimal
import java.math.RoundingMode

class PurchaseOrderService(
    private val repository: PurchaseOrderRepository = PurchaseOrderRepositoryImpl()
) {
    suspend fun getPurchaseOrders(ownerUserId: Int): List<PurchaseOrderResponse> =
        repository.getAllPurchaseOrders(ownerUserId)

    suspend fun getPurchaseOrder(id: Int, ownerUserId: Int): PurchaseOrderResponse {
        return repository.getPurchaseOrderById(id, ownerUserId)
            ?: throw NotFoundException("Purchase order not found")
    }

    suspend fun createPurchaseOrder(request: CreatePurchaseOrderRequest, ownerUserId: Int): PurchaseOrderResponse {
        validateSupplier(request.supplierId, ownerUserId)
        val lines = validateAndMergeItems(request.items)
        ensureProductsExist(lines, ownerUserId)
        val expected = parseOptionalDateTime(request.expectedDeliveryDate)
        return repository.createPurchaseOrder(ownerUserId, request.supplierId, expected, lines)
    }

    suspend fun updatePurchaseOrder(
        id: Int,
        request: UpdatePurchaseOrderRequest,
        ownerUserId: Int
    ): PurchaseOrderResponse {
        repository.getPurchaseOrderById(id, ownerUserId)
            ?: throw NotFoundException("Purchase order not found")

        validateSupplier(request.supplierId, ownerUserId)
        val lines = validateAndMergeItems(request.items)
        ensureProductsExist(lines, ownerUserId)
        val expected = parseOptionalDateTime(request.expectedDeliveryDate)
        return repository.updatePurchaseOrder(id, ownerUserId, request.supplierId, expected, lines)
    }

    suspend fun receivePurchaseOrder(id: Int, ownerUserId: Int): PurchaseOrderResponse {
        return repository.receivePurchaseOrder(id, ownerUserId)
    }

    private suspend fun validateSupplier(supplierId: Int, ownerUserId: Int) {
        if (supplierId <= 0) {
            throw BadRequestException("Invalid supplier ID")
        }
        if (!repository.supplierExists(supplierId, ownerUserId)) {
            throw NotFoundException("Supplier not found")
        }
    }

    private fun validateAndMergeItems(
        items: List<CreatePurchaseOrderItemRequest>
    ): List<PurchaseOrderLineInput> {
        if (items.isEmpty()) {
            throw BadRequestException("Purchase order must contain at least one item")
        }

        // Merge duplicate product lines; last unitCost wins when combining.
        val merged = linkedMapOf<Int, Pair<Int, Double>>()
        for (item in items) {
            if (item.productId <= 0) {
                throw BadRequestException("Invalid product ID")
            }
            if (item.quantity <= 0) {
                throw BadRequestException("Quantity must be greater than zero")
            }
            if (item.unitCost < 0) {
                throw BadRequestException("Unit cost cannot be negative")
            }

            val existing = merged[item.productId]
            if (existing == null) {
                merged[item.productId] = item.quantity to item.unitCost
            } else {
                merged[item.productId] = (existing.first + item.quantity) to item.unitCost
            }
        }

        return merged.map { (productId, pair) ->
            PurchaseOrderLineInput(
                productId = productId,
                quantity = pair.first,
                unitCost = BigDecimal.valueOf(pair.second).setScale(2, RoundingMode.HALF_UP)
            )
        }
    }

    private suspend fun ensureProductsExist(lines: List<PurchaseOrderLineInput>, ownerUserId: Int) {
        for (line in lines) {
            if (!repository.productExists(line.productId, ownerUserId)) {
                throw NotFoundException("Product not found: id=${line.productId}")
            }
        }
    }
}
