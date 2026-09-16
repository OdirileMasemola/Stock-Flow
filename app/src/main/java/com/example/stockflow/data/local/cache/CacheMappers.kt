package com.example.stockflow.data.local.cache

import com.example.stockflow.data.remote.BusinessDto
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
