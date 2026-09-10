package com.example.stockflow.services

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.CreateSupplierRequest
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.SupplierResponse
import com.example.stockflow.models.UpdateSupplierRequest
import com.example.stockflow.repositories.SupplierRepository
import com.example.stockflow.repositories.SupplierRepositoryImpl

class SupplierService(
    private val repository: SupplierRepository = SupplierRepositoryImpl()
) {
    suspend fun getSuppliers(): List<SupplierResponse> = repository.getAllSuppliers()

    suspend fun getSupplier(id: Int): SupplierResponse {
        return repository.getSupplierById(id)
            ?: throw NotFoundException("Supplier not found")
    }

    suspend fun createSupplier(request: CreateSupplierRequest): SupplierResponse {
        validateSupplierFields(
            name = request.name,
            contactName = request.contactName,
            phone = request.phone,
            email = request.email,
            address = request.address
        )

        val existing = repository.findByName(request.name.trim())
        if (existing != null) {
            throw ConflictException("A supplier with this name already exists")
        }

        return repository.createSupplier(request)
    }

    suspend fun updateSupplier(id: Int, request: UpdateSupplierRequest): SupplierResponse {
        repository.getSupplierById(id)
            ?: throw NotFoundException("Supplier not found")

        validateSupplierFields(
            name = request.name,
            contactName = request.contactName,
            phone = request.phone,
            email = request.email,
            address = request.address
        )

        val existingWithName = repository.findByName(request.name.trim())
        if (existingWithName != null && existingWithName.id != id) {
            throw ConflictException("A supplier with this name already exists")
        }

        return repository.updateSupplier(id, request)
            ?: throw NotFoundException("Supplier not found")
    }

    suspend fun deleteSupplier(id: Int) {
        repository.getSupplierById(id)
            ?: throw NotFoundException("Supplier not found")

        if (repository.isReferencedByProducts(id)) {
            throw ConflictException(
                "Cannot delete supplier: products still reference this supplier"
            )
        }
        if (repository.isReferencedByPurchaseOrders(id)) {
            throw ConflictException(
                "Cannot delete supplier: purchase orders still reference this supplier"
            )
        }

        val deleted = repository.deleteSupplier(id)
        if (!deleted) {
            throw NotFoundException("Supplier not found")
        }
    }

    private fun validateSupplierFields(
        name: String,
        contactName: String?,
        phone: String?,
        email: String?,
        address: String?
    ) {
        if (name.isBlank()) {
            throw BadRequestException("Supplier name cannot be blank")
        }
        if (name.trim().length > 100) {
            throw BadRequestException("Supplier name must be 100 characters or fewer")
        }

        contactName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length > 100) {
                throw BadRequestException("Contact name must be 100 characters or fewer")
            }
        }

        phone?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length > 20) {
                throw BadRequestException("Phone must be 20 characters or fewer")
            }
        }

        email?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length > 100) {
                throw BadRequestException("Email must be 100 characters or fewer")
            }
            if (!isValidEmail(it)) {
                throw BadRequestException("Invalid email format")
            }
        }

        address?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length > 255) {
                throw BadRequestException("Address must be 255 characters or fewer")
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.contains("@") &&
            trimmed.indexOf("@") > 0 &&
            trimmed.indexOf("@") < trimmed.lastIndex &&
            trimmed.contains(".")
    }
}
