package com.example.stockflow.data.local.cache

import com.example.stockflow.data.remote.BusinessDto
import com.example.stockflow.data.remote.DashboardLowStockItemDto
import com.example.stockflow.data.remote.DashboardPurchaseOrderItemDto
import com.example.stockflow.data.remote.DashboardSaleItemDto
import com.example.stockflow.data.remote.DashboardSummaryDto
import com.example.stockflow.data.remote.WeeklySalesDayDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.remote.ProfileDto
import com.example.stockflow.data.remote.PurchaseOrderDto
import com.example.stockflow.data.remote.PurchaseOrderItemDto
import com.example.stockflow.data.remote.SupplierDto

fun ProductDto.toCachedEntity(userId: Int, cachedAt: Long) = CachedProduct(
    userId = userId,
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
    imageUrl = imageUrl,
    cachedAt = cachedAt
)

fun CachedProduct.toDto() = ProductDto(
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

fun CategoryDto.toCachedEntity(userId: Int, cachedAt: Long) = CachedCategory(
    userId = userId,
    id = id,
    name = name,
    description = description,
    cachedAt = cachedAt
)

fun CachedCategory.toDto() = CategoryDto(
    id = id,
    name = name,
    description = description
)

fun SupplierDto.toCachedEntity(userId: Int, cachedAt: Long) = CachedSupplier(
    userId = userId,
    id = id,
    name = name,
    contactName = contactName,
    phone = phone,
    email = email,
    address = address,
    cachedAt = cachedAt
)

fun CachedSupplier.toDto() = SupplierDto(
    id = id,
    name = name,
    contactName = contactName,
    phone = phone,
    email = email,
    address = address
)

fun PurchaseOrderDto.toCachedOrderEntity(userId: Int, cachedAt: Long) = CachedPurchaseOrder(
    userId = userId,
    id = id,
    supplierId = supplierId,
    supplierName = supplierName,
    totalAmount = totalAmount,
    status = status,
    expectedDeliveryDate = expectedDeliveryDate,
    createdAt = createdAt,
    cachedAt = cachedAt
)

fun PurchaseOrderItemDto.toCachedEntity(userId: Int, purchaseOrderId: Int, cachedAt: Long) =
    CachedPurchaseOrderItem(
        userId = userId,
        id = id,
        purchaseOrderId = purchaseOrderId,
        productId = productId,
        productName = productName,
        quantity = quantity,
        unitCost = unitCost,
        subtotal = subtotal,
        cachedAt = cachedAt
    )

fun CachedPurchaseOrder.toDto(items: List<CachedPurchaseOrderItem>) = PurchaseOrderDto(
    id = id,
    supplierId = supplierId,
    supplierName = supplierName,
    totalAmount = totalAmount,
    status = status,
    expectedDeliveryDate = expectedDeliveryDate,
    createdAt = createdAt,
    items = items.map { it.toDto() }
)

fun CachedPurchaseOrderItem.toDto() = PurchaseOrderItemDto(
    id = id,
    productId = productId,
    productName = productName,
    quantity = quantity,
    unitCost = unitCost,
    subtotal = subtotal
)

fun ProfileDto.toCachedEntity(userId: Int, cachedAt: Long) = CachedProfile(
    userId = userId,
    id = id,
    username = username,
    email = email,
    fullName = fullName,
    roleId = roleId,
    profileImageUrl = profileImageUrl,
    cachedAt = cachedAt
)

fun CachedProfile.toDto() = ProfileDto(
    id = id,
    username = username,
    email = email,
    fullName = fullName,
    roleId = roleId,
    profileImageUrl = profileImageUrl
)

fun BusinessDto.toCachedEntity(userId: Int, cachedAt: Long) = CachedBusiness(
    userId = userId,
    id = id,
    storeName = storeName,
    ownerName = ownerName,
    phone = phone,
    email = email,
    address = address,
    imageUrl = imageUrl,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    updatedAt = updatedAt,
    cachedAt = cachedAt
)

fun CachedBusiness.toDto() = BusinessDto(
    id = id,
    userId = userId,
    storeName = storeName,
    ownerName = ownerName,
    phone = phone,
    email = email,
    address = address,
    imageUrl = imageUrl,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    updatedAt = updatedAt
)


private val dashboardGson = Gson()

fun DashboardSummaryDto.toCachedEntity(userId: Int, cachedAt: Long) = CachedDashboardSnapshot(
    userId = userId,
    totalProducts = totalProducts,
    totalStockQuantity = totalStockQuantity,
    inventoryValue = inventoryValue,
    todaySalesTotal = todaySalesTotal,
    todaySalesCount = todaySalesCount,
    lowStockCount = lowStockCount,
    weeklySalesJson = dashboardGson.toJson(weeklySales.orEmpty()),
    recentSalesJson = dashboardGson.toJson(recentSales.orEmpty()),
    recentPurchaseOrdersJson = dashboardGson.toJson(recentPurchaseOrders.orEmpty()),
    lowStockPreviewJson = dashboardGson.toJson(lowStockPreview.orEmpty()),
    cachedAt = cachedAt
)

fun CachedDashboardSnapshot.toDto(): DashboardSummaryDto {
    val weeklyType = object : TypeToken<List<WeeklySalesDayDto>>() {}.type
    val salesType = object : TypeToken<List<DashboardSaleItemDto>>() {}.type
    val poType = object : TypeToken<List<DashboardPurchaseOrderItemDto>>() {}.type
    val lowType = object : TypeToken<List<DashboardLowStockItemDto>>() {}.type
    return DashboardSummaryDto(
        totalProducts = totalProducts,
        totalStockQuantity = totalStockQuantity,
        inventoryValue = inventoryValue,
        todaySalesTotal = todaySalesTotal,
        todaySalesCount = todaySalesCount,
        lowStockCount = lowStockCount,
        weeklySales = dashboardGson.fromJson(weeklySalesJson, weeklyType) ?: emptyList(),
        recentSales = dashboardGson.fromJson(recentSalesJson, salesType) ?: emptyList(),
        recentPurchaseOrders = dashboardGson.fromJson(recentPurchaseOrdersJson, poType) ?: emptyList(),
        lowStockPreview = dashboardGson.fromJson(lowStockPreviewJson, lowType) ?: emptyList()
    )
}
