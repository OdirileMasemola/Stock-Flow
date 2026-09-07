package com.example.stockflow.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val fullName: String,
    val password: String,
    val roleId: Int
)

@Serializable
data class RegisterResponse(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val roleId: Int
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: User
)

class BadRequestException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)
