package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.*
import org.jetbrains.exposed.sql.*

interface UserRepository {
    suspend fun findUserById(id: Int): User?
    suspend fun findByUsername(username: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByFirebaseUid(firebaseUid: String): User?
    suspend fun findByIdentifier(identifier: String): Pair<User, String?>?
    suspend fun roleExists(roleId: Int): Boolean
    suspend fun createUser(request: RegisterRequest, passwordHash: String): User
    suspend fun createGoogleUser(
        username: String,
        email: String,
        fullName: String,
        firebaseUid: String,
        roleId: Int
    ): User

    suspend fun updateProfile(userId: Int, fullName: String, profileImageUrl: String?): User?
}

class UserRepositoryImpl : UserRepository {
    override suspend fun findUserById(id: Int): User? = dbQuery {
        Users.selectAll().where { Users.id eq id }
            .map { toUser(it) }
            .singleOrNull()
    }

    override suspend fun findByUsername(username: String): User? = dbQuery {
        Users.selectAll().where { Users.username eq username }
            .map { toUser(it) }
            .singleOrNull()
    }

    override suspend fun findByEmail(email: String): User? = dbQuery {
        Users.selectAll().where { Users.email eq email }
            .map { toUser(it) }
            .singleOrNull()
    }

    override suspend fun findByFirebaseUid(firebaseUid: String): User? = dbQuery {
        Users.selectAll().where { Users.firebaseUid eq firebaseUid }
            .map { toUser(it) }
            .singleOrNull()
    }

    override suspend fun findByIdentifier(identifier: String): Pair<User, String?>? = dbQuery {
        Users.selectAll()
            .where { (Users.username eq identifier) or (Users.email eq identifier) }
            .map { toUser(it) to it[Users.passwordHash] }
            .singleOrNull()
    }

    override suspend fun roleExists(roleId: Int): Boolean = dbQuery {
        Roles.selectAll().where { Roles.id eq roleId }.count() > 0
    }

    override suspend fun createUser(request: RegisterRequest, passwordHash: String): User = dbQuery {
        val insertStatement = Users.insert {
            it[username] = request.username
            it[email] = request.email
            it[fullName] = request.fullName
            it[Users.passwordHash] = passwordHash
            it[roleId] = request.roleId
        }
        
        insertStatement.resultedValues?.first()?.let { toUser(it) }
            ?: throw RuntimeException("Failed to create user")
    }

    override suspend fun createGoogleUser(
        username: String,
        email: String,
        fullName: String,
        firebaseUid: String,
        roleId: Int
    ): User = dbQuery {
        val insertStatement = Users.insert {
            it[Users.username] = username
            it[Users.email] = email
            it[Users.fullName] = fullName
            it[Users.passwordHash] = null
            it[Users.firebaseUid] = firebaseUid
            it[Users.roleId] = roleId
        }

        insertStatement.resultedValues?.first()?.let { toUser(it) }
            ?: throw RuntimeException("Failed to create user")
    }

    override suspend fun updateProfile(
        userId: Int,
        fullName: String,
        profileImageUrl: String?
    ): User? = dbQuery {
        val updated = Users.update({ Users.id eq userId }) {
            it[Users.fullName] = fullName
            it[Users.profileImageUrl] = profileImageUrl?.trim()?.takeIf { url -> url.isNotEmpty() }
        }
        if (updated == 0) return@dbQuery null
        Users.selectAll().where { Users.id eq userId }
            .map { toUser(it) }
            .singleOrNull()
    }

    private fun toUser(row: ResultRow) = User(
        id = row[Users.id],
        username = row[Users.username],
        email = row[Users.email],
        fullName = row[Users.fullName],
        roleId = row[Users.roleId],
        profileImageUrl = row[Users.profileImageUrl]
    )
}
