package com.odirilemasemola.stockflow.ui.employees

import com.odirilemasemola.stockflow.StockFlowApp
import com.odirilemasemola.stockflow.data.local.SessionStore
import com.odirilemasemola.stockflow.data.remote.EmployeeApi
import com.odirilemasemola.stockflow.data.remote.EmployeeDto
import com.odirilemasemola.stockflow.data.repository.EmployeeRepository
import com.odirilemasemola.stockflow.data.repository.EmployeeRepository.EmployeeError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = StockFlowApp::class, sdk = [34])
class EmployeeRepositoryTest {

    private lateinit var api: EmployeeApi
    private lateinit var repository: EmployeeRepository

    @Before
    fun setUp() {
        api = mockk()
        val session = mockk<SessionStore>()
        every { session.getToken() } returns "token"
        repository = EmployeeRepository(api = api, sessionStore = session)
    }

    private fun json(body: String) = body.toResponseBody("application/json".toMediaType())

    @Test
    fun listsEmployees() = runBlocking {
        val staff = EmployeeDto(2, "Sipho Mokoena", "sipho", "sipho@test.local", "Staff")
        coEvery { api.getEmployees("Bearer token") } returns Response.success(listOf(staff))
        assertEquals(listOf(staff), repository.getEmployees().getOrThrow())
    }

    @Test
    fun forbiddenIsReportedAsNotOwner() = runBlocking {
        coEvery { api.getEmployees(any()) } returns Response.error(403, json("""{"error":"x"}"""))
        val error = repository.getEmployees().exceptionOrNull() as EmployeeError
        assertEquals(EmployeeError.Kind.NOT_OWNER, error.kind)
    }

    @Test
    fun networkFailureIsReportedAsOffline() = runBlocking {
        coEvery { api.addEmployee(any(), any()) } throws IOException("offline")
        val error = repository.addEmployee("a@b.co", "Staff").exceptionOrNull() as EmployeeError
        assertEquals(EmployeeError.Kind.OFFLINE, error.kind)
    }

    @Test
    fun serverValidationMessageIsShown() = runBlocking {
        coEvery { api.addEmployee(any(), any()) } returns
            Response.error(409, json("""{"error":"This account already works for another shop"}"""))
        val error = repository.addEmployee("a@b.co", "Staff").exceptionOrNull() as EmployeeError
        assertEquals(EmployeeError.Kind.VALIDATION, error.kind)
        assertTrue(error.message!!.contains("another shop"))
    }

    @Test
    fun removeSucceedsOnNoContent() = runBlocking {
        coEvery { api.removeEmployee(any(), 7) } returns Response.success(204, Unit)
        assertTrue(repository.removeEmployee(7).isSuccess)
    }
}
