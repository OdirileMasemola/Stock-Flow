package com.example.stockflow.data.sync

import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.CreateCategoryRequest
import com.google.gson.Gson

/**
 * JSON payload stored in the write queue for category CREATE.
 * Contains no secrets — only category fields the API accepts.
 */
data class CategoryWritePayload(
    val name: String,
    val description: String? = null
) {
    fun toCreateRequest() = CreateCategoryRequest(
        name = name,
        description = description
    )

    fun toCategoryDto(id: Int) = CategoryDto(
        id = id,
        name = name,
        description = description
    )

    companion object {
        private val gson = Gson()

        fun fromCreate(request: CreateCategoryRequest) = CategoryWritePayload(
            name = request.name.trim(),
            description = request.description
        )

        fun toJson(payload: CategoryWritePayload): String = gson.toJson(payload)

        fun fromJson(json: String): CategoryWritePayload =
            gson.fromJson(json, CategoryWritePayload::class.java)
    }
}
