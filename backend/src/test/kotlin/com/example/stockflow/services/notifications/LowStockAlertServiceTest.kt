package com.example.stockflow.services.notifications

import com.example.stockflow.models.LowStockCrossing
import com.example.stockflow.repositories.DeviceTokenRepository
import com.example.stockflow.repositories.StoredDeviceToken
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LowStockAlertServiceTest {

    private class FakeRepo : DeviceTokenRepository {
        val tokens = mutableListOf<StoredDeviceToken>()
        val deactivated = mutableListOf<String>()

        override suspend fun upsert(userId: Int, token: String, platform: String) =
            error("not used")

        override suspend fun deactivate(userId: Int, token: String) = false
        override suspend fun delete(userId: Int, token: String) = false

        override suspend fun markInactiveByToken(token: String) {
            deactivated += token
        }

        override suspend fun findActiveTokensForUserIds(userIds: Collection<Int>): List<StoredDeviceToken> =
            tokens.filter { it.active && it.userId in userIds }

        override suspend fun resolveAlertRecipientUserIds(actingUserId: Int): List<Int> =
            listOf(1, actingUserId).distinct()
    }

    private class RecordingSender : FcmSender {
        data class Call(val token: String, val title: String, val body: String, val data: Map<String, String>)
        val calls = mutableListOf<Call>()
        var result: FcmSendResult = FcmSendResult.SUCCESS

        override fun send(
            token: String,
            title: String,
            body: String,
            data: Map<String, String>
        ): FcmSendResult {
            calls += Call(token, title, body, data)
            return result
        }
    }

    @Test
    fun onlyNotifiesWhenCrossingIntoLowStock() {
        runBlocking {
            val repo = FakeRepo().apply {
                tokens += StoredDeviceToken(1, 1, "t1", "android", true)
            }
            val sender = RecordingSender()
            val service = LowStockAlertService(repo, sender)

            service.notifyCrossings(
                actingUserId = 5,
                crossings = listOf(
                    LowStockCrossing(10, "Milk", previousStock = 3, currentStock = 2, minStockLevel = 5)
                )
            )
            assertTrue(sender.calls.isEmpty())

            service.notifyCrossings(
                actingUserId = 5,
                crossings = listOf(
                    LowStockCrossing(10, "Milk", previousStock = 6, currentStock = 4, minStockLevel = 5)
                )
            )
            assertEquals(1, sender.calls.size)
            assertEquals("t1", sender.calls[0].token)
            assertTrue(sender.calls[0].body.contains("Milk"))
            assertEquals("low_stock", sender.calls[0].data["type"])
        }
    }

    @Test
    fun invalidTokenIsDeactivated() {
        runBlocking {
            val repo = FakeRepo().apply {
                tokens += StoredDeviceToken(1, 1, "bad", "android", true)
            }
            val sender = RecordingSender().apply { result = FcmSendResult.INVALID_TOKEN }
            val service = LowStockAlertService(repo, sender)
            service.notifyCrossings(
                1,
                listOf(LowStockCrossing(1, "Bread", 8, 2, 5))
            )
            assertEquals(listOf("bad"), repo.deactivated)
        }
    }

    @Test
    fun userIsolationRecipientsOnly() {
        runBlocking {
            val repo = FakeRepo().apply {
                tokens += StoredDeviceToken(1, 1, "owner", "android", true)
                tokens += StoredDeviceToken(2, 99, "other", "android", true)
            }
            val sender = RecordingSender()
            val service = LowStockAlertService(repo, sender)
            service.notifyCrossings(
                actingUserId = 1,
                crossings = listOf(LowStockCrossing(1, "Soap", 10, 1, 5))
            )
            assertEquals(listOf("owner"), sender.calls.map { it.token })
        }
    }
}
