package com.example.stockflow.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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
                    PurchaseOrderItems,
                    Businesses
                )
                // Users/Products/Businesses/Roles: add or widen columns on existing DBs.
                SchemaUtils.createMissingTablesAndColumns(Users, Products, Businesses, Roles)
                // Required for signup/role picker — not test data.
                seedDefaultRolesIfEmpty()
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
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30_000
            idleTimeout = 600_000
            maxLifetime = 1_800_000
            leakDetectionThreshold = 60_000
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }

    private fun seedDefaultRolesIfEmpty() {
        if (Roles.selectAll().count() > 0) {
            return
        }
        logger.info("Seeding default roles...")
        listOf(
            "Owner" to OWNER_ROLE_DESCRIPTION,
            "Staff" to STAFF_ROLE_DESCRIPTION,
            "Supplier" to SUPPLIER_ROLE_DESCRIPTION
        ).forEach { (name, description) ->
            Roles.insert {
                it[Roles.name] = name
                it[Roles.description] = description
            }
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction { block() }

    private const val OWNER_ROLE_DESCRIPTION =
        "Full access to the app. Manage products, sales, suppliers, purchase orders, reports, staff, and store settings. Can update business and account information."

    private const val STAFF_ROLE_DESCRIPTION =
        "Access to daily shop operations. View/manage inventory and make sales. View relevant stock and sales information. Should not manage staff, business settings, or sensitive owner information."

    private const val SUPPLIER_ROLE_DESCRIPTION =
        "Limited supplier-focused access. View their products/orders and purchase orders relevant to them. See order status and quantities. No access to the shop's sales, reports, staff, or private business settings."
}
