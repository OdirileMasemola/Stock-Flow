package com.example.stockflow.models

import kotlinx.serialization.Serializable

/**
 * Body for DELETE /api/users/me.
 * The account is taken from the JWT. This value only confirms the caller meant to delete.
 */
@Serializable
data class DeleteAccountRequest(
    val confirmation: String
)

/**
 * Identifiers written onto a closed account row.
 * The row itself stays because sales.user_id is a required foreign key.
 */
object AccountClosure {
    const val CONFIRMATION = "DELETE"
    const val FULL_NAME = "Deleted user"

    fun username(userId: Int): String = "deleted-$userId"

    fun email(userId: Int): String = "deleted-$userId@users.invalid"

    fun isClosed(userId: Int, username: String, email: String): Boolean =
        username == username(userId) && email == email(userId)
}

/** Personal-data cleanup could not finish. The HTTP layer must not report full success. */
class CleanupIncompleteException(message: String) : RuntimeException(message)
