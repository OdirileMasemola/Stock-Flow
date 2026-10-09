package com.example.stockflow.services

import com.example.stockflow.models.AccountClosure
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.CleanupIncompleteException
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.repositories.AccountDeletionStore
import com.example.stockflow.repositories.AccountSnapshot
import com.example.stockflow.services.activity.ActivityCleanupResult
import com.example.stockflow.services.activity.ActivityStore
import com.example.stockflow.services.activity.InMemoryActivityStore
import com.example.stockflow.services.storage.ImageFolder
import com.example.stockflow.services.storage.ImageStorage
import com.example.stockflow.services.storage.OwnedImageDeleteResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountDeletionServiceTest {

    @Test
    fun missingConfirmationDoesNotTouchTheAccount() = runBlocking {
        val store = FakeAccountStore()
        store.addUser(1)
        store.salesUserIds += 1
        store.productSkus += "SKU-1"
        val service = service(store)
        assertFailsWith<BadRequestException> {
            service.deleteAccount(userId = 1, confirmation = "no")
        }
        assertFalse(store.rows.getValue(1).closed)
        assertEquals(listOf(1), store.salesUserIds)
        assertEquals(listOf("SKU-1"), store.productSkus)
    }

    @Test
    fun closesOnlyTheAuthenticatedAccountAndKeepsSharedRecords() = runBlocking {
        val store = FakeAccountStore()
        store.addUser(1, profileImageUrl = "/uploads/profiles/a.png", businessId = 9, businessImageUrl = "/uploads/businesses/b.png")
        store.addUser(2)
        store.salesUserIds += 1
        store.productSkus += "SKU-1"
        val images = FakeImages()
        val activity = InMemoryActivityStore()
        activity.write("9", "product_created", "created", 1, 3, "Rice", null)
        activity.write("user-1", "note", "early", 1, null, null, null)
        val service = service(store, images, activity)

        service.deleteAccount(1, AccountClosure.CONFIRMATION)

        val closed = store.rows.getValue(1)
        assertTrue(closed.closed)
        assertEquals(AccountClosure.username(1), closed.username)
        assertEquals(AccountClosure.email(1), closed.email)
        assertEquals(AccountClosure.FULL_NAME, closed.fullName)
        assertTrue(closed.tokens.isEmpty())
        assertEquals(null, closed.businessId)
        assertEquals(null, closed.profileImageUrl)
        assertFalse(store.rows.getValue(2).closed)
        assertEquals(listOf(1), store.salesUserIds)
        assertEquals(listOf("SKU-1"), store.productSkus)
        assertEquals(
            listOf("/uploads/profiles/a.png" to ImageFolder.PROFILES, "/uploads/businesses/b.png" to ImageFolder.BUSINESSES),
            images.deleted
        )
        assertTrue(activity.records.isEmpty())
    }

    @Test
    fun repeatedDeletionIsSafe() = runBlocking {
        val store = FakeAccountStore()
        store.addUser(1)
        val service = service(store)
        service.deleteAccount(1, AccountClosure.CONFIRMATION)
        service.deleteAccount(1, AccountClosure.CONFIRMATION)
        assertEquals(1, store.closeCount)
        assertTrue(store.rows.getValue(1).closed)
    }

    @Test
    fun unknownUserIsNotFound() = runBlocking {
        val service = service(FakeAccountStore())
        assertFailsWith<NotFoundException> {
            service.deleteAccount(4, AccountClosure.CONFIRMATION)
        }
        Unit
    }

    @Test
    fun sharedCatalogImageIsNotDeleted() = runBlocking {
        val store = FakeAccountStore()
        store.addUser(1, profileImageUrl = "/uploads/products/shared.png")
        store.sharedImages += "/uploads/products/shared.png"
        store.productSkus += "SKU-1"
        val images = FakeImages()
        val service = service(store, images)
        service.deleteAccount(1, AccountClosure.CONFIRMATION)
        assertTrue(images.deleted.isEmpty())
        assertEquals(listOf("SKU-1"), store.productSkus)
        assertTrue(store.rows.getValue(1).closed)
    }

    @Test
    fun imageCleanupFailureLeavesTheAccountOpen() = runBlocking {
        val store = FakeAccountStore()
        store.addUser(1, profileImageUrl = "/uploads/profiles/a.png")
        val images = FakeImages().apply { fail = true }
        val service = service(store, images)
        assertFailsWith<CleanupIncompleteException> {
            service.deleteAccount(1, AccountClosure.CONFIRMATION)
        }
        assertEquals(0, store.closeCount)
        assertFalse(store.rows.getValue(1).closed)
    }

    @Test
    fun activityCleanupFailureLeavesTheAccountOpen() = runBlocking {
        val store = FakeAccountStore()
        store.addUser(1, businessId = 3)
        val activity = InMemoryActivityStore().apply { deleteFails = true }
        val service = service(store, activity = activity)
        assertFailsWith<CleanupIncompleteException> {
            service.deleteAccount(1, AccountClosure.CONFIRMATION)
        }
        assertEquals(0, store.closeCount)
        assertFalse(store.rows.getValue(1).closed)
    }

    @Test
    fun retryReportsActivityFailureAfterTheAccountIsClosed() = runBlocking {
        val store = FakeAccountStore()
        store.addUser(1)
        val activity = InMemoryActivityStore()
        val service = service(store, activity = activity)
        service.deleteAccount(1, AccountClosure.CONFIRMATION)
        activity.deleteFails = true
        assertFailsWith<CleanupIncompleteException> {
            service.deleteAccount(1, AccountClosure.CONFIRMATION)
        }
        assertTrue(store.rows.getValue(1).closed)
    }

    private fun service(
        store: FakeAccountStore,
        images: FakeImages = FakeImages(),
        activity: ActivityStore = InMemoryActivityStore()
    ): UserService {
        return UserService(
            imageStorage = ProductImageStorage(images),
            accountDeletionStore = store,
            activityStore = activity
        )
    }

    private class FakeImages : ImageStorage {
        val deleted = mutableListOf<Pair<String, ImageFolder>>()
        var fail: Boolean = false

        override fun saveProductImage(bytes: ByteArray, originalFileName: String?, contentType: String?) = unused()
        override fun saveProfileImage(bytes: ByteArray, originalFileName: String?, contentType: String?) = unused()
        override fun saveBusinessImage(bytes: ByteArray, originalFileName: String?, contentType: String?) = unused()
        override fun save(folder: ImageFolder, bytes: ByteArray, originalFileName: String?, contentType: String?) = unused()
        override fun deleteIfManaged(imageUrl: String?) = Unit
        override fun deleteOwned(imageUrl: String?, folder: ImageFolder): OwnedImageDeleteResult {
            if (fail) return OwnedImageDeleteResult.Failed
            val url = imageUrl?.trim().orEmpty()
            if (url.isEmpty()) return OwnedImageDeleteResult.Skipped
            deleted += url to folder
            return OwnedImageDeleteResult.Deleted
        }

        private fun unused(): String = error("unused")
    }

    private class Row(
        var username: String,
        var email: String,
        var fullName: String,
        var profileImageUrl: String?,
        var businessId: Int?,
        var businessImageUrl: String?,
        val tokens: MutableList<String>
    ) {
        val closed: Boolean get() = username.startsWith("deleted-")
    }

    private class FakeAccountStore : AccountDeletionStore {
        val rows = mutableMapOf<Int, Row>()
        val salesUserIds = mutableListOf<Int>()
        val productSkus = mutableListOf<String>()
        val sharedImages = mutableSetOf<String>()
        var closeCount = 0

        fun addUser(
            id: Int,
            profileImageUrl: String? = null,
            businessId: Int? = null,
            businessImageUrl: String? = null
        ) {
            rows[id] = Row(
                username = "user$id",
                email = "user$id@shop.test",
                fullName = "User $id",
                profileImageUrl = profileImageUrl,
                businessId = businessId,
                businessImageUrl = businessImageUrl,
                tokens = mutableListOf("token-$id")
            )
        }

        override suspend fun load(userId: Int): AccountSnapshot? {
            val row = rows[userId] ?: return null
            return AccountSnapshot(
                id = userId,
                closed = AccountClosure.isClosed(userId, row.username, row.email),
                profileImageUrl = row.profileImageUrl,
                businessId = row.businessId,
                businessImageUrl = row.businessImageUrl
            )
        }

        override suspend fun close(userId: Int) {
            val row = rows.getValue(userId)
            closeCount += 1
            row.username = AccountClosure.username(userId)
            row.email = AccountClosure.email(userId)
            row.fullName = AccountClosure.FULL_NAME
            row.profileImageUrl = null
            row.businessId = null
            row.businessImageUrl = null
            row.tokens.clear()
        }

        override suspend fun imageReferencedElsewhere(url: String, userId: Int): Boolean = url in sharedImages
    }
}
