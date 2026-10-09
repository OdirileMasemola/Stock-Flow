package com.example.stockflow.database

import com.example.stockflow.models.Businesses
import com.example.stockflow.models.Categories
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.CreateSupplierRequest
import com.example.stockflow.models.DeviceTokens
import com.example.stockflow.models.Products
import com.example.stockflow.models.PurchaseOrderItems
import com.example.stockflow.models.PurchaseOrders
import com.example.stockflow.models.Roles
import com.example.stockflow.models.SaleItems
import com.example.stockflow.models.Sales
import com.example.stockflow.models.Suppliers
import com.example.stockflow.models.UpdateBusinessRequest
import com.example.stockflow.models.Users
import com.example.stockflow.repositories.BusinessRepository
import com.example.stockflow.repositories.DeviceTokenRepository
import com.example.stockflow.repositories.StoredDeviceToken
import com.example.stockflow.services.ProductImageStorage
import com.example.stockflow.services.ProductService
import com.example.stockflow.services.PurchaseOrderService
import com.example.stockflow.services.SaleService
import com.example.stockflow.services.SupplierService
import com.example.stockflow.services.activity.ActivityService
import com.example.stockflow.services.activity.InMemoryActivityStore
import com.example.stockflow.services.notifications.FcmSendResult
import com.example.stockflow.services.notifications.FcmSender
import com.example.stockflow.services.notifications.LowStockAlertService
import com.example.stockflow.services.storage.ImageFolder
import com.example.stockflow.services.storage.ImageStorage
import com.example.stockflow.services.storage.OwnedImageDeleteResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs the real startup migration ([DatabaseFactory.initSchema]) against a disposable PostgreSQL
 * server. Skipped unless STOCKFLOW_PG_TEST_URL points at a local maintenance database, e.g.
 * jdbc:postgresql://127.0.0.1:55432/postgres (optional STOCKFLOW_PG_TEST_USER / _PASSWORD).
 * Each test creates its own sf_migtest_* database and drops it afterwards.
 */
class ShopOwnershipMigrationPostgresTest {

    private val baseUrl: String? = System.getenv("STOCKFLOW_PG_TEST_URL")
    private val user = System.getenv("STOCKFLOW_PG_TEST_USER") ?: "postgres"
    private val password = System.getenv("STOCKFLOW_PG_TEST_PASSWORD") ?: ""
    private lateinit var dbName: String
    private lateinit var dbUrl: String
    private lateinit var db: Database

    private val products = ProductService(
        imageStorage = ProductImageStorage(delegate = NoopImageStorage),
        lowStockAlerts = LowStockAlertService(NoTokens, NoopSender, activity()),
        activityService = activity()
    )
    private val suppliers = SupplierService()
    private val sales = SaleService(lowStockAlerts = LowStockAlertService(NoTokens, NoopSender, activity()))
    private val orders = PurchaseOrderService()

    @BeforeTest
    fun setUp() {
        assumeTrue("STOCKFLOW_PG_TEST_URL not set; skipping PostgreSQL migration tests", baseUrl != null)
        val url = baseUrl!!
        val host = URI(url.removePrefix("jdbc:")).host
        require(host == "127.0.0.1" || host == "localhost") { "Refusing to run migration tests against host $host" }
        require('?' !in url) { "STOCKFLOW_PG_TEST_URL must not contain query parameters" }
        dbName = "sf_migtest_" + UUID.randomUUID().toString().replace("-", "").take(12)
        admin { it.createStatement().use { s -> s.execute("CREATE DATABASE $dbName") } }
        dbUrl = url.substringBeforeLast('/') + "/" + dbName
        db = Database.connect(dbUrl, driver = "org.postgresql.Driver", user = user, password = password)
        TransactionManager.defaultDatabase = db
    }

    @AfterTest
    fun tearDown() {
        if (!::db.isInitialized) return
        TransactionManager.defaultDatabase = null
        TransactionManager.closeAndUnregister(db)
        admin { it.createStatement().use { s -> s.execute("DROP DATABASE IF EXISTS $dbName WITH (FORCE)") } }
    }

    // --- 1. Expected constraint names -------------------------------------------------------

