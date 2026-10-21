package com.odirilemasemola.stockflow.ui.employees

import android.content.Context
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ApplicationProvider
import com.odirilemasemola.stockflow.R
import com.odirilemasemola.stockflow.StockFlowApp
import com.odirilemasemola.stockflow.data.remote.EmployeeDto
import com.odirilemasemola.stockflow.databinding.ActivityAddEmployeeBinding
import com.odirilemasemola.stockflow.databinding.ActivityEmployeesBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Inflates the Employees screens with sample data on small/large and light/dark configurations,
 * checks every view fits, and writes PNG previews to app/build/screenshots for visual review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = StockFlowApp::class, sdk = [34])
class EmployeesLayoutRenderTest {

    private val sample = listOf(
        EmployeeDto(2, "Sipho Thabo Mokoena", "sipho", "sipho.mokoena@example.co.za", "Staff"),
        EmployeeDto(3, "Lerato Dlamini", "lerato", "lerato@example.com", "Supplier"),
        EmployeeDto(4, "A very long employee name that should ellipsize", "long", "a.very.long.email.address@example.com", "Staff")
    )

    private fun themed(): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_StockFlow)

    @Test
    @Config(qualifiers = "w320dp-h568dp-notnight")
    fun listRendersOnSmallLightScreen() = renderList("employees_small_light", 320, 568)

    @Test
    @Config(qualifiers = "w411dp-h891dp-night")
    fun listRendersOnLargeDarkScreen() = renderList("employees_large_dark", 411, 891)

    @Test
    @Config(qualifiers = "zu-w600dp-h960dp-notnight")
    fun listRendersOnTabletInIsiZulu() = renderList("employees_tablet_zu", 600, 960)

    @Test
    @Config(qualifiers = "st-w320dp-h568dp-night")
    fun addFormRendersOnSmallDarkScreenInSesotho() {
        val context = themed()
        val binding = ActivityAddEmployeeBinding.inflate(LayoutInflater.from(context))
        val root = layout(binding.root, context, 320, 568)
        assertTrue(binding.btnSave.width > 0)
        assertTrue(binding.chipStaff.isChecked)
        save(root, "add_employee_small_dark_st")
    }

    private fun renderList(name: String, widthDp: Int, heightDp: Int) {
        val context = themed()
        val binding = ActivityEmployeesBinding.inflate(LayoutInflater.from(context))
        val adapter = EmployeeAdapter(onChangeRole = {}, onRemove = {})
        binding.rvEmployees.layoutManager = LinearLayoutManager(context)
        binding.rvEmployees.adapter = adapter
        adapter.submitList(sample)
        binding.rvEmployees.visibility = View.VISIBLE
        binding.tvCount.visibility = View.VISIBLE
        binding.tvCount.text = context.resources.getQuantityString(R.plurals.employees_count, sample.size, sample.size)

        val root = layout(binding.root, context, widthDp, heightDp)
        assertEquals(sample.size, binding.rvEmployees.childCount)
        // The add button must stay fully on screen next to the subtitle.
        assertTrue(binding.btnAddEmployee.right <= binding.headerBar.width)
        assertTrue(binding.tvSubtitle.width > 0)
        for (i in 0 until binding.rvEmployees.childCount) {
            val item = binding.rvEmployees.getChildAt(i)
            assertTrue(item.width <= binding.rvEmployees.width)
            val role = item.findViewById<View>(R.id.tvRole)
            assertTrue("role chip visible", role.width > 0 && role.right <= item.width)
        }
        save(root, name)
    }

    private fun layout(view: View, context: Context, widthDp: Int, heightDp: Int): View {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
        // RecyclerView lays out its children on the next pass.
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
        return view
    }

    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
