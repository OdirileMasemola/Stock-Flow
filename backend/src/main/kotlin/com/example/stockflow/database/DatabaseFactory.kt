package com.example.stockflow.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.example.stockflow.config.AppConfig
import com.example.stockflow.models.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun init() {
        logger.info("Initializing database connection...")
        try {
            val dataSource = hikari()
            Database.connect(dataSource)
            logger.info("Database connection established successfully.")
            
            transaction { initSchema() }
        } catch (e: Exception) {
            logger.error("CRITICAL: Database initialization failed!", e)
            throw e
        }
    }

    /**
     * Must run inside a transaction. A failure in [migrateShopOwnership] rolls everything back.
     * createMissingTablesAndColumns commits internally, so a later failure leaves the completed
     * (idempotent) ownership migration in place while startup still aborts with the real cause.
     */
    internal fun initSchema() {
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
            Businesses,
            DeviceTokens
        )
        migrateShopOwnership()
        // Users/Products/Businesses/Roles/DeviceTokens: add or widen columns on existing DBs.
        SchemaUtils.createMissingTablesAndColumns(
            Users, Products, Businesses, Roles, DeviceTokens
        )
        widenImageUrlColumns()
        // Required for signup/role picker — not test data.
        seedDefaultRolesIfEmpty()
        logger.info("Database schema verification completed.")
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

    /**
     * Cloud public URLs fit in 500 chars, but widen to 1024 for headroom
     * (custom domains / longer object keys). Only shorter VARCHAR columns are altered, so wider or
     * TEXT columns are never narrowed. Errors are not caught: a failed statement aborts the
     * PostgreSQL transaction, so startup must fail and roll back with the real cause.
     */
    private fun widenImageUrlColumns() {
        val tx = TransactionManager.current()
        for ((table, column) in listOf("products" to "image_url", "users" to "profile_image_url", "businesses" to "image_url")) {
            var needsWidening = false
            tx.exec(
                "SELECT data_type, character_maximum_length FROM information_schema.columns " +
                    "WHERE table_schema = current_schema() AND table_name = '$table' AND column_name = '$column'"
            ) { rs ->
                if (rs.next()) {
                    val length = rs.getInt("character_maximum_length")
                    needsWidening = rs.getString("data_type") == "character varying" && !rs.wasNull() && length < IMAGE_URL_LENGTH
                }
            }
            if (needsWidening) {
                logger.info("Widening {}.{} to VARCHAR({})", table, column, IMAGE_URL_LENGTH)
                tx.exec("ALTER TABLE $table ALTER COLUMN $column TYPE VARCHAR($IMAGE_URL_LENGTH)")
            }
        }
    }

    private const val IMAGE_URL_LENGTH = 1024

    /**
     * Adds owner_user_id to shop tables and makes SKU / supplier name unique per shop.
     * Idempotent. Names match what Exposed generates so createMissingTablesAndColumns sees them.
     * Only sales are backfilled (from the recording user); other existing rows stay unassigned.
     * Runs before createMissingTablesAndColumns so Exposed does not re-create the global SKU index.
     *
     * Global uniqueness is found from the PostgreSQL catalogs by definition, not by name, because
     * older databases may carry Postgres-generated names (e.g. products_sku_key). Startup fails
     * rather than continuing with a schema that would still reject the same SKU in another shop.
     */
    private fun migrateShopOwnership() {
        val tx = TransactionManager.current()
        for (table in listOf("products", "suppliers", "sales", "purchase_orders")) {
            tx.exec("ALTER TABLE $table ADD COLUMN IF NOT EXISTS owner_user_id INTEGER")
            tx.exec("CREATE INDEX IF NOT EXISTS ${table}_owner_user_id ON $table (owner_user_id)")
            tx.exec(
                """
                DO ${'$'}${'$'} BEGIN
                  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_${table}_owner_user_id__id') THEN
                    ALTER TABLE $table ADD CONSTRAINT fk_${table}_owner_user_id__id
                      FOREIGN KEY (owner_user_id) REFERENCES users(id);
                  END IF;
                END ${'$'}${'$'}
                """.trimIndent()
            )
        }
        ensurePerShopUnique(tx, table = "products", column = "sku", indexName = "products_owner_sku_unique")
        ensurePerShopUnique(tx, table = "suppliers", column = "name", indexName = "suppliers_owner_name_unique")
        tx.exec("UPDATE sales SET owner_user_id = user_id WHERE owner_user_id IS NULL")
    }

    private const val OWNER_COLUMN = "owner_user_id"

    private class UniqueIndex(
        val qualifiedName: String,
        val name: String,
        val definition: String,
        /** Key columns in order; null entries are expressions. INCLUDE columns are excluded. */
        val keyColumns: List<String?>,
        /** Every column the index touches: keys, INCLUDE columns, expressions and predicate. */
        val referencedColumns: Set<String>,
        val partial: Boolean,
        val usable: Boolean,
        val nullsNotDistinct: Boolean,
        val constraintName: String?,
        val referencedByForeignKey: Boolean,
    )

    private fun uniqueIndexes(tx: Transaction, table: String): List<UniqueIndex> {
        val result = mutableListOf<UniqueIndex>()
        tx.exec(
            """
            SELECT format('%I.%I', n.nspname, ic.relname) AS qualified_name,
                   ic.relname AS name,
                   pg_get_indexdef(i.indexrelid) AS definition,
                   ARRAY(
                     SELECT a.attname::text
                     FROM unnest(i.indkey::int2[]) WITH ORDINALITY AS k(attnum, ord)
                     LEFT JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum AND k.attnum > 0
                     WHERE k.ord <= i.indnkeyatts
                     ORDER BY k.ord
                   ) AS key_columns,
                   ARRAY(
                     SELECT a.attname::text FROM pg_attribute a
                     WHERE a.attrelid = i.indrelid AND a.attnum > 0
                       AND (a.attnum = ANY (i.indkey::int2[])
                            OR EXISTS (SELECT 1 FROM pg_depend d
                                       WHERE d.classid = 'pg_class'::regclass AND d.objid = i.indexrelid
                                         AND d.refclassid = 'pg_class'::regclass AND d.refobjid = i.indrelid
                                         AND d.refobjsubid = a.attnum))
                   ) AS referenced_columns,
                   i.indpred IS NOT NULL AS partial,
                   (i.indisvalid AND i.indisready AND i.indislive) AS usable,
                   coalesce((to_jsonb(i) ->> 'indnullsnotdistinct')::boolean, false) AS nulls_not_distinct,
                   (SELECT format('%I', c.conname) FROM pg_constraint c
                     WHERE c.conindid = i.indexrelid AND c.conrelid = i.indrelid AND c.contype = 'u') AS constraint_name,
                   EXISTS (SELECT 1 FROM pg_constraint f
                           WHERE f.contype = 'f' AND f.conindid = i.indexrelid) AS referenced_by_fk
            FROM pg_index i
            JOIN pg_class ic ON ic.oid = i.indexrelid
            JOIN pg_namespace n ON n.oid = ic.relnamespace
            WHERE i.indrelid = '$table'::regclass AND i.indisunique AND NOT i.indisprimary
            ORDER BY ic.relname
            """.trimIndent()
        ) { rs ->
            while (rs.next()) {
                result += UniqueIndex(
                    qualifiedName = rs.getString("qualified_name"),
                    name = rs.getString("name"),
                    definition = rs.getString("definition"),
                    keyColumns = (rs.getArray("key_columns").array as Array<*>).map { it as String? },
                    referencedColumns = (rs.getArray("referenced_columns").array as Array<*>)
                        .mapNotNull { it as String? }.toSet(),
                    partial = rs.getBoolean("partial"),
                    usable = rs.getBoolean("usable"),
                    nullsNotDistinct = rs.getBoolean("nulls_not_distinct"),
                    constraintName = rs.getString("constraint_name"),
                    referencedByForeignKey = rs.getBoolean("referenced_by_fk"),
                )
            }
        }
        return result
    }

    /**
     * Replaces global uniqueness on [table].[column] with uniqueness per (owner_user_id, column).
     * The per-shop index is created and verified before anything is dropped. Only plain
     * single-column unique indexes/constraints on [column] are removed; any other shape that would
     * still block the same value in two shops aborts startup so an operator can review it.
     */
    private fun ensurePerShopUnique(tx: Transaction, table: String, column: String, indexName: String) {
        fun UniqueIndex.isPerShop() = usable && !partial && !nullsNotDistinct &&
            keyColumns.size == 2 && keyColumns.toSet() == setOf(OWNER_COLUMN, column)
        // A plain owner_user_id key column means rows from different shops can never collide.
        fun UniqueIndex.blocksAcrossShops() = column in referencedColumns && OWNER_COLUMN !in keyColumns
        fun UniqueIndex.isObsoleteGlobal() = keyColumns == listOf(column) && !partial && !referencedByForeignKey
        fun UniqueIndex.describe() =
            "$name: $definition" + if (referencedByForeignKey) " (referenced by a foreign key)" else ""

        var indexes = uniqueIndexes(tx, table)
        if (indexes.none { it.isPerShop() }) {
            indexes.firstOrNull { it.name == indexName }?.let {
                migrationFailure(table, "$indexName exists but is not a usable unique ($OWNER_COLUMN, $column) index: ${it.describe()}")
            }
            tx.exec("CREATE UNIQUE INDEX $indexName ON $table ($OWNER_COLUMN, $column)")
            indexes = uniqueIndexes(tx, table)
            if (indexes.none { it.isPerShop() }) {
                migrationFailure(table, "could not verify unique ($OWNER_COLUMN, $column) after creating $indexName")
            }
        }

        val blockers = indexes.filter { it.blocksAcrossShops() }
        val unsupported = blockers.filterNot { it.isObsoleteGlobal() }
        if (unsupported.isNotEmpty()) {
            migrationFailure(
                table,
                "unsupported uniqueness on $column would still apply across shops; review manually: " +
                    unsupported.joinToString("; ") { it.describe() }
            )
        }
        for (global in blockers) {
            logger.info("Replacing global unique on {}.{} ({}) with per-shop uniqueness", table, column, global.name)
            if (global.constraintName != null) {
                tx.exec("ALTER TABLE $table DROP CONSTRAINT ${global.constraintName}")
            } else {
                tx.exec("DROP INDEX ${global.qualifiedName}")
            }
        }

        val remaining = uniqueIndexes(tx, table)
        if (remaining.none { it.isPerShop() } || remaining.any { it.blocksAcrossShops() }) {
            migrationFailure(
                table,
                "per-shop uniqueness on $column not established; unique indexes now: " +
                    remaining.joinToString("; ") { it.describe() }
            )
        }
    }

    private fun migrationFailure(table: String, detail: String): Nothing =
        throw IllegalStateException("Shop-ownership migration aborted for table '$table': $detail")

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