    @Test
    fun migratesExpectedConstraintNames() {
        createLegacySchema(
            "ALTER TABLE products ADD CONSTRAINT products_sku_unique UNIQUE (sku)",
            "ALTER TABLE suppliers ADD CONSTRAINT suppliers_name_unique UNIQUE (name)"
        )
        migrateAndVerifyLegacy()
    }

    // --- 2. Unexpected constraint names -----------------------------------------------------

    @Test
    fun migratesUnexpectedConstraintNames() {
        createLegacySchema(
            "ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku)",
            "ALTER TABLE suppliers ADD CONSTRAINT \"Suppliers Name Legacy\" UNIQUE (name)"
        )
        migrateAndVerifyLegacy()
    }

    // --- 3. Equivalent unique indexes without constraints -----------------------------------

    @Test
    fun migratesPlainUniqueIndexesAndDuplicateGlobals() {
        createLegacySchema(
            "CREATE UNIQUE INDEX legacy_sku_idx ON products (sku)",
            "ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku)",
            "CREATE UNIQUE INDEX \"supplier name idx\" ON suppliers (name)"
        )
        assertEquals(2, uniqueDefs("products").size)
        migrateAndVerifyLegacy()
    }

    @Test
    fun recoversFromStateLeftByPreviousMigrationVersion() {
        createLegacySchema(
            "ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku)",
            "ALTER TABLE suppliers ADD CONSTRAINT suppliers_name_key UNIQUE (name)"
        )
        // What the earlier migration left behind: owner columns + per-shop indexes, global still present.
        exec(
            "ALTER TABLE products ADD COLUMN owner_user_id INTEGER",
            "ALTER TABLE suppliers ADD COLUMN owner_user_id INTEGER",
            "ALTER TABLE sales ADD COLUMN owner_user_id INTEGER",
            "ALTER TABLE purchase_orders ADD COLUMN owner_user_id INTEGER",
            "CREATE UNIQUE INDEX products_owner_sku_unique ON products (owner_user_id, sku)",
            "CREATE UNIQUE INDEX suppliers_owner_name_unique ON suppliers (owner_user_id, name)"
        )
        migrateAndVerifyLegacy()
    }

    // --- 8. Failed migration rolls back -----------------------------------------------------

    @Test
    fun unsupportedExpressionIndexAbortsAndRollsBack() {
        createLegacySchema(
            "ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku)",
            "CREATE UNIQUE INDEX products_sku_lower ON products (lower(sku))",
            "ALTER TABLE suppliers ADD CONSTRAINT suppliers_name_key UNIQUE (name)"
        )
        assertMigrationFailsAndRollsBack("products_sku_lower")
    }

    @Test
    fun globalUniqueReferencedByForeignKeyAbortsAndRollsBack() {
        createLegacySchema(
            "ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku)",
            "ALTER TABLE suppliers ADD CONSTRAINT suppliers_name_key UNIQUE (name)",
            "CREATE TABLE legacy_sku_refs (sku VARCHAR(50) REFERENCES products (sku))"
        )
        assertMigrationFailsAndRollsBack("referenced by a foreign key")
        assertEquals(1, query("SELECT count(*) FROM pg_constraint WHERE conrelid = 'legacy_sku_refs'::regclass AND contype = 'f'").single().toInt())
    }

    @Test
    fun misdefinedIndexWithReservedNameAbortsAndRollsBack() {
        createLegacySchema(
            "CREATE UNIQUE INDEX products_owner_sku_unique ON products (sku)",
            "ALTER TABLE suppliers ADD CONSTRAINT suppliers_name_key UNIQUE (name)"
        )
        assertMigrationFailsAndRollsBack("products_owner_sku_unique exists but is not")
    }

    // --- 9. Repeated startup ----------------------------------------------------------------

    @Test
    fun repeatedStartupIsStable() {
        createLegacySchema(
            "ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku)",
            "CREATE UNIQUE INDEX legacy_supplier_name ON suppliers (name)"
        )
        val data = snapshot()
        startup()
        val schemaAfterFirst = schemaSnapshot()
        startup()
        startup()
        assertEquals(schemaAfterFirst, schemaSnapshot())
        assertEquals(data, snapshot())
        assertPerShopOnly()
        assertLegacyHidden()
        assertPerShopUniquenessEnforced(categoryId = 1, shopA = SHOP_A, shopB = SHOP_B)
    }

