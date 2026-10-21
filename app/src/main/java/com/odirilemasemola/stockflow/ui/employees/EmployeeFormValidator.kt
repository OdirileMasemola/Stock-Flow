package com.odirilemasemola.stockflow.ui.employees

import androidx.annotation.StringRes
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.remote.EmployeeRoles

/** Client-side checks before calling the server, which validates again. */
object EmployeeFormValidator {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    @StringRes
    fun emailError(raw: String): Int? {
        val email = raw.trim()
        return when {
            email.isEmpty() -> R.string.error_employee_email_required
            email.length > 100 || !EMAIL.matches(email) -> R.string.error_employee_email_invalid
            else -> null
        }
    }

    fun isAssignableRole(role: String): Boolean = role in EmployeeRoles.ASSIGNABLE

    /** Up to two initials for the avatar, e.g. "Sipho Mokoena" -> "SM". */
    fun initials(fullName: String, fallback: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val letters = when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
            parts.size == 1 -> parts.first().take(1)
            else -> fallback.trim().take(1)
        }
        return letters.uppercase().ifEmpty { "?" }
    }
}
