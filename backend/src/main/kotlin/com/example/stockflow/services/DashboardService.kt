package com.example.stockflow.services

import com.example.stockflow.models.DashboardSummaryResponse
import com.example.stockflow.models.ReportsResponse
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.repositories.DashboardRepository
import com.example.stockflow.repositories.DashboardRepositoryImpl
import com.example.stockflow.repositories.resolveCustomReportRange
import com.example.stockflow.repositories.resolveReportRange

class DashboardService(
    private val repository: DashboardRepository = DashboardRepositoryImpl()
) {
    suspend fun getSummary(ownerUserId: Int): DashboardSummaryResponse = repository.getSummary(ownerUserId)

    suspend fun getReports(ownerUserId: Int, range: String?, from: String?, to: String?): ReportsResponse {
        val (start, end, label) = try {
            if (!from.isNullOrBlank() && !to.isNullOrBlank()) {
                resolveCustomReportRange(from, to)
            } else {
                resolveReportRange(range ?: "7d")
            }
        } catch (e: IllegalArgumentException) {
            throw BadRequestException(e.message ?: "Invalid date range")
        }
        return repository.getReports(ownerUserId, start, end, label)
    }
}