    // --- 10. Fresh database -----------------------------------------------------------------

    @Test
    fun freshDatabaseInitialises() {
        startup()
        val schema = schemaSnapshot()
        startup()
        assertEquals(schema, schemaSnapshot())
        assertPerShopOnly()
        assertEquals(listOf("Owner", "Staff", "Supplier"), query("SELECT name FROM roles ORDER BY id"))
        exec(
            "INSERT INTO users (username, email, full_name, role_id) VALUES " +
                "('fresh-a', 'fa@test.local', 'A', 1), ('fresh-b', 'fb@test.local', 'B', 1)",
            "INSERT INTO categories (name) VALUES ('General')"
        )
        assertPerShopUniquenessEnforced(categoryId = 1, shopA = 1, shopB = 2)
        println("Fresh-database catalog:\n" + schemaSnapshot().filter { it.startsWith("products") || it.startsWith("suppliers") }.joinToString("\n"))
    }

    // --- 11. Image URL column widening ------------------------------------------------------

    @Test
    fun narrowImageUrlColumnsAreWidened() {
        startup()
        exec(
            "ALTER TABLE products ALTER COLUMN image_url TYPE VARCHAR(500)",
            "ALTER TABLE users ALTER COLUMN profile_image_url TYPE VARCHAR(500)",
            "ALTER TABLE businesses ALTER COLUMN image_url TYPE VARCHAR(500)"
        )
        startup()
        assertEquals(listOf("1024", "1024", "1024"), imageUrlColumnLengths())
    }

    @Test
    fun imageUrlLongerThanModelFailsStartupWithRealCauseAndKeepsData() {
        startup()
        val longUrl = "https://cdn.test.local/" + "a".repeat(1500)
        exec(
            "ALTER TABLE products ALTER COLUMN image_url TYPE TEXT",
            "INSERT INTO categories (name) VALUES ('General')",
            "INSERT INTO products (name, cost_price, selling_price, category_id, image_url) " +
                "VALUES ('Long', 1, 2, 1, '$longUrl')"
        )

        val error = assertFailsWith<Exception> { startup() }
        println("Expected length failure [$dbName]: ${error.message}")
        assertTrue("value too long" in error.message.orEmpty(), "unexpected diagnostic: ${error.message}")
        assertTrue("current transaction is aborted" !in error.message.orEmpty(), "real cause hidden: ${error.message}")
        assertEquals(listOf(longUrl), query("SELECT image_url FROM products"))
    }

    @Test
    fun imageUrlWideningFailureAbortsWithRealCauseAndRecoversOnRetry() {
        createLegacySchema(
            "ALTER TABLE products ADD CONSTRAINT products_sku_key UNIQUE (sku)",
            "ALTER TABLE suppliers ADD CONSTRAINT suppliers_name_key UNIQUE (name)"
        )
        exec(
            "ALTER TABLE products ALTER COLUMN image_url TYPE VARCHAR(500)",
            "CREATE VIEW product_images AS SELECT id, image_url FROM products"
        )
        val data = snapshot()
        val foreignKeys = foreignKeyDefs()

        val error = assertFailsWith<Exception> { startup() }
        println("Expected widening failure [$dbName]: ${error.message}")
        assertTrue("product_images" in error.message.orEmpty(), "unexpected diagnostic: ${error.message}")
        assertTrue("current transaction is aborted" !in error.message.orEmpty(), "real cause hidden: ${error.message}")
        assertEquals(data, snapshot(), "existing rows changed by a failed startup")
        assertEquals(listOf("500"), query(
            "SELECT character_maximum_length FROM information_schema.columns " +
                "WHERE table_name = 'products' AND column_name = 'image_url'"
        ))

        exec("DROP VIEW product_images")
        startup()
        assertEquals(listOf("1024", "1024", "1024"), imageUrlColumnLengths())
        assertPerShopOnly()
        assertEquals(data, snapshot())
        assertTrue(foreignKeyDefs().containsAll(foreignKeys), "a pre-existing foreign key was removed")
        assertLegacyHidden()
    }

    // --- Scenario helpers -------------------------------------------------------------------

