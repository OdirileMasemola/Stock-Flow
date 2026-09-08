package com.example.stockflow.config

import io.github.cdimascio.dotenv.dotenv

object AppConfig {
    private val dotenv = loadDotenv()

    private fun loadDotenv(): io.github.cdimascio.dotenv.Dotenv {
        val directories = listOf("./", "./backend", "../")
        for (directory in directories) {
            val loaded = dotenv {
                ignoreIfMissing = true
                this.directory = directory
                filename = ".env.local"
            }
            if (!loaded["DB_PASSWORD"].isNullOrBlank()) {
                return loaded
            }
        }
        return dotenv {
            ignoreIfMissing = true
            filename = ".env.local"
        }
    }

    val dbDriver = getEnv("DB_DRIVER") ?: "org.postgresql.Driver"
    val dbUrl = getEnv("DB_URL") ?: "jdbc:postgresql://localhost:5432/stockflow_db"
    val dbUser = getEnv("DB_USER") ?: "postgres"
    val dbPassword = getEnv("DB_PASSWORD") ?: throw RuntimeException("DB_PASSWORD environment variable is missing")

    // JWT Configuration
    val jwtSecret = getEnv("JWT_SECRET") ?: "stockflow-super-secret-key-12345"
    val jwtIssuer = getEnv("JWT_ISSUER") ?: "com.example.stockflow"
    val jwtAudience = getEnv("JWT_AUDIENCE") ?: "stockflow-users"
    val jwtExpiration = getEnv("JWT_EXPIRATION")?.toLong() ?: 3600000L // Default 1 hour in ms

    private fun getEnv(key: String): String? {
        return System.getenv(key) ?: dotenv.get(key)
    }
}
