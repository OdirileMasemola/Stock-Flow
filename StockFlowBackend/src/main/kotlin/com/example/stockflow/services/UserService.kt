package com.example.stockflow.services

import com.example.stockflow.models.User
import com.example.stockflow.repositories.UserRepository
import com.example.stockflow.repositories.UserRepositoryImpl

class UserService(private val repository: UserRepository = UserRepositoryImpl()) {
    suspend fun getUser(id: Int): User? {
        return repository.findUserById(id)
    }
}
