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
    val dbUrl = getEnv("DB_URL") ?: "jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres"
    val dbUser = getEnv("DB_USER") ?: "postgres.vvwciismgnblpvpurujb"
    val dbPassword = getEnv("DB_PASSWORD") ?: throw RuntimeException("DB_PASSWORD environment variable is missing")

    // Server Configuration
    val serverPort = getEnv("PORT")?.toIntOrNull() ?: 8080
    val corsAllowedHosts = getEnv("CORS_ALLOWED_HOSTS")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    // JWT Configuration — fail closed if secret is missing (never use a hardcoded default).
    val jwtSecret = getEnv("JWT_SECRET")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw RuntimeException("JWT_SECRET environment variable is missing")
    val jwtIssuer = getEnv("JWT_ISSUER") ?: "com.example.stockflow"
    val jwtAudience = getEnv("JWT_AUDIENCE") ?: "stockflow-users"
    val jwtExpiration = getEnv("JWT_EXPIRATION")?.toLong() ?: 3600000L // Default 1 hour in ms

    val firebaseCredentialsPath = getEnv("FIREBASE_CREDENTIALS_PATH")

    /**
     * OAuth 2.0 Web client ID used to validate Google ID token `aud` claims.
     * Must match the Android app's requestIdToken / google_web_client_id.
     */
    val googleWebClientId: String = getEnv("GOOGLE_WEB_CLIENT_ID")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "664389052883-cqddukkpbu4tp5ffre0mh9l2hpegsek5.apps.googleusercontent.com"

    /**
     * Directory for uploaded product images (relative or absolute).
     * Defaults work from either the repo root or the backend module working directory.
     */
    val uploadsDir: String = resolveUploadsDir()

    private fun resolveUploadsDir(): String {
        val configured = getEnv("UPLOADS_DIR")?.trim()?.takeIf { it.isNotEmpty() }
        if (configured != null) return configured
        val fromRoot = java.io.File("backend/uploads")
        if (java.io.File("backend").isDirectory || fromRoot.parentFile?.exists() == true) {
            return fromRoot.path
        }
        return "uploads"
    }

    private fun getEnv(key: String): String? {
        return System.getenv(key) ?: dotenv.get(key)
    }
}
