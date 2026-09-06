package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.User
import com.example.stockflow.models.Users
import org.jetbrains.exposed.sql.*

interface UserRepository {
    suspend fun findUserById(id: Int): User?
}

class UserRepositoryImpl : UserRepository {
    override suspend fun findUserById(id: Int): User? = dbQuery {
        Users.selectAll().where { Users.id eq id }
            .map { User(it[Users.id], it[Users.username], it[Users.email], it[Users.fullName]) }
            .singleOrNull()
    }
}
