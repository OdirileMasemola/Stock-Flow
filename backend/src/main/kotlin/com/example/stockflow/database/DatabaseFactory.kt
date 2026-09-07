package com.example.stockflow.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun init() {
        logger.info("Initializing database connection...")
        try {
            val dataSource = hikari()
            Database.connect(dataSource)
            logger.info("Database connection established successfully.")
            
            transaction {
                logger.info("Starting schema creation...")
                // Create tables if they don't exist
                SchemaUtils.create(
                    Roles, 
                    Users, 
                    Categories, 
                    Suppliers, 
                    Products, 
                    Sales, 
                    SaleItems, 
                    PurchaseOrders, 
                    PurchaseOrderItems
                )
                logger.info("Database schema verification completed.")
            }
        } catch (e: Exception) {
            logger.error("CRITICAL: Database initialization failed!", e)
            throw e
        }
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig().apply {
            driverClassName = AppConfig.dbDriver
            jdbcUrl = AppConfig.dbUrl
            username = AppConfig.dbUser
            password = AppConfig.dbPassword
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction { block() }
}
