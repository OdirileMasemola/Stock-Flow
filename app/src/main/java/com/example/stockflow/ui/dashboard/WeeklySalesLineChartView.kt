package com.example.stockflow.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.stockflow.R
import kotlin.math.max

/**
 * Minimal area line chart inspired by 21st.dev analytics cards
 * (soft gradient fill under a smooth primary stroke).
 */
class WeeklySalesLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    private val linePath = Path()
    private val areaPath = Path()
    private var values: FloatArray = FloatArray(0)
    private var primaryColor: Int = ContextCompat.getColor(context, R.color.icon_sales)
    private var gridColor: Int = ContextCompat.getColor(context, R.color.divider_color)
    private var surfaceColor: Int = ContextCompat.getColor(context, R.color.surface)

    fun setValues(amounts: List<Double>) {
        values = FloatArray(amounts.size) { i -> amounts[i].toFloat().coerceAtLeast(0f) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty() || width == 0 || height == 0) return

        val density = resources.displayMetrics.density
        val padH = 8f * density
        val padTop = 12f * density
        val padBottom = 10f * density
        val chartLeft = padH
        val chartRight = width - padH
        val chartTop = padTop
        val chartBottom = height - padBottom
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop
        if (chartWidth <= 0f || chartHeight <= 0f) return

        gridPaint.color = gridColor
        for (i in 1..3) {
            val y = chartTop + chartHeight * (i / 4f)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
        }

        val maxValue = max(values.maxOrNull() ?: 0f, 0.01f)
        val count = values.size
        val stepX = if (count == 1) 0f else chartWidth / (count - 1)

        fun xAt(index: Int): Float = chartLeft + stepX * index
        fun yAt(value: Float): Float {
            val normalized = value / maxValue
            // Keep a little headroom so points aren't clipped.
            return chartBottom - (normalized * chartHeight * 0.88f)
        }

        linePath.reset()
        areaPath.reset()

        if (count == 1) {
            val x = (chartLeft + chartRight) / 2f
            val y = yAt(values[0])
            linePath.moveTo(chartLeft, y)
            linePath.lineTo(chartRight, y)
            areaPath.moveTo(chartLeft, chartBottom)
            areaPath.lineTo(chartLeft, y)
            areaPath.lineTo(chartRight, y)
            areaPath.lineTo(chartRight, chartBottom)
            areaPath.close()
        } else {
            val pointsX = FloatArray(count) { xAt(it) }
            val pointsY = FloatArray(count) { yAt(values[it]) }

            linePath.moveTo(pointsX[0], pointsY[0])
            for (i in 0 until count - 1) {
                val x1 = pointsX[i]
                val y1 = pointsY[i]
                val x2 = pointsX[i + 1]
                val y2 = pointsY[i + 1]
                val cx = (x1 + x2) / 2f
                linePath.cubicTo(cx, y1, cx, y2, x2, y2)
            }

            areaPath.moveTo(pointsX[0], chartBottom)
            areaPath.lineTo(pointsX[0], pointsY[0])
            for (i in 0 until count - 1) {
                val x1 = pointsX[i]
                val y1 = pointsY[i]
                val x2 = pointsX[i + 1]
                val y2 = pointsY[i + 1]
                val cx = (x1 + x2) / 2f
                areaPath.cubicTo(cx, y1, cx, y2, x2, y2)
            }
            areaPath.lineTo(pointsX[count - 1], chartBottom)
            areaPath.close()
        }

        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)
        areaPaint.shader = LinearGradient(
            0f,
            chartTop,
            0f,
            chartBottom,
            intArrayOf(Color.argb(0x55, r, g, b), Color.argb(0x08, r, g, b)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(areaPath, areaPaint)
        areaPaint.shader = null

        linePaint.color = primaryColor
        linePaint.strokeWidth = 2.5f * density
        canvas.drawPath(linePath, linePaint)

        pointPaint.color = primaryColor
        pointStrokePaint.color = surfaceColor
        val pointRadius = 3.5f * density
        for (i in values.indices) {
            val x = if (count == 1) (chartLeft + chartRight) / 2f else xAt(i)
            val y = yAt(values[i])
            canvas.drawCircle(x, y, pointRadius + density, pointStrokePaint)
            canvas.drawCircle(x, y, pointRadius, pointPaint)
        }
    }
}
