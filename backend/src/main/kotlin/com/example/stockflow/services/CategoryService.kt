package com.example.stockflow.services

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.CategoryResponse
import com.example.stockflow.models.CreateCategoryRequest
import com.example.stockflow.repositories.CategoryRepository
import com.example.stockflow.repositories.CategoryRepositoryImpl

/**
 * @param created true when a new row was inserted; false when an existing category was reused.
 */
data class CategoryUpsertResult(
    val category: CategoryResponse,
    val created: Boolean
)

class CategoryService(
    private val repository: CategoryRepository = CategoryRepositoryImpl()
) {
    suspend fun getCategories(): List<CategoryResponse> = repository.getAllCategories()

    /**
     * Returns an existing category with the same name (case-insensitive),
     * or creates one when none exists.
     */
    suspend fun findOrCreate(request: CreateCategoryRequest): CategoryUpsertResult {
        val name = request.name.trim()
        validateCategoryFields(name, request.description)

        val existing = repository.findByNameIgnoreCase(name)
        if (existing != null) {
            return CategoryUpsertResult(existing, created = false)
        }

        return try {
            val created = repository.createCategory(
                CreateCategoryRequest(name = name, description = request.description)
            )
            CategoryUpsertResult(created, created = true)
        } catch (_: Exception) {
            // Unique index race: another request inserted the same name concurrently.
            val raced = repository.findByNameIgnoreCase(name)
                ?: throw BadRequestException("Unable to create category")
            CategoryUpsertResult(raced, created = false)
        }
    }

    private fun validateCategoryFields(name: String, description: String?) {
        if (name.isBlank()) {
            throw BadRequestException("Category name cannot be blank")
        }
        if (name.length > 50) {
            throw BadRequestException("Category name must be 50 characters or fewer")
        }
        description?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length > 255) {
                throw BadRequestException("Category description must be 255 characters or fewer")
            }
        }
    }
}
