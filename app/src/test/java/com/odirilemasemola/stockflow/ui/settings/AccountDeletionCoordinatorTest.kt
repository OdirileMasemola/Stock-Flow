package com.odirilemasemola.stockflow.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionCoordinatorTest {

    @Test
    fun cancellationDoesNotCallTheServerOrClearLocalData() = runBlocking {
        var serverCalls = 0
        var cleared = false
        val coordinator = AccountDeletionCoordinator(
            requestDeletion = {
                serverCalls += 1
                Result.success(Unit)
            },
            onConfirmedDeletion = { cleared = true }
        )

        val result = coordinator.onConfirmationResult(confirmed = false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AccountDeletionCancelled)
        assertEquals(0, serverCalls)
        assertFalse(cleared)
    }

    @Test
    fun successClearsLocalDataOnce() = runBlocking {
        var cleared = 0
        val coordinator = AccountDeletionCoordinator(
            requestDeletion = { Result.success(Unit) },
            onConfirmedDeletion = { cleared += 1 }
        )

        val result = coordinator.onConfirmationResult(confirmed = true)

        assertTrue(result.isSuccess)
        assertEquals(1, cleared)
    }

    @Test
    fun apiFailureDoesNotClearLocalData() = runBlocking {
        var cleared = false
        val coordinator = AccountDeletionCoordinator(
            requestDeletion = { Result.failure(IllegalStateException("server rejected")) },
            onConfirmedDeletion = { cleared = true }
        )

        val result = coordinator.onConfirmationResult(confirmed = true)

        assertTrue(result.isFailure)
        assertEquals("server rejected", result.exceptionOrNull()?.message)
        assertFalse(cleared)
    }

    @Test
    fun secondRequestWaitsUntilTheFirstFinishes() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var serverCalls = 0
        val coordinator = AccountDeletionCoordinator(
            requestDeletion = {
                serverCalls += 1
                started.complete(Unit)
                release.await()
                Result.success(Unit)
            },
            onConfirmedDeletion = { }
        )

        val first = async { coordinator.onConfirmationResult(confirmed = true) }
        started.await()
        val second = coordinator.onConfirmationResult(confirmed = true)
        assertTrue(second.isFailure)
        release.complete(Unit)
        assertTrue(first.await().isSuccess)
        assertEquals(1, serverCalls)
    }
}
