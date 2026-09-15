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
import com.example.stockflow.R

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

        canvas.drawText(context.getString(R.string.pdf_report_title), margin, y, titlePaint)
        y += 22f
        canvas.drawText(context.getString(R.string.pdf_date_range, rangeLabel), margin, y, subtitlePaint)
        y += 14f
        val generatedAt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText(
            context.getString(R.string.pdf_generated, generatedAt),
            margin,
            y,
            subtitlePaint
        )
        y += 18f
        drawLine()

        val sales = reports.sales
        drawHeading(context.getString(R.string.pdf_section_sales))
        drawBody(context.getString(R.string.report_sales_total, sales?.totalSales ?: 0.0))
        drawBody(context.getString(R.string.report_sales_count, sales?.salesCount ?: 0))
        drawBody(context.getString(R.string.report_sales_average, sales?.averageSaleValue ?: 0.0))
        val payments = sales?.byPaymentMethod.orEmpty()
        if (payments.isEmpty()) {
            drawBody(context.getString(R.string.report_payment_none))
        } else {
            drawBody(context.getString(R.string.pdf_payment_breakdown))
            for (method in payments) {
                drawBody(
                    "• " + context.getString(
                        R.string.report_payment_line,
                        method.paymentMethod.orEmpty(),
                        method.salesCount,
                        method.totalAmount
                    )
                )
            }
        }

        val inv = reports.inventory
        drawHeading(context.getString(R.string.pdf_section_inventory))
        drawBody(context.getString(R.string.report_inv_products, inv?.totalProducts ?: 0))
        drawBody(context.getString(R.string.report_inv_stock, inv?.totalStockQuantity ?: 0))
        drawBody(context.getString(R.string.report_inv_value, inv?.inventoryValue ?: 0.0))
        drawBody(context.getString(R.string.report_inv_low, inv?.lowStockCount ?: 0))

        val purchases = reports.purchases
        drawHeading(context.getString(R.string.pdf_section_purchases))
        drawBody(context.getString(R.string.report_po_count, purchases?.purchaseOrderCount ?: 0))
        drawBody(context.getString(R.string.report_po_pending, purchases?.pendingCount ?: 0))
        drawBody(context.getString(R.string.report_po_received, purchases?.receivedCount ?: 0))
        drawBody(context.getString(R.string.report_po_total, purchases?.purchasingTotal ?: 0.0))

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
