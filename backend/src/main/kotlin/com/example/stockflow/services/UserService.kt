package com.example.stockflow.services

import com.example.stockflow.models.*
import com.example.stockflow.repositories.UserRepository
import com.example.stockflow.repositories.UserRepositoryImpl
import com.example.stockflow.config.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class UserService(private val repository: UserRepository = UserRepositoryImpl()) {
    suspend fun getUser(id: Int): User? {
        return repository.findUserById(id)
    }

    suspend fun authenticateUser(request: LoginRequest): LoginResponse {
        if (request.identifier.isBlank() || request.password.isBlank()) {
            throw BadRequestException("Identifier and password are required")
        }

        val userPair = repository.findByIdentifier(request.identifier)
            ?: throw UnauthorizedException("Invalid username/email or password")

        val (user, passwordHash) = userPair

        if (!BCrypt.checkpw(request.password, passwordHash)) {
            throw UnauthorizedException("Invalid username/email or password")
        }

        val token = generateToken(user)

        return LoginResponse(token, user)
    }

    private fun generateToken(user: User): String {
        return JWT.create()
            .withAudience(AppConfig.jwtAudience)
            .withIssuer(AppConfig.jwtIssuer)
            .withClaim("username", user.username)
            .withClaim("userId", user.id)
            .withClaim("roleId", user.roleId)
            .withExpiresAt(Date(System.currentTimeMillis() + AppConfig.jwtExpiration))
            .sign(Algorithm.HMAC256(AppConfig.jwtSecret))
    }

    suspend fun registerUser(request: RegisterRequest): RegisterResponse {
        validateRegistrationRequest(request)

        if (repository.findByUsername(request.username) != null) {
            throw ConflictException("Username is already taken")
        }

        if (repository.findByEmail(request.email) != null) {
            throw ConflictException("Email is already registered")
        }

        if (!repository.roleExists(request.roleId)) {
            throw BadRequestException("Invalid role ID")
        }

        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())
        val user = repository.createUser(request, passwordHash)

        return RegisterResponse(
            id = user.id!!,
            username = user.username,
            email = user.email,
            fullName = user.fullName,
            roleId = user.roleId!!
        )
    }

    private fun validateRegistrationRequest(request: RegisterRequest) {
        if (request.username.isBlank()) throw BadRequestException("Username cannot be blank")
        if (request.fullName.isBlank()) throw BadRequestException("Full name cannot be blank")
        if (request.email.isBlank()) throw BadRequestException("Email cannot be blank")
        if (!isValidEmail(request.email)) throw BadRequestException("Invalid email format")
        if (request.password.length < 8) throw BadRequestException("Password must be at least 8 characters")
    }

    private fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
}
