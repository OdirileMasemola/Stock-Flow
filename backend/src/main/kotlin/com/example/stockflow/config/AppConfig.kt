package com.example.stockflow.config

object AppConfig {
    val dbDriver = System.getenv("DB_DRIVER") ?: "org.postgresql.Driver"
    val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/stockflow_db"
    val dbUser = System.getenv("DB_USER") ?: "postgres"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "password"

    // JWT Configuration
    val jwtSecret = System.getenv("JWT_SECRET") ?: "stockflow-super-secret-key-12345"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "com.example.stockflow"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "stockflow-users"
    val jwtExpiration = System.getenv("JWT_EXPIRATION")?.toLong() ?: 3600000L // Default 1 hour in ms
}
