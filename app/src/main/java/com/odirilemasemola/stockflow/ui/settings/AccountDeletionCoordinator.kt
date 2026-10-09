package com.odirilemasemola.stockflow.ui.settings

/**
 * Runs account deletion only after the Settings confirmation dialog is accepted.
 * Local session data is cleared only when the server reports success.
 */
class AccountDeletionCoordinator(
    private val requestDeletion: suspend () -> Result<Unit>,
    private val onConfirmedDeletion: () -> Unit
) {
    private var inProgress = false

    suspend fun onConfirmationResult(confirmed: Boolean): Result<Unit> {
        if (!confirmed) {
            return Result.failure(AccountDeletionCancelled())
        }
        if (inProgress) {
            return Result.failure(IllegalStateException("Account deletion is already in progress"))
        }
        inProgress = true
        return try {
            val result = requestDeletion()
            if (result.isSuccess) {
                onConfirmedDeletion()
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inProgress = false
        }
    }
}

class AccountDeletionCancelled : Exception("Account deletion was cancelled")
