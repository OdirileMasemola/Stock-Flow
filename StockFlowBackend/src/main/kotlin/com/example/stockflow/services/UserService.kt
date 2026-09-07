package com.example.stockflow.services

import com.example.stockflow.models.*
import com.example.stockflow.repositories.UserRepository
import com.example.stockflow.repositories.UserRepositoryImpl
import org.mindrot.jbcrypt.BCrypt

class UserService(private val repository: UserRepository = UserRepositoryImpl()) {
    suspend fun getUser(id: Int): User? {
        return repository.findUserById(id)
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
