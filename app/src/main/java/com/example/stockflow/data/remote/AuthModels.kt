package com.example.stockflow.data.remote

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: AuthUser
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val fullName: String,
    val password: String,
    val roleId: Int
)

data class RegisterResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val roleId: Int
)

data class AuthUser(
    val id: Int?,
    val username: String,
    val email: String,
    val fullName: String,
    val roleId: Int?
)

data class ApiErrorResponse(
    val error: String?,
    val code: String? = null
)

data class RoleDto(
    val id: Int,
    val name: String,
    val description: String? = null
) {
    override fun toString(): String = name
}

data class GoogleAuthRequest(
    val idToken: String,
    val roleId: Int? = null
)
