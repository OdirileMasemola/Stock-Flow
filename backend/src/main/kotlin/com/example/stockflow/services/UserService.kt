package com.example.stockflow.services

import com.example.stockflow.models.*
import com.example.stockflow.repositories.UserRepository
import com.example.stockflow.repositories.UserRepositoryImpl
import com.example.stockflow.config.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class UserService(
    private val repository: UserRepository = UserRepositoryImpl(),
    private val firebaseTokenVerifier: FirebaseTokenVerifier = FirebaseTokenVerifier(),
    private val imageStorage: ProductImageStorage = ProductImageStorage()
) {
    suspend fun getUser(id: Int): User? {
        return repository.findUserById(id)
    }

    suspend fun getProfile(userId: Int): ProfileResponse {
        val user = repository.findUserById(userId)
            ?: throw NotFoundException("User not found")
        return user.toProfileResponse()
    }

    suspend fun updateProfile(userId: Int, request: UpdateProfileRequest): ProfileResponse {
        val fullName = request.fullName.trim()
        if (fullName.isBlank()) {
            throw BadRequestException("Full name cannot be blank")
        }
        if (fullName.length > 100) {
            throw BadRequestException("Full name is too long")
        }

        val existing = repository.findUserById(userId)
            ?: throw NotFoundException("User not found")

        val newImageUrl = request.profileImageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val updated = repository.updateProfile(userId, fullName, newImageUrl)
            ?: throw NotFoundException("User not found")

        val oldUrl = existing.profileImageUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (oldUrl != null && oldUrl != newImageUrl) {
            imageStorage.deleteIfManaged(oldUrl)
        }

        return updated.toProfileResponse()
    }

    fun uploadProfileImage(
        bytes: ByteArray,
        originalFileName: String?,
        contentType: String?
    ): ImageUploadResponse {
        val imageUrl = imageStorage.saveProfileImage(bytes, originalFileName, contentType)
        return ImageUploadResponse(imageUrl = imageUrl)
    }

    private fun User.toProfileResponse() = ProfileResponse(
        id = id ?: throw NotFoundException("User not found"),
        username = username,
        email = email,
        fullName = fullName,
        roleId = roleId,
        profileImageUrl = profileImageUrl
    )

    suspend fun authenticateUser(request: LoginRequest): LoginResponse {
        if (request.identifier.isBlank() || request.password.isBlank()) {
            throw BadRequestException("Identifier and password are required")
        }

        val userPair = repository.findByIdentifier(request.identifier)
            ?: throw UnauthorizedException("Invalid username/email or password")

        val (user, passwordHash) = userPair

        if (passwordHash.isNullOrBlank() || !BCrypt.checkpw(request.password, passwordHash)) {
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

    suspend fun authenticateWithGoogle(request: GoogleAuthRequest): LoginResponse {
        val verified = withContext(Dispatchers.IO) {
            firebaseTokenVerifier.verifyIdToken(request.idToken)
        }

        val existingByUid = repository.findByFirebaseUid(verified.uid)
        if (existingByUid != null) {
            return LoginResponse(generateToken(existingByUid), existingByUid)
        }

        val existingByEmail = repository.findByEmail(verified.email)
        if (existingByEmail != null) {
            throw ConflictException("An account with this email already exists. Please log in with your password.")
        }

        val roleId = request.roleId
            ?: throw UnauthorizedException(
                "No StockFlow account was found for this Google account. Please sign up first.",
                code = "ACCOUNT_NOT_FOUND"
            )

        if (!repository.roleExists(roleId)) {
            throw BadRequestException("Invalid role ID")
        }

        val username = uniqueUsername(verified.email, verified.uid)
        val user = repository.createGoogleUser(
            username = username,
            email = verified.email,
            fullName = verified.displayName,
            firebaseUid = verified.uid,
            roleId = roleId
        )

        return LoginResponse(generateToken(user), user)
    }

    private suspend fun uniqueUsername(email: String, firebaseUid: String): String {
        val base = email.substringBefore("@")
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .take(32)
            .ifBlank { "user_${firebaseUid.take(8)}" }

        if (repository.findByUsername(base) == null) {
            return base
        }

        val suffix = firebaseUid.filter { it.isLetterOrDigit() }.take(8)
        return "${base.take(40 - suffix.length - 1)}_$suffix"
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
