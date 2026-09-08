package com.example.stockflow.repositories

import com.example.stockflow.database.DatabaseFactory.dbQuery
import com.example.stockflow.models.Role
import com.example.stockflow.models.Roles
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

interface RoleRepository {
    suspend fun findAll(): List<Role>
}

class RoleRepositoryImpl : RoleRepository {
    override suspend fun findAll(): List<Role> = dbQuery {
        Roles.selectAll()
            .orderBy(Roles.id)
            .map { toRole(it) }
    }

    private fun toRole(row: ResultRow) = Role(
        id = row[Roles.id],
        name = row[Roles.name],
        description = row[Roles.description]
    )
}
