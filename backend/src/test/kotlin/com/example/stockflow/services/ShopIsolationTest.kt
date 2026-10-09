package com.example.stockflow.services

import com.example.stockflow.models.AccountClosure
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.Businesses
import com.example.stockflow.models.Categories
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.CreateProductRequest
import com.example.stockflow.models.CreatePurchaseOrderItemRequest
import com.example.stockflow.models.CreatePurchaseOrderRequest
import com.example.stockflow.models.CreateSaleItemRequest
import com.example.stockflow.models.CreateSaleRequest
import com.example.stockflow.models.CreateSupplierRequest
import com.example.stockflow.models.DeviceTokens
import com.example.stockflow.models.ForbiddenException
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.PurchaseOrderItems
import com.example.stockflow.models.PurchaseOrders
import com.example.stockflow.models.Products
import com.example.stockflow.models.Roles
import com.example.stockflow.models.SaleItems
import com.example.stockflow.models.Sales
import com.example.stockflow.models.Suppliers
import com.example.stockflow.models.UnauthorizedException
import com.example.stockflow.models.UpdateBusinessRequest
import com.example.stockflow.models.UpdateProductRequest
import com.example.stockflow.models.UpdatePurchaseOrderRequest
import com.example.stockflow.models.UpdateSupplierRequest
import com.example.stockflow.models.Users
import com.example.stockflow.repositories.BusinessRepository
import com.example.stockflow.repositories.DeviceTokenRepositoryImpl
import com.example.stockflow.repositories.StoredDeviceToken
import com.example.stockflow.repositories.DeviceTokenRepository
import com.example.stockflow.services.activity.ActivityService
import com.example.stockflow.services.activity.InMemoryActivityStore
import com.example.stockflow.services.notifications.FcmSendResult
import com.example.stockflow.services.notifications.FcmSender
import com.example.stockflow.services.notifications.LowStockAlertService
import com.example.stockflow.services.storage.ImageFolder
import com.example.stockflow.services.storage.ImageStorage
import com.example.stockflow.services.storage.LocalDiskImageStorage
import com.example.stockflow.services.storage.OwnedImageDeleteResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Shop isolation against a throwaway in-memory H2 database (PostgreSQL mode).
 * Shop A and shop B are separate owner accounts; nothing touches the real database.
 */
class ShopIsolationTest {

    private var ownerA = 0
    private var ownerB = 0
    private var staffC = 0
    private var supplierD = 0
    private var closedE = 0
    private var categoryId = 0

    private val access = ShopAccessService()
    private val products = ProductService(
        imageStorage = ProductImageStorage(delegate = NoopImageStorage),
        lowStockAlerts = LowStockAlertService(NoTokens, NoopSender, activity()),
        activityService = activity()
    )
    private val sales = SaleService(lowStockAlerts = LowStockAlertService(NoTokens, NoopSender, activity()))
    private val suppliers = SupplierService()
    private val orders = PurchaseOrderService()
    private val dashboard = DashboardService()

