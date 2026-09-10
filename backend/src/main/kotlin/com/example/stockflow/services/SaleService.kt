package com.example.stockflow.services

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.CreateSaleRequest
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.SaleResponse
import com.example.stockflow.repositories.SaleLineInput
import com.example.stockflow.repositories.SaleRepository
import com.example.stockflow.repositories.SaleRepositoryImpl

class SaleService(
    private val repository: SaleRepository = SaleRepositoryImpl()
) {
    companion object {
        /** Allowed payment methods for StockFlow POS (no gateway — record only). */
        val ALLOWED_PAYMENT_METHODS = setOf("Cash", "Card", "Other")
    }

    suspend fun getSales(): List<SaleResponse> = repository.getAllSales()

    suspend fun getSale(id: Int): SaleResponse {
        return repository.getSaleById(id)
            ?: throw NotFoundException("Sale not found")
    }

    suspend fun createSale(userId: Int, request: CreateSaleRequest): SaleResponse {
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

        return repository.createSale(
            userId = userId,
            paymentMethod = paymentMethod,
            lines = lines
        )
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
