package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.*
import org.jetbrains.exposed.sql.*

interface UserRepository {
    suspend fun findUserById(id: Int): User?
    suspend fun findByUsername(username: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun roleExists(roleId: Int): Boolean
    suspend fun createUser(request: RegisterRequest, passwordHash: String): User
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

    private fun toUser(row: ResultRow) = User(
        id = row[Users.id],
        username = row[Users.username],
        email = row[Users.email],
        fullName = row[Users.fullName],
        roleId = row[Users.roleId]
    )
}