    @BeforeTest
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:shop_${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(
                Roles, Users, Categories, Suppliers, Products, Sales, SaleItems,
                PurchaseOrders, PurchaseOrderItems, Businesses, DeviceTokens
            )
            val ownerRole = insertRole("Owner")
            val staffRole = insertRole("Staff")
            val supplierRole = insertRole("Supplier")
            ownerA = insertUser("shop-a", "a@test.local", ownerRole)
            ownerB = insertUser("shop-b", "b@test.local", ownerRole)
            staffC = insertUser("staff-c", "c@test.local", staffRole)
            supplierD = insertUser("supplier-d", "d@test.local", supplierRole)
            closedE = insertUser("pending-close", "e@test.local", ownerRole)
            markClosed(closedE)
            categoryId = Categories.insert { it[name] = "General" }[Categories.id]
        }
    }

    // --- 2. Owner reads own records ---------------------------------------------------------

    @Test
    fun ownerCanUseOwnRecords() = runBlocking<Unit> {
        val shop = access.requireShopMember(ownerA)
        assertEquals(ownerA, shop.ownerUserId)

        val supplier = suppliers.createSupplier(supplierRequest("Acme"), shop.ownerUserId)
        val product = products.createProduct(productRequest("Milk", "MILK-1", supplier.id), shop.ownerUserId)
        val sale = sales.createSale(shop.userId, shop.ownerUserId, saleRequest(product.id, 2))
        val order = orders.createPurchaseOrder(orderRequest(supplier.id, product.id), shop.ownerUserId)

        assertEquals(listOf(product.id), products.getProducts(ownerA).map { it.id })
        assertEquals(supplier.id, suppliers.getSupplier(supplier.id, ownerA).id)
        assertEquals(sale.id, sales.getSale(sale.id, ownerA).id)
        assertEquals(order.id, orders.getPurchaseOrder(order.id, ownerA).id)
        assertEquals("Received", orders.receivePurchaseOrder(order.id, ownerA).status)
        assertEquals(10 - 2 + 4, products.getProduct(product.id, ownerA).stockLevel)
    }

    // --- 3. Staff work in their own shop ----------------------------------------------------

    @Test
    fun staffCanSellAndManageProductsInOwnShop() = runBlocking<Unit> {
        val shop = access.requireShopMember(staffC)
        assertEquals(staffC, shop.ownerUserId)

        val product = products.createProduct(productRequest("Bread", "BRD-1"), shop.ownerUserId, shop.userId)
        val sale = sales.createSale(shop.userId, shop.ownerUserId, saleRequest(product.id, 1))
        assertEquals(staffC, sale.userId)
        assertEquals(listOf(sale.id), sales.getSales(staffC).map { it.id })
        assertTrue(products.getProducts(ownerA).isEmpty())
    }

    // --- 4. Sales are private ---------------------------------------------------------------

    @Test
    fun otherShopCannotReadSales() = runBlocking<Unit> {
        val product = products.createProduct(productRequest("Milk", "MILK-1"), ownerA)
        val sale = sales.createSale(ownerA, ownerA, saleRequest(product.id, 1))

        assertTrue(sales.getSales(ownerB).isEmpty())
        rejects<NotFoundException> { sales.getSale(sale.id, ownerB) }
    }

    // --- 5. Supplier contacts are private ---------------------------------------------------

    @Test
    fun otherShopCannotReadOrChangeSuppliers() = runBlocking<Unit> {
        val supplier = suppliers.createSupplier(supplierRequest("Acme"), ownerA)

        assertTrue(suppliers.getSuppliers(ownerB).isEmpty())
        rejects<NotFoundException> { suppliers.getSupplier(supplier.id, ownerB) }
        rejects<NotFoundException> {
            suppliers.updateSupplier(supplier.id, UpdateSupplierRequest(name = "Hijacked"), ownerB)
        }
        rejects<NotFoundException> { suppliers.deleteSupplier(supplier.id, ownerB) }

        val stored = suppliers.getSupplier(supplier.id, ownerA)
        assertEquals("Acme", stored.name)
        assertEquals("orders@acme.test", stored.email)
    }

    // --- 6. Purchase orders are private -----------------------------------------------------

    @Test
    fun otherShopCannotReadOrChangePurchaseOrders() = runBlocking<Unit> {
        val supplier = suppliers.createSupplier(supplierRequest("Acme"), ownerA)
        val product = products.createProduct(productRequest("Milk", "MILK-1"), ownerA)
        val order = orders.createPurchaseOrder(orderRequest(supplier.id, product.id), ownerA)

        val supplierB = suppliers.createSupplier(supplierRequest("Beta"), ownerB)
        val productB = products.createProduct(productRequest("Tea", "TEA-1"), ownerB)

        assertTrue(orders.getPurchaseOrders(ownerB).isEmpty())
        rejects<NotFoundException> { orders.getPurchaseOrder(order.id, ownerB) }
        rejects<NotFoundException> {
            orders.updatePurchaseOrder(
                order.id,
                UpdatePurchaseOrderRequest(supplierB.id, null, listOf(CreatePurchaseOrderItemRequest(productB.id, 1, 1.0))),
                ownerB
            )
        }
        rejects<NotFoundException> { orders.receivePurchaseOrder(order.id, ownerB) }

        val stored = orders.getPurchaseOrder(order.id, ownerA)
        assertEquals("Pending", stored.status)
        assertEquals(listOf(product.id), stored.items.map { it.productId })
        assertEquals(10, products.getProduct(product.id, ownerA).stockLevel)
    }

    // --- 7. Products can't be changed by another shop ---------------------------------------

    @Test
    fun otherShopCannotReadUpdateOrDeleteProducts() = runBlocking<Unit> {
        val product = products.createProduct(productRequest("Milk", "MILK-1"), ownerA)

        assertTrue(products.getProducts(ownerB).isEmpty())
        assertTrue(products.getLowStockProducts(ownerB).isEmpty())
        rejects<NotFoundException> { products.getProduct(product.id, ownerB) }
        rejects<NotFoundException> { products.getProductBySku("MILK-1", ownerB) }
        rejects<NotFoundException> {
            products.updateProduct(product.id, updateRequest("Hijacked", stock = 0), ownerB)
        }
        rejects<NotFoundException> { products.deleteProduct(product.id, ownerB) }

        val stored = products.getProduct(product.id, ownerA)
        assertEquals("Milk", stored.name)
        assertEquals(10, stored.stockLevel)
    }

    @Test
    fun anotherShopCannotDeleteAProductPhotoItStillUses() = runBlocking<Unit> {
        val root = Files.createTempDirectory("shop-images").toFile()
        val productsWithImages = ProductService(
            imageStorage = ProductImageStorage(delegate = LocalDiskImageStorage(root, 1024)),
            lowStockAlerts = LowStockAlertService(NoTokens, NoopSender, activity()),
            activityService = activity()
        )
        val photoA = File(root, "products/a.jpg").apply { writeText("a") }
        val profilePhoto = File(root, "profiles/someone.jpg").apply { writeText("p") }
        val photoB = File(root, "products/b.jpg").apply { writeText("b") }
        try {
            productsWithImages.createProduct(productRequest("Milk", "MILK-1").copy(imageUrl = "/uploads/products/a.jpg"), ownerA)

            val copied = productsWithImages.createProduct(
                productRequest("Tea", "TEA-1").copy(imageUrl = "/uploads/products/a.jpg"), ownerB
            )
            productsWithImages.updateProduct(
                copied.id,
                updateRequest("Tea", stock = 10).copy(imageUrl = "/uploads/profiles/someone.jpg"),
                ownerB
            )
            productsWithImages.deleteProduct(copied.id, ownerB)
            assertTrue(photoA.exists())
            assertTrue(profilePhoto.exists())

            val own = productsWithImages.createProduct(
                productRequest("Jam", "JAM-1").copy(imageUrl = "/uploads/products/b.jpg"), ownerB
            )
            productsWithImages.deleteProduct(own.id, ownerB)
            assertFalse(photoB.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    // --- 8. Manipulated IDs and owner fields ------------------------------------------------

    @Test
    fun foreignIdsInRequestBodiesAreRejected() = runBlocking<Unit> {
        val supplierA = suppliers.createSupplier(supplierRequest("Acme"), ownerA)
        val productA = products.createProduct(productRequest("Milk", "MILK-1"), ownerA)
        val supplierB = suppliers.createSupplier(supplierRequest("Beta"), ownerB)
        val productB = products.createProduct(productRequest("Tea", "TEA-1"), ownerB)

        rejects<NotFoundException> { sales.createSale(ownerB, ownerB, saleRequest(productA.id, 1)) }
        rejects<NotFoundException> {
            orders.createPurchaseOrder(orderRequest(supplierA.id, productB.id), ownerB)
        }
        rejects<NotFoundException> {
            orders.createPurchaseOrder(orderRequest(supplierB.id, productA.id), ownerB)
        }
        rejects<BadRequestException> {
            products.createProduct(productRequest("Sneaky", "SNK-1", supplierA.id), ownerB)
        }

        assertEquals(10, products.getProduct(productA.id, ownerA).stockLevel)
        assertTrue(sales.getSales(ownerA).isEmpty())
        assertTrue(orders.getPurchaseOrders(ownerA).isEmpty())
    }

    @Test
    fun ownerFieldInRequestBodyIsIgnored() = runBlocking<Unit> {
        val json = Json { ignoreUnknownKeys = true }
        val body = """{"name":"Planted","sku":"PLT-1","costPrice":1.0,"sellingPrice":2.0,""" +
            """"stockLevel":10,"minStockLevel":1,"categoryId":$categoryId,"ownerUserId":$ownerA}"""
        val request = json.decodeFromString(CreateProductRequest.serializer(), body)

        val shopB = access.requireShopMember(ownerB)
        products.createProduct(request, shopB.ownerUserId, shopB.userId)

        assertTrue(products.getProducts(ownerA).isEmpty())
        assertEquals(listOf("Planted"), products.getProducts(ownerB).map { it.name })
    }

    // --- 9. Child records follow their parent shop ------------------------------------------

    @Test
    fun saleAndOrderItemsAreOnlyReachableThroughOwnedParent() = runBlocking<Unit> {
        val supplier = suppliers.createSupplier(supplierRequest("Acme"), ownerA)
        val product = products.createProduct(productRequest("Milk", "MILK-1"), ownerA)
        val sale = sales.createSale(ownerA, ownerA, saleRequest(product.id, 3))
        val order = orders.createPurchaseOrder(orderRequest(supplier.id, product.id), ownerA)

        assertEquals(1, sales.getSale(sale.id, ownerA).items.size)
        assertEquals(1, orders.getPurchaseOrder(order.id, ownerA).items.size)
        assertTrue(sales.getSales(ownerB).flatMap { it.items }.isEmpty())
        assertTrue(orders.getPurchaseOrders(ownerB).flatMap { it.items }.isEmpty())
        rejects<NotFoundException> { sales.getSale(sale.id, ownerB) }
        rejects<NotFoundException> { orders.getPurchaseOrder(order.id, ownerB) }
    }

    // --- 10. Dashboard and reports ----------------------------------------------------------

    @Test
    fun dashboardAndReportsOnlyCountOwnShop() = runBlocking<Unit> {
        val supplierA = suppliers.createSupplier(supplierRequest("Acme"), ownerA)
        val productA = products.createProduct(productRequest("Milk", "MILK-1", cost = 2.0, price = 5.0), ownerA)
        sales.createSale(ownerA, ownerA, saleRequest(productA.id, 2))
        orders.createPurchaseOrder(orderRequest(supplierA.id, productA.id), ownerA)

        val productB = products.createProduct(productRequest("Tea", "TEA-1", cost = 1.0, price = 3.0, stock = 1), ownerB)
        sales.createSale(ownerB, ownerB, saleRequest(productB.id, 1))

        val a = dashboard.getSummary(ownerA)
        assertEquals(1, a.totalProducts)
        assertEquals(8, a.totalStockQuantity)
        assertEquals(16.0, a.inventoryValue)
        assertEquals(10.0, a.todaySalesTotal)
        assertEquals(1, a.todaySalesCount)
        assertEquals(0, a.lowStockCount)
        assertEquals(1, a.recentSales.size)
        assertEquals(1, a.recentPurchaseOrders.size)
        assertEquals(10.0, a.weeklySales.sumOf { it.totalAmount })

        val b = dashboard.getSummary(ownerB)
        assertEquals(1, b.totalProducts)
        assertEquals(3.0, b.todaySalesTotal)
        assertEquals(1, b.lowStockCount)
        assertEquals(listOf("Tea"), b.lowStockPreview.map { it.name })
        assertTrue(b.recentPurchaseOrders.isEmpty())

        val reportA = dashboard.getReports(ownerA, "7d", null, null)
        assertEquals(10.0, reportA.sales.totalSales)
        assertEquals(1, reportA.sales.salesCount)
        assertEquals(1, reportA.purchases.purchaseOrderCount)
        assertEquals(1, reportA.inventory.totalProducts)

        val reportB = dashboard.getReports(ownerB, "7d", null, null)
        assertEquals(3.0, reportB.sales.totalSales)
        assertEquals(0, reportB.purchases.purchaseOrderCount)
        assertEquals(0.0, reportB.purchases.purchasingTotal)

        val empty = dashboard.getSummary(staffC)
        assertEquals(0, empty.totalProducts)
        assertEquals(0.0, empty.todaySalesTotal)
        assertTrue(empty.recentSales.isEmpty())
    }

    // --- 11. Supplier role and closed accounts ----------------------------------------------

    @Test
    fun supplierRoleHasNoShopAccess() {
        runBlocking {
            rejects<ForbiddenException> { access.requireShopMember(supplierD) }
        }
    }

    @Test
    fun closedOrUnknownAccountsAreRejected() {
        runBlocking {
            rejects<UnauthorizedException> { access.requireShopMember(closedE) }
            rejects<UnauthorizedException> { access.requireShopMember(99_999) }
        }
    }

    // --- Migration behaviour ----------------------------------------------------------------

    @Test
    fun legacyRowsWithoutOwnerAreHiddenFromEveryone() = runBlocking<Unit> {
        val generalCategory = categoryId
        val legacyProductId = transaction {
            val supplierId = Suppliers.insert { it[name] = "Old supplier" }[Suppliers.id]
            val productId = Products.insert {
                it[name] = "Old product"
                it[sku] = "OLD-1"
                it[costPrice] = BigDecimal("1.00")
                it[sellingPrice] = BigDecimal("2.00")
                it[stockLevel] = 1
                it[minStockLevel] = 5
                it[Products.categoryId] = generalCategory
                it[Products.supplierId] = supplierId
            }[Products.id]
            PurchaseOrders.insert {
                it[PurchaseOrders.supplierId] = supplierId
                it[totalAmount] = BigDecimal("5.00")
            }
            productId
        }

        for (owner in listOf(ownerA, ownerB, staffC)) {
            assertTrue(products.getProducts(owner).isEmpty())
            assertTrue(products.getLowStockProducts(owner).isEmpty())
            assertTrue(suppliers.getSuppliers(owner).isEmpty())
            assertTrue(orders.getPurchaseOrders(owner).isEmpty())
            rejects<NotFoundException> { products.getProduct(legacyProductId, owner) }
            rejects<NotFoundException> { products.deleteProduct(legacyProductId, owner) }
            assertEquals(0, dashboard.getSummary(owner).totalProducts)
        }
    }

    @Test
    fun skuAndSupplierNameAreUniquePerShop() = runBlocking<Unit> {
        products.createProduct(productRequest("Milk", "MILK-1"), ownerA)
        products.createProduct(productRequest("Milk", "MILK-1"), ownerB)
        suppliers.createSupplier(supplierRequest("Acme"), ownerA)
        suppliers.createSupplier(supplierRequest("Acme"), ownerB)

        rejects<ConflictException> { products.createProduct(productRequest("Milk 2", "MILK-1"), ownerA) }
        rejects<ConflictException> { suppliers.createSupplier(supplierRequest("Acme"), ownerA) }
    }

    @Test
    fun lowStockAlertsOnlyGoToShopOwner() = runBlocking<Unit> {
        val repo = DeviceTokenRepositoryImpl()
        assertEquals(listOf(ownerA), repo.resolveAlertRecipientUserIds(ownerA))
        assertEquals(listOf(ownerB), repo.resolveAlertRecipientUserIds(ownerB))
    }

    // --- Helpers ----------------------------------------------------------------------------

    /** dbQuery runs as a child coroutine; a supervisor keeps an expected failure from cancelling the test. */
    private suspend inline fun <reified T : Throwable> rejects(crossinline block: suspend () -> Unit): T =
        supervisorScope { assertFailsWith<T> { block() } }

    private fun insertRole(roleName: String): Int = Roles.insert { it[name] = roleName }[Roles.id]

    private fun insertUser(username: String, email: String, roleId: Int): Int = Users.insert {
        it[Users.username] = username
        it[Users.email] = email
        it[fullName] = username
        it[Users.roleId] = roleId
    }[Users.id]

    private fun markClosed(id: Int) {
        Users.update({ Users.id eq id }) {
            it[username] = AccountClosure.username(id)
            it[email] = AccountClosure.email(id)
        }
    }

    private fun productRequest(
        name: String,
        sku: String?,
        supplierId: Int? = null,
        cost: Double = 1.0,
        price: Double = 2.0,
        stock: Int = 10
    ) = CreateProductRequest(
        name = name,
        sku = sku,
        costPrice = cost,
        sellingPrice = price,
        stockLevel = stock,
        minStockLevel = 1,
        categoryId = categoryId,
        supplierId = supplierId
    )

    private fun updateRequest(name: String, stock: Int) = UpdateProductRequest(
        name = name,
        sku = null,
        costPrice = 1.0,
        sellingPrice = 2.0,
        stockLevel = stock,
        minStockLevel = 1,
        categoryId = categoryId
    )

    private fun supplierRequest(name: String) =
        CreateSupplierRequest(name = name, contactName = "Sam", phone = "0100000000", email = "orders@acme.test")

    private fun saleRequest(productId: Int, quantity: Int) =
        CreateSaleRequest(paymentMethod = "Cash", items = listOf(CreateSaleItemRequest(productId, quantity)))

    private fun orderRequest(supplierId: Int, productId: Int) = CreatePurchaseOrderRequest(
        supplierId = supplierId,
        items = listOf(CreatePurchaseOrderItemRequest(productId = productId, quantity = 4, unitCost = 1.5))
    )

    private fun activity() = ActivityService(store = InMemoryActivityStore(), businessRepository = NoBusiness)

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