    private fun imageUrlColumnLengths(): List<String> = query(
        "SELECT character_maximum_length FROM information_schema.columns WHERE table_schema = 'public' AND " +
            "((table_name = 'products' AND column_name = 'image_url') OR " +
            "(table_name = 'users' AND column_name = 'profile_image_url') OR " +
            "(table_name = 'businesses' AND column_name = 'image_url')) ORDER BY table_name"
    )

    private fun migrateAndVerifyLegacy() {
        val data = snapshot()
        val foreignKeys = foreignKeyDefs()
        println("Before migration [$dbName]:\n" + (uniqueDefs("products") + uniqueDefs("suppliers")).joinToString("\n"))

        startup()

        println("After migration [$dbName]:\n" + (uniqueDefs("products") + uniqueDefs("suppliers")).joinToString("\n"))
        assertPerShopOnly()
        assertEquals(data, snapshot(), "existing rows changed (ignoring the new owner_user_id column)")
        assertTrue(foreignKeyDefs().containsAll(foreignKeys), "a pre-existing foreign key was removed")
        assertLegacyHidden()
        assertPerShopUniquenessEnforced(categoryId = 1, shopA = SHOP_A, shopB = SHOP_B)
    }

    private fun assertMigrationFailsAndRollsBack(expectedDiagnostic: String) {
        val data = snapshot()
        val before = schemaSnapshot()

        val error = assertFailsWith<IllegalStateException> { startup() }
        println("Expected migration failure [$dbName]: ${error.message}")
        assertTrue(expectedDiagnostic in error.message.orEmpty(), "unexpected diagnostic: ${error.message}")

        assertEquals(before, schemaSnapshot(), "schema not rolled back")
        assertEquals(data, snapshot(), "data not rolled back")
        assertEquals(
            0,
            query(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' " +
                    "AND table_name IN ('products','suppliers','sales','purchase_orders') AND column_name = 'owner_user_id'"
            ).single().toInt(),
            "owner_user_id survived a rolled-back migration"
        )
    }

    /** Legacy rows keep a null owner (hidden); the sale is backfilled to the user who recorded it. */
    private fun assertLegacyHidden() = runBlocking {
        assertEquals("2", query("SELECT count(*) FROM products WHERE owner_user_id IS NULL").single())
        assertEquals("1", query("SELECT count(*) FROM suppliers WHERE owner_user_id IS NULL").single())
        assertEquals("1", query("SELECT count(*) FROM purchase_orders WHERE owner_user_id IS NULL").single())
        assertEquals(listOf("1|1"), query("SELECT user_id || '|' || owner_user_id FROM sales ORDER BY id"))

        for (owner in listOf(LEGACY_USER, SHOP_A, SHOP_B)) {
            assertTrue(products.getProducts(owner).isEmpty())
            assertTrue(suppliers.getSuppliers(owner).isEmpty())
            assertTrue(orders.getPurchaseOrders(owner).isEmpty())
        }
        assertEquals(listOf(1), sales.getSales(LEGACY_USER).map { it.id })
        val legacySale = sales.getSale(1, LEGACY_USER)
        assertEquals(listOf(1 to "Legacy Milk"), legacySale.items.map { it.productId to it.productName })
        assertTrue(sales.getSales(SHOP_A).isEmpty())
    }

    /** Covers same value across shops (allowed) and within a shop (409 in the service, 23505 in SQL). */
    private fun assertPerShopUniquenessEnforced(categoryId: Int, shopA: Int, shopB: Int) = runBlocking<Unit> {
        val a = products.createProduct(productRequest("Milk", "LEG-1", categoryId), shopA)
        products.createProduct(productRequest("Milk", "LEG-1", categoryId), shopB)
        rejects<ConflictException> { products.createProduct(productRequest("Milk 2", "LEG-1", categoryId), shopA) }
        products.createProduct(productRequest("No SKU 1", null, categoryId), shopA)
        products.createProduct(productRequest("No SKU 2", null, categoryId), shopA)

        suppliers.createSupplier(supplierRequest("Legacy Supplier"), shopA)
        suppliers.createSupplier(supplierRequest("Legacy Supplier"), shopB)
        rejects<ConflictException> { suppliers.createSupplier(supplierRequest("Legacy Supplier"), shopA) }

        assertUniqueViolation(
            "INSERT INTO products (name, sku, cost_price, selling_price, category_id, owner_user_id) " +
                "VALUES ('Raw', 'LEG-1', 1, 2, $categoryId, $shopA)"
        )
        assertUniqueViolation("INSERT INTO suppliers (name, owner_user_id) VALUES ('Legacy Supplier', $shopA)")
        assertTrue(products.getProducts(shopA).any { it.id == a.id })
    }

