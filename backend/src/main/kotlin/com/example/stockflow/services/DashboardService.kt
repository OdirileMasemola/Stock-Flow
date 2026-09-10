package com.example.stockflow.services

import com.example.stockflow.models.DashboardSummaryResponse
import com.example.stockflow.models.ReportsResponse
import com.example.stockflow.repositories.DashboardRepository
import com.example.stockflow.repositories.DashboardRepositoryImpl
import com.example.stockflow.repositories.resolveReportRange

class DashboardService(
    private val repository: DashboardRepository = DashboardRepositoryImpl()
) {
    suspend fun getSummary(): DashboardSummaryResponse = repository.getSummary()

    suspend fun getReports(range: String?): ReportsResponse {
        val (start, end, label) = resolveReportRange(range ?: "7d")
        return repository.getReports(start, end, label)
    }
}
