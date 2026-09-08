package com.example.stockflow.services

import com.example.stockflow.models.Role
import com.example.stockflow.repositories.RoleRepository
import com.example.stockflow.repositories.RoleRepositoryImpl

class RoleService(private val repository: RoleRepository = RoleRepositoryImpl()) {
    suspend fun listRoles(): List<Role> = repository.findAll()
}
