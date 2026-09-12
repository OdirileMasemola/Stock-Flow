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
    suspend fun getSummary(): DashboardSummaryResponse = repository.getSummary()

    suspend fun getReports(range: String?, from: String?, to: String?): ReportsResponse {
        val (start, end, label) = try {
            if (!from.isNullOrBlank() && !to.isNullOrBlank()) {
                resolveCustomReportRange(from, to)
            } else {
                resolveReportRange(range ?: "7d")
            }
        } catch (e: IllegalArgumentException) {
            throw BadRequestException(e.message ?: "Invalid date range")
        }
        return repository.getReports(start, end, label)
    }
}