    // --- Schema / data helpers --------------------------------------------------------------

    /** Current models minus the owner columns, plus the given global-uniqueness DDL and demo rows. */
    private fun createLegacySchema(vararg globalUniqueness: String) {
        transaction(db) {
            SchemaUtils.create(
                Roles, Users, Categories, Suppliers, Products, Sales, SaleItems,
                PurchaseOrders, PurchaseOrderItems, Businesses, DeviceTokens
            )
        }
        exec(
            "ALTER TABLE products DROP COLUMN owner_user_id",
            "ALTER TABLE suppliers DROP COLUMN owner_user_id",
            "ALTER TABLE sales DROP COLUMN owner_user_id",
            "ALTER TABLE purchase_orders DROP COLUMN owner_user_id"
        )
        assertTrue(uniqueDefs("products").isEmpty() && uniqueDefs("suppliers").isEmpty())
        exec(*globalUniqueness)
        exec(
            "INSERT INTO roles (name, description) VALUES ('Owner', 'o'), ('Staff', 's'), ('Supplier', 'p')",
            "INSERT INTO users (username, email, full_name, role_id) VALUES " +
                "('legacy-user', 'legacy@test.local', 'Legacy', 1), " +
                "('shop-a', 'a@test.local', 'A', 1), ('shop-b', 'b@test.local', 'B', 1)",
            "INSERT INTO categories (name) VALUES ('General')",
            "INSERT INTO suppliers (name, contact_name, phone, email) VALUES ('Legacy Supplier', 'Sam', '0100000000', 'sam@legacy.test')",
            "INSERT INTO products (name, sku, cost_price, selling_price, stock_level, min_stock_level, category_id, supplier_id) VALUES " +
                "('Legacy Milk', 'LEG-1', 1.00, 2.00, 7, 2, 1, 1), ('Legacy Bread', NULL, 1.00, 2.00, 3, 1, 1, NULL)",
            "INSERT INTO sales (user_id, total_amount, payment_method, created_at) VALUES (1, 4.00, 'Cash', '2025-01-01 10:00')",
            "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal) VALUES (1, 1, 2, 2.00, 4.00)",
            "INSERT INTO purchase_orders (supplier_id, total_amount, status, created_at) VALUES (1, 5.00, 'Pending', '2025-01-02 10:00')",
            "INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, unit_cost, subtotal) VALUES (1, 1, 5, 1.00, 5.00)"
        )
    }

    private fun startup() = transaction(db) { DatabaseFactory.initSchema() }

    private fun assertPerShopOnly() {
        assertEquals(
            listOf("CREATE UNIQUE INDEX products_owner_sku_unique ON public.products USING btree (owner_user_id, sku)"),
            uniqueDefs("products")
        )
        assertEquals(
            listOf("CREATE UNIQUE INDEX suppliers_owner_name_unique ON public.suppliers USING btree (owner_user_id, name)"),
            uniqueDefs("suppliers")
        )
    }

    private fun uniqueDefs(table: String): List<String> = query(
        "SELECT pg_get_indexdef(indexrelid) FROM pg_index " +
            "WHERE indrelid = '$table'::regclass AND indisunique AND NOT indisprimary ORDER BY 1"
    )

    private fun foreignKeyDefs(): List<String> = query(
        "SELECT conrelid::regclass || ' ' || conname || ' ' || pg_get_constraintdef(oid) FROM pg_constraint " +
            "WHERE contype = 'f' AND connamespace = 'public'::regnamespace ORDER BY 1"
    )

