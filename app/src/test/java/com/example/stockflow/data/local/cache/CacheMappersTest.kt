package com.example.stockflow.data.local.cache

import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.PurchaseOrderDto
import com.example.stockflow.data.remote.PurchaseOrderItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheMappersTest {

    @Test
    fun productRoundTripPreservesFieldsIncludingImageUrlString() {
        val dto = ProductDto(
            id = 7,
            name = "Milk",
            sku = "SKU-1",
            costPrice = 5.0,
            sellingPrice = 8.5,
            stockLevel = 12,
            minStockLevel = 3,
            categoryId = 2,
            categoryName = "Dairy",
            supplierId = 9,
            imageUrl = "https://cdn.example/p.jpg"
        )
        val entity = dto.toCachedEntity(userId = 42, cachedAt = 1_700_000_000_000L)
        assertEquals(42, entity.userId)
        assertEquals(1_700_000_000_000L, entity.cachedAt)
        assertEquals("https://cdn.example/p.jpg", entity.imageUrl)
        assertEquals(dto, entity.toDto())
    }

    @Test
    fun purchaseOrderRoundTripIncludesItems() {
        val item = PurchaseOrderItemDto(1, 10, "Sugar", 2, 4.0, 8.0)
        val dto = PurchaseOrderDto(
            id = 3,
            supplierId = 5,
            supplierName = "ABC",
            totalAmount = 8.0,
            status = "PENDING",
            expectedDeliveryDate = null,
            createdAt = "2026-01-01T00:00:00Z",
            items = listOf(item)
        )
        val orderEntity = dto.toCachedOrderEntity(1, 99L)
        val itemEntity = item.toCachedEntity(1, dto.id, 99L)
        val restored = orderEntity.toDto(listOf(itemEntity))
        assertEquals(dto, restored)
        assertNull(restored.expectedDeliveryDate)
    }
}
