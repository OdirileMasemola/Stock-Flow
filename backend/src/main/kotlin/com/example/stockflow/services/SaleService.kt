package com.example.stockflow.services

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.CreateSaleRequest
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.SaleResponse
import com.example.stockflow.repositories.SaleLineInput
import com.example.stockflow.repositories.SaleRepository
import com.example.stockflow.repositories.SaleRepositoryImpl
import com.example.stockflow.services.notifications.LowStockAlertService

class SaleService(
    private val repository: SaleRepository = SaleRepositoryImpl(),
    private val lowStockAlerts: LowStockAlertService = LowStockAlertService()
) {
    companion object {
        /** Allowed payment methods for StockFlow POS (no gateway — record only). */
        val ALLOWED_PAYMENT_METHODS = setOf("Cash", "Card", "Other")
        private val CLIENT_SALE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{8,64}$")
    }

    suspend fun getSales(ownerUserId: Int): List<SaleResponse> = repository.getAllSales(ownerUserId)

    suspend fun getSale(id: Int, ownerUserId: Int): SaleResponse {
        return repository.getSaleById(id, ownerUserId)
            ?: throw NotFoundException("Sale not found")
    }

    suspend fun createSale(userId: Int, ownerUserId: Int, request: CreateSaleRequest): SaleResponse {
        if (userId <= 0) {
            throw BadRequestException("Invalid user")
        }

        val paymentMethod = normalizePaymentMethod(request.paymentMethod)
        if (request.items.isEmpty()) {
            throw BadRequestException("Cart cannot be empty")
        }

        // Merge duplicate product lines so stock checks use the total quantity.
        val merged = linkedMapOf<Int, Int>()
        for (item in request.items) {
            if (item.productId <= 0) {
                throw BadRequestException("Invalid product ID")
            }
            if (item.quantity <= 0) {
                throw BadRequestException("Quantity must be greater than zero")
            }
            merged[item.productId] = (merged[item.productId] ?: 0) + item.quantity
        }

        val lines = merged.map { (productId, quantity) ->
            SaleLineInput(productId = productId, quantity = quantity)
        }

        val clientSaleId = normalizeClientSaleId(request.clientSaleId)
        if (clientSaleId != null) {
            repository.findByClientSaleId(ownerUserId, clientSaleId)?.let { return it }
        }

        val result = repository.createSale(
            userId = userId,
            ownerUserId = ownerUserId,
            paymentMethod = paymentMethod,
            lines = lines,
            clientSaleId = clientSaleId
        )
        if (result.duplicate) {
            return result.sale
        }

        // Non-blocking FCM; never fails the sale.
        lowStockAlerts.notifyCrossingsAsync(userId, result.lowStockCrossings, shopOwnerUserId = ownerUserId)

        return result.sale
    }

    private fun normalizeClientSaleId(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.length > 64 || !CLIENT_SALE_ID_PATTERN.matches(trimmed)) {
            throw BadRequestException("Invalid client sale ID")
        }
        return trimmed
    }

    private fun normalizePaymentMethod(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw BadRequestException("Payment method is required")
        }
        val matched = ALLOWED_PAYMENT_METHODS.firstOrNull { it.equals(trimmed, ignoreCase = true) }
            ?: throw BadRequestException(
                "Invalid payment method. Allowed: ${ALLOWED_PAYMENT_METHODS.joinToString(", ")}"
            )
        return matched
    }
}
