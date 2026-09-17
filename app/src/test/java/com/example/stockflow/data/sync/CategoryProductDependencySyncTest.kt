package com.example.stockflow.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stockflow.StockFlowApp
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.data.local.cache.PendingEntityType
import com.example.stockflow.data.local.cache.PendingOpStatus
import com.example.stockflow.data.local.cache.PendingOpType
import com.example.stockflow.data.local.cache.PendingOperationEntity
import com.example.stockflow.data.local.cache.StockFlowCacheDatabase
import com.example.stockflow.data.remote.CategoryApi
import com.example.stockflow.data.remote.CategoryDto
import com.example.stockflow.data.remote.CreateCategoryRequest
import com.example.stockflow.data.remote.CreateProductRequest
import com.example.stockflow.data.remote.ProductApi
import com.example.stockflow.data.remote.ProductDto
import com.example.stockflow.data.repository.CategoryRepository
import com.example.stockflow.data.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class CategoryProductDependencySyncTest {

    private lateinit var db: StockFlowCacheDatabase
    private lateinit var productApi: ProductApi
    private lateinit var categoryApi: CategoryApi
    private lateinit var sessionStore: SessionStore
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var productRepo: ProductRepository
    private lateinit var processor: PendingSyncProcessor
    private var now = 50_000L

    @Before
    fun setUp() {
        LocalTempIds.resetForTests(-1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StockFlowCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productApi = mockk(relaxed = true)
        categoryApi = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)
        every { sessionStore.getToken() } returns "token"
        every { sessionStore.getUserId() } returns 7
        categoryRepo = CategoryRepository(
            api = categoryApi,
            sessionStore = sessionStore,
            database = db,
            clock = { now },
            onEnqueueSync = {}
        )
        productRepo = ProductRepository(
            api = productApi,
            sessionStore = sessionStore,
            database = db,
            clock = { now },
            onEnqueueSync = {}
        )
        processor = PendingSyncProcessor(
            api = productApi,
            categoryApi = categoryApi,
            sessionStore = sessionStore,
            database = db,
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun offlineCategoryCreateEnqueuesAndCaches() = runBlocking {
        coEvery { categoryApi.createCategory(any(), any()) } throws IOException("offline")

        val result = categoryRepo.findOrCreateCategory("  Snacks  ")

        assertTrue(result is WriteResult.Queued)
        val dto = (result as WriteResult.Queued).data
        assertTrue(LocalTempIds.isTemporary(dto.id))
        assertEquals("Snacks", dto.name)
        assertEquals(1, db.pendingOperationDao().countActive(7))
        assertEquals(PendingEntityType.CATEGORY, db.pendingOperationDao().getPendingForUser(7).single().entityType)
        assertEquals("Snacks", db.categoryDao().getById(7, dto.id)?.name)
    }

    @Test
    fun categorySyncsBeforeProductAndRewritesCategoryId() = runBlocking {
        // Seed pending CATEGORY then PRODUCT with temp category id
        val catLocal = -10
        val prodLocal = -20
        db.pendingOperationDao().upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = 7,
                operationType = PendingOpType.CREATE,
                entityType = PendingEntityType.CATEGORY,
                localEntityId = catLocal.toString(),
                remoteEntityId = null,
                payloadJson = CategoryWritePayload.toJson(CategoryWritePayload("Snacks")),
                status = PendingOpStatus.PENDING,
                retryCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
        db.categoryDao().upsert(
            com.example.stockflow.data.local.cache.CachedCategory(7, catLocal, "Snacks", null, now)
        )
        val productPayload = ProductWritePayload(
            name = "Chips", sku = "C1", costPrice = 1.0, sellingPrice = 2.0,
            stockLevel = 5, minStockLevel = 1, categoryId = catLocal, categoryName = "Snacks"
        )
        db.pendingOperationDao().upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = 7,
                operationType = PendingOpType.CREATE,
                entityType = PendingEntityType.PRODUCT,
                localEntityId = prodLocal.toString(),
                remoteEntityId = null,
                payloadJson = ProductWritePayload.toJson(productPayload),
                status = PendingOpStatus.PENDING,
                retryCount = 0,
                lastError = null,
                createdAt = now + 1,
                updatedAt = now + 1
            )
        )
        db.productDao().upsert(
            com.example.stockflow.data.local.cache.CachedProduct(
                7, prodLocal, "Chips", "C1", 1.0, 2.0, 5, 1, catLocal, "Snacks", null, null, now
            )
        )

        coEvery { categoryApi.createCategory(any(), any()) } returns Response.success(
            CategoryDto(id = 99, name = "Snacks")
        )
        var capturedCategoryId: Int? = null
        coEvery { productApi.createProduct(any(), any()) } answers {
            val req = secondArg<com.example.stockflow.data.remote.CreateProductRequest>()
            capturedCategoryId = req.categoryId
            Response.success(
                ProductDto(
                    id = 55, name = "Chips", sku = "C1", costPrice = 1.0, sellingPrice = 2.0,
                    stockLevel = 5, minStockLevel = 1, categoryId = 99, categoryName = "Snacks"
                )
            )
        }

        val summary = processor.syncPendingForCurrentUser()

        assertEquals(2, summary.succeeded)
        assertEquals(99, capturedCategoryId)
        assertEquals(0, db.pendingOperationDao().countActive(7))
        assertEquals("Snacks", db.categoryDao().getById(7, 99)?.name)
        assertEquals(null, db.categoryDao().getById(7, catLocal))
        assertEquals(99, db.productDao().getById(7, 55)?.categoryId)
    }

    @Test
    fun productWithTempCategoryIsDeferredUntilCategorySynced() = runBlocking {
        val catLocal = -11
        val prodLocal = -21
        // Only product pending — category still pending but will fail network so product defers
        db.pendingOperationDao().upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = 7,
                operationType = PendingOpType.CREATE,
                entityType = PendingEntityType.CATEGORY,
                localEntityId = catLocal.toString(),
                remoteEntityId = null,
                payloadJson = CategoryWritePayload.toJson(CategoryWritePayload("Temp")),
                status = PendingOpStatus.PENDING,
                retryCount = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now
            )
        )
        db.pendingOperationDao().upsert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                userId = 7,
                operationType = PendingOpType.CREATE,
                entityType = PendingEntityType.PRODUCT,
                localEntityId = prodLocal.toString(),
                remoteEntityId = null,
                payloadJson = ProductWritePayload.toJson(
                    ProductWritePayload(
                        name = "X", sku = null, costPrice = 1.0, sellingPrice = 2.0,
                        stockLevel = 1, minStockLevel = 1, categoryId = catLocal
                    )
                ),
                status = PendingOpStatus.PENDING,
                retryCount = 0,
                lastError = null,
                createdAt = now + 1,
                updatedAt = now + 1
            )
        )
        coEvery { categoryApi.createCategory(any(), any()) } throws IOException("offline")

        val summary = processor.syncPendingForCurrentUser()

        // Category deferred on network (batch stops); product remains pending
        assertTrue(summary.deferredNetwork >= 1)
        assertEquals(2, db.pendingOperationDao().countActive(7))
    }
}
