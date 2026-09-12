package com.example.stockflow.data.remote

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class LoginResponse(
    val token: String? = null,
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
    val id: Int? = null,
    val username: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val roleId: Int? = null
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
