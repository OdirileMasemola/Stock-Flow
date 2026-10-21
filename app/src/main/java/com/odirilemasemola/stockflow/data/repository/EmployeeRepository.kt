package com.odirilemasemola.stockflow.data.repository

import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.local.SessionStore
import com.odirilemasemola.stockflow.data.remote.AddEmployeeRequest
import com.odirilemasemola.stockflow.data.remote.ApiErrorResponse
import com.odirilemasemola.stockflow.data.remote.EmployeeApi
import com.odirilemasemola.stockflow.data.remote.EmployeeDto
import com.odirilemasemola.stockflow.data.remote.RetrofitClient
import com.odirilemasemola.stockflow.data.remote.ShopMembershipDto
import com.odirilemasemola.stockflow.data.remote.UpdateEmployeeRoleRequest
import com.odirilemasemola.stockflow.ui.common.AppStrings
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

/**
 * Employee management (online only: it changes other people's accounts, so it is never queued).
 * The server enforces owner-only access; [EmployeeError.NotOwner] lets the UI explain a 403.
 */
class EmployeeRepository(
    private val api: EmployeeApi = RetrofitClient.employeeApi,
    private val sessionStore: SessionStore
) {
    private val gson = Gson()

    class EmployeeError(message: String, val kind: Kind) : Exception(message) {
        enum class Kind { OFFLINE, NOT_OWNER, VALIDATION, OTHER }
    }

    suspend fun getMembership(): Result<ShopMembershipDto> =
        call(R.string.error_unable_load_employees) { api.getMembership(it) }

    suspend fun getEmployees(): Result<List<EmployeeDto>> =
        call(R.string.error_unable_load_employees) { api.getEmployees(it) }

    suspend fun addEmployee(email: String, role: String): Result<EmployeeDto> =
        call(R.string.error_unable_add_employee) { api.addEmployee(it, AddEmployeeRequest(email.trim(), role)) }

    suspend fun updateRole(employeeId: Int, role: String): Result<EmployeeDto> =
        call(R.string.error_unable_update_employee) { api.updateRole(it, employeeId, UpdateEmployeeRoleRequest(role)) }

    suspend fun removeEmployee(employeeId: Int): Result<Unit> {
        return try {
            val response = api.removeEmployee(authHeader(), employeeId)
            if (response.isSuccessful) Result.success(Unit) else Result.failure(httpError(response, R.string.error_unable_remove_employee))
        } catch (_: IOException) {
            Result.failure(offline())
        } catch (e: EmployeeError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(EmployeeError(e.message ?: AppStrings.get(R.string.error_unable_remove_employee), EmployeeError.Kind.OTHER))
        }
    }

    private suspend fun <T> call(fallbackRes: Int, block: suspend (String) -> Response<T>): Result<T> {
        return try {
            val response = block(authHeader())
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(httpError(response, fallbackRes))
            }
        } catch (_: IOException) {
            Result.failure(offline())
        } catch (e: EmployeeError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(EmployeeError(e.message ?: AppStrings.get(fallbackRes), EmployeeError.Kind.OTHER))
        }
    }

    private fun offline() = EmployeeError(AppStrings.get(R.string.employees_offline), EmployeeError.Kind.OFFLINE)

    private fun httpError(response: Response<*>, fallbackRes: Int): EmployeeError {
        if (response.code() == 403) {
            return EmployeeError(AppStrings.get(R.string.employees_owner_only), EmployeeError.Kind.NOT_OWNER)
        }
        val raw = response.errorBody()?.string()
        val apiMessage = try {
            gson.fromJson(raw, ApiErrorResponse::class.java)?.error
        } catch (_: Exception) {
            null
        }
        val kind = if (response.code() in 400..499) EmployeeError.Kind.VALIDATION else EmployeeError.Kind.OTHER
        return EmployeeError(apiMessage?.takeIf { it.isNotBlank() } ?: AppStrings.get(fallbackRes), kind)
    }

    private fun authHeader(): String {
        val token = sessionStore.getToken()
        if (token.isNullOrBlank()) {
            throw EmployeeError(AppStrings.get(R.string.error_not_signed_in), EmployeeError.Kind.OTHER)
        }
        return "Bearer $token"
    }
}
