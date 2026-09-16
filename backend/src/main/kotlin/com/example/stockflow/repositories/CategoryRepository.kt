package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.Categories
import com.example.stockflow.models.CategoryResponse
import com.example.stockflow.models.CreateCategoryRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll

interface CategoryRepository {
    suspend fun getAllCategories(): List<CategoryResponse>
    suspend fun getCategoryById(id: Int): CategoryResponse?
    suspend fun findByNameIgnoreCase(name: String): CategoryResponse?
    suspend fun createCategory(request: CreateCategoryRequest): CategoryResponse
}

class CategoryRepositoryImpl : CategoryRepository {

    override suspend fun getAllCategories(): List<CategoryResponse> = dbQuery {
        Categories
            .selectAll()
            .orderBy(Categories.name to SortOrder.ASC)
            .map { toCategoryResponse(it) }
    }

    override suspend fun getCategoryById(id: Int): CategoryResponse? = dbQuery {
        Categories
            .selectAll()
            .where { Categories.id eq id }
            .map { toCategoryResponse(it) }
            .singleOrNull()
    }

    override suspend fun findByNameIgnoreCase(name: String): CategoryResponse? = dbQuery {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) {
            return@dbQuery null
        }
        Categories
            .selectAll()
            .where { Categories.name.lowerCase() eq normalized }
            .map { toCategoryResponse(it) }
            .singleOrNull()
    }

    override suspend fun createCategory(request: CreateCategoryRequest): CategoryResponse = dbQuery {
        val insertStatement = Categories.insert {
            it[name] = request.name.trim()
            it[description] = request.description?.trim()?.takeIf { value -> value.isNotEmpty() }
        }

        val newId = insertStatement.resultedValues?.first()?.get(Categories.id)
            ?: throw RuntimeException("Failed to create category")

        Categories
            .selectAll()
            .where { Categories.id eq newId }
            .map { toCategoryResponse(it) }
            .single()
    }

    private fun toCategoryResponse(row: ResultRow) = CategoryResponse(
        id = row[Categories.id],
        name = row[Categories.name],
        description = row[Categories.description]
    )
}
