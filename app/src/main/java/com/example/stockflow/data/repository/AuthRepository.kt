package com.example.stockflow.data.repository

import kotlinx.coroutines.delay

class AuthRepository {

    /**
     * Mocks a login API call.
     * Returns true if successful, false otherwise.
     */
    suspend fun login(username: String, password: String): Result<Boolean> {
        // Simulate network delay
        delay(2000)
        
        // Mock logic: allow any non-empty credentials
        return if (username.isNotEmpty() && password.length >= 6) {
            Result.success(true)
        } else {
            Result.failure(Exception("Invalid credentials. Password must be at least 6 characters."))
        }
    }

    /**
     * Mocks a sign-up API call.
     */
    suspend fun signUp(name: String, phone: String, email: String, password: String): Result<Boolean> {
        // Simulate network delay
        delay(2000)
        
        return if (name.isNotEmpty() && phone.isNotEmpty() && email.isNotEmpty() && password.length >= 6) {
            Result.success(true)
        } else {
            Result.failure(Exception("Signup failed. Ensure all fields are filled and password is >= 6 chars."))
        }
    }
}
