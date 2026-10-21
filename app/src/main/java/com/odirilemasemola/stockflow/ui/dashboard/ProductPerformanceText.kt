package com.odirilemasemola.stockflow.ui.dashboard

import android.content.Context
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.data.remote.ProductPerformanceItemDto

/** Shared text for the best-selling / slow-moving lists on screen and in the PDF export. */
object ProductPerformanceText {

    fun bestSellingLines(context: Context, items: List<ProductPerformanceItemDto>?): List<String> {
        val list = items.orEmpty()
        if (list.isEmpty()) return listOf(context.getString(R.string.report_best_selling_none))
        return list.mapIndexed { index, item ->
            context.getString(
                R.string.report_best_selling_line,
                index + 1,
                displayName(context, item),
                item.quantitySold,
                item.revenue
            )
        }
    }

    fun slowMovingLines(context: Context, items: List<ProductPerformanceItemDto>?): List<String> {
        val list = items.orEmpty()
        if (list.isEmpty()) return listOf(context.getString(R.string.report_slow_moving_none))
        return list.mapIndexed { index, item ->
            context.getString(
                R.string.report_slow_moving_line,
                index + 1,
                displayName(context, item),
                item.quantitySold,
                item.stockLevel
            )
        }
    }

    private fun displayName(context: Context, item: ProductPerformanceItemDto): String =
        item.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.product_fallback, item.productId)
}
