package com.example.stockflow.ui.dashboard

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.stockflow.data.remote.ReportsDto
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a simple StockFlow business report PDF using the platform [PdfDocument] API.
 */
object ReportsPdfExporter {

    fun export(
        context: Context,
        reports: ReportsDto,
        rangeLabel: String
    ): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(dir, "stockflow_report_$stamp.pdf")

        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val contentWidth = pageWidth - margin * 2

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1B2438.toInt()
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7F8C8D.toInt()
            textSize = 11f
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2C3E50.toInt()
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2C3E50.toInt()
            textSize = 11f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEAEDED.toInt()
            strokeWidth = 1f
        }

        var pageNumber = 1
        var pageInfo = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = pageInfo.canvas
        var y = margin

        fun newPageIfNeeded(needed: Float) {
            if (y + needed <= pageHeight - margin) return
            document.finishPage(pageInfo)
            pageNumber += 1
            pageInfo = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = pageInfo.canvas
            y = margin
        }

        fun drawLine() {
            newPageIfNeeded(16f)
            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
            y += 14f
        }

        fun drawHeading(text: String) {
            newPageIfNeeded(28f)
            y += 8f
            canvas.drawText(text, margin, y, headingPaint)
            y += 16f
        }

        fun drawBody(text: String) {
            val lines = wrapText(text, bodyPaint, contentWidth)
            for (line in lines) {
                newPageIfNeeded(16f)
                canvas.drawText(line, margin, y, bodyPaint)
                y += 15f
            }
        }

        canvas.drawText("StockFlow Business Report", margin, y, titlePaint)
        y += 22f
        canvas.drawText("Date range: $rangeLabel", margin, y, subtitlePaint)
        y += 14f
        canvas.drawText(
            "Generated: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}",
            margin,
            y,
            subtitlePaint
        )
        y += 18f
        drawLine()

        val sales = reports.sales
        drawHeading("Sales")
        drawBody("Total sales: R${"%.2f".format(sales?.totalSales ?: 0.0)}")
        drawBody("Sales count: ${sales?.salesCount ?: 0}")
        drawBody("Average sale: R${"%.2f".format(sales?.averageSaleValue ?: 0.0)}")
        val payments = sales?.byPaymentMethod.orEmpty()
        if (payments.isEmpty()) {
            drawBody("No sales in this range.")
        } else {
            drawBody("Payment breakdown:")
            for (method in payments) {
                drawBody(
                    "• ${method.paymentMethod.orEmpty()} — ${method.salesCount} sales · " +
                        "R${"%.2f".format(method.totalAmount)}"
                )
            }
        }

        val inv = reports.inventory
        drawHeading("Inventory")
        drawBody("Products: ${inv?.totalProducts ?: 0}")
        drawBody("Total stock: ${inv?.totalStockQuantity ?: 0}")
        drawBody("Inventory value: R${"%.2f".format(inv?.inventoryValue ?: 0.0)}")
        drawBody("Low stock: ${inv?.lowStockCount ?: 0}")

        val purchases = reports.purchases
        drawHeading("Purchases")
        drawBody("Purchase orders: ${purchases?.purchaseOrderCount ?: 0}")
        drawBody("Pending: ${purchases?.pendingCount ?: 0}")
        drawBody("Received: ${purchases?.receivedCount ?: 0}")
        drawBody("Purchasing total: R${"%.2f".format(purchases?.purchasingTotal ?: 0.0)}")

        document.finishPage(pageInfo)
        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
        return outFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
