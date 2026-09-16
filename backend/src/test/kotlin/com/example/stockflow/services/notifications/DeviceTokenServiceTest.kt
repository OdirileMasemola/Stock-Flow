package com.example.stockflow.services.notifications

import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.RegisterDeviceTokenRequest
import com.example.stockflow.models.UnregisterDeviceTokenRequest
import com.example.stockflow.repositories.DeviceTokenRepository
import com.example.stockflow.repositories.StoredDeviceToken
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeviceTokenServiceTest {

    private class FakeRepo : DeviceTokenRepository {
        val tokens = mutableMapOf<String, StoredDeviceToken>()
        var nextId = 1

        override suspend fun upsert(userId: Int, token: String, platform: String): StoredDeviceToken {
            val existing = tokens[token]
            val stored = StoredDeviceToken(
                id = existing?.id ?: nextId++,
                userId = userId,
                token = token,
                platform = platform,
                active = true
            )
            tokens[token] = stored
            return stored
        }

        override suspend fun deactivate(userId: Int, token: String): Boolean {
            val existing = tokens[token] ?: return false
            if (existing.userId != userId) return false
            tokens[token] = existing.copy(active = false)
            return true
        }

        override suspend fun delete(userId: Int, token: String): Boolean {
            val existing = tokens[token] ?: return false
            if (existing.userId != userId) return false
            tokens.remove(token)
            return true
        }

        override suspend fun markInactiveByToken(token: String) {
            tokens[token]?.let { tokens[token] = it.copy(active = false) }
        }

        override suspend fun findActiveTokensForUserIds(userIds: Collection<Int>): List<StoredDeviceToken> =
            tokens.values.filter { it.active && it.userId in userIds }

        override suspend fun resolveAlertRecipientUserIds(actingUserId: Int): List<Int> =
            listOfNotNull(actingUserId.takeIf { it > 0 }, 1).distinct()
    }

    @Test
    fun registerRequiresAuthUserId() {
        runBlocking {
            val service = DeviceTokenService(FakeRepo())
            assertFailsWith<BadRequestException> {
                service.register(0, RegisterDeviceTokenRequest(fcmToken = "abc"))
            }
        }
    }

    @Test
    fun registerRejectsBlankToken() {
        runBlocking {
            val service = DeviceTokenService(FakeRepo())
            assertFailsWith<BadRequestException> {
                service.register(7, RegisterDeviceTokenRequest(fcmToken = "  "))
            }
        }
    }

    @Test
    fun upsertReassignsTokenToNewUser() {
        runBlocking {
            val repo = FakeRepo()
            val service = DeviceTokenService(repo)
            service.register(1, RegisterDeviceTokenRequest(fcmToken = "tok-a", platform = "android"))
            val second = service.register(2, RegisterDeviceTokenRequest(fcmToken = "tok-a", platform = "android"))
            assertEquals(true, second.active)
            assertEquals(2, repo.tokens["tok-a"]!!.userId)
            assertEquals(1, repo.tokens.size)
        }
    }

    @Test
    fun multipleDevicesPerUserAllowed() {
        runBlocking {
            val repo = FakeRepo()
            val service = DeviceTokenService(repo)
            service.register(9, RegisterDeviceTokenRequest(fcmToken = "phone"))
            service.register(9, RegisterDeviceTokenRequest(fcmToken = "tablet"))
            assertEquals(2, repo.tokens.size)
            assertTrue(repo.tokens.values.all { it.userId == 9 })
        }
    }

    @Test
    fun unregisterDeactivatesOwnedTokenOnly() {
        runBlocking {
            val repo = FakeRepo()
            val service = DeviceTokenService(repo)
            service.register(3, RegisterDeviceTokenRequest(fcmToken = "mine"))
            service.register(4, RegisterDeviceTokenRequest(fcmToken = "theirs"))
            service.unregister(3, UnregisterDeviceTokenRequest(fcmToken = "mine"))
            assertEquals(false, repo.tokens["mine"]!!.active)
            assertEquals(true, repo.tokens["theirs"]!!.active)
            service.unregister(3, UnregisterDeviceTokenRequest(fcmToken = "theirs"))
            assertEquals(true, repo.tokens["theirs"]!!.active)
        }
    }
}
