package com.odirilemasemola.stockflow.ui.employees

import com.odirilemasemola.stockflow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployeeFormValidatorTest {

    @Test
    fun emailIsRequiredAndValidated() {
        assertEquals(R.string.error_employee_email_required, EmployeeFormValidator.emailError("   "))
        assertEquals(R.string.error_employee_email_invalid, EmployeeFormValidator.emailError("sipho"))
        assertEquals(R.string.error_employee_email_invalid, EmployeeFormValidator.emailError("sipho@shop"))
        assertEquals(R.string.error_employee_email_invalid, EmployeeFormValidator.emailError("a".repeat(95) + "@x.com"))
        assertNull(EmployeeFormValidator.emailError("  sipho@shop.co.za "))
    }

    @Test
    fun onlyStaffAndSupplierAreAssignable() {
        assertTrue(EmployeeFormValidator.isAssignableRole("Staff"))
        assertTrue(EmployeeFormValidator.isAssignableRole("Supplier"))
        assertFalse(EmployeeFormValidator.isAssignableRole("Owner"))
        assertFalse(EmployeeFormValidator.isAssignableRole(""))
    }

    @Test
    fun initialsUseFirstAndLastName() {
        assertEquals("SM", EmployeeFormValidator.initials("Sipho Thabo Mokoena", "sipho"))
        assertEquals("L", EmployeeFormValidator.initials("lerato", "x"))
        assertEquals("U", EmployeeFormValidator.initials("  ", "user7"))
        assertEquals("?", EmployeeFormValidator.initials("", ""))
    }
}
