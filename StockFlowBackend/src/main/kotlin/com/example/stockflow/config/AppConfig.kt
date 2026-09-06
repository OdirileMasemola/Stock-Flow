package com.example.stockflow.config

object AppConfig {
    val dbDriver = System.getenv("DB_DRIVER") ?: "org.postgresql.Driver"
    val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/stockflow_db"
    val dbUser = System.getenv("DB_USER") ?: "postgres"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "password"
}