    private fun schemaSnapshot(): List<String> = query(
        """
        SELECT c.relname || ' | ' || pg_get_indexdef(i.indexrelid)
        FROM pg_index i JOIN pg_class c ON c.oid = i.indrelid
        WHERE c.relnamespace = 'public'::regnamespace
        UNION ALL
        SELECT conrelid::regclass || ' | ' || conname || ' | ' || pg_get_constraintdef(oid)
        FROM pg_constraint WHERE connamespace = 'public'::regnamespace
        UNION ALL
        SELECT table_name || ' | column ' || column_name || ' ' || data_type
        FROM information_schema.columns WHERE table_schema = 'public'
        ORDER BY 1
        """.trimIndent()
    )

    /** Every legacy row as JSON, ignoring the owner column the migration is allowed to add. */
    private fun snapshot(): Map<String, List<String>> = LEGACY_TABLES.associateWith { table ->
        query("SELECT (to_jsonb(t) - 'owner_user_id')::text FROM $table t ORDER BY id")
    }

    private fun assertUniqueViolation(sql: String) {
        val error = assertFailsWith<SQLException> { exec(sql) }
        assertEquals("23505", error.sqlState, "expected unique violation, got: ${error.message}")
    }

    private fun <T> admin(block: (Connection) -> T): T = DriverManager.getConnection(baseUrl, user, password).use(block)

    private fun exec(vararg statements: String) = DriverManager.getConnection(dbUrl, user, password).use { c ->
        c.createStatement().use { s -> statements.forEach { s.execute(it) } }
    }

    private fun query(sql: String): List<String> = DriverManager.getConnection(dbUrl, user, password).use { c ->
        c.createStatement().use { s ->
            s.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    private suspend inline fun <reified T : Throwable> rejects(crossinline block: suspend () -> Unit): T =
        supervisorScope { assertFailsWith<T> { block() } }

    private fun productRequest(name: String, sku: String?, categoryId: Int) = CreateProductRequest(
        name = name, sku = sku, costPrice = 1.0, sellingPrice = 2.0,
        stockLevel = 10, minStockLevel = 1, categoryId = categoryId
    )

    private fun supplierRequest(name: String) =
        CreateSupplierRequest(name = name, contactName = "Sam", phone = "0100000000", email = "orders@test.local")

    private fun activity() = ActivityService(store = InMemoryActivityStore(), businessRepository = NoBusiness)

    private companion object {
        const val LEGACY_USER = 1
        const val SHOP_A = 2
        const val SHOP_B = 3
        val LEGACY_TABLES = listOf(
            "roles", "users", "categories", "suppliers", "products",
            "sales", "sale_items", "purchase_orders", "purchase_order_items"
        )
    }

    private object NoBusiness : BusinessRepository {
        override suspend fun findByUserId(userId: Int) = null
        override suspend fun create(userId: Int, request: UpdateBusinessRequest) = error("n/a")
        override suspend fun update(userId: Int, request: UpdateBusinessRequest) = error("n/a")
    }

    private object NoTokens : DeviceTokenRepository {
        override suspend fun upsert(userId: Int, token: String, platform: String) = error("n/a")
        override suspend fun deactivate(userId: Int, token: String) = false
        override suspend fun delete(userId: Int, token: String) = false
        override suspend fun markInactiveByToken(token: String) {}
        override suspend fun findActiveTokensForUserIds(userIds: Collection<Int>) = emptyList<StoredDeviceToken>()
        override suspend fun resolveAlertRecipientUserIds(shopOwnerUserId: Int) = listOf(shopOwnerUserId)
    }

    private object NoopSender : FcmSender {
        override fun send(token: String, title: String, body: String, data: Map<String, String>) =
            FcmSendResult.SUCCESS
    }

    private object NoopImageStorage : ImageStorage {
        override fun saveProductImage(bytes: ByteArray, originalFileName: String?, contentType: String?) = "x"
        override fun saveProfileImage(bytes: ByteArray, originalFileName: String?, contentType: String?) = "x"
        override fun saveBusinessImage(bytes: ByteArray, originalFileName: String?, contentType: String?) = "x"
        override fun save(folder: ImageFolder, bytes: ByteArray, originalFileName: String?, contentType: String?) = "x"
        override fun deleteIfManaged(imageUrl: String?) {}
        override fun deleteOwned(imageUrl: String?, folder: ImageFolder) = OwnedImageDeleteResult.Skipped
    }
}
