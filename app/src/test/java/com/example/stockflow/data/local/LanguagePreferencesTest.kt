package com.example.stockflow.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for language-tag helpers.
 * SharedPreferences persist/load needs an Android runtime (instrumented/Robolectric);
 * Stage 1 verifies tag normalisation and supported-tag rules here.
 */
class LanguagePreferencesTest {

    @Test
    fun defaultTagIsEnglish() {
        assertEquals(LanguagePreferences.TAG_ENGLISH, LanguagePreferences.DEFAULT_LANGUAGE_TAG)
    }

    @Test
    fun supportedTagsIncludeEnZuSt() {
        assertTrue(LanguagePreferences.isSupported("en"))
        assertTrue(LanguagePreferences.isSupported("zu"))
        assertTrue(LanguagePreferences.isSupported("st"))
        assertFalse(LanguagePreferences.isSupported("tn")) // Setswana not yet supported
        assertFalse(LanguagePreferences.isSupported("fr"))
    }

    @Test
    fun normalizeAcceptsBcp47AndFallsBack() {
        assertEquals("zu", LanguagePreferences.normalizeTag("zu-ZA"))
        assertEquals("st", LanguagePreferences.normalizeTag("ST_ZA"))
        assertEquals("en", LanguagePreferences.normalizeTag("en"))
        assertEquals("en", LanguagePreferences.normalizeTag(""))
        assertEquals("en", LanguagePreferences.normalizeTag("tn"))
        assertEquals("zu", LanguagePreferences.normalizeTag("  Zu  "))
    }

    @Test
    fun supportedTagsSetMatchesStage1() {
        assertEquals(
            setOf(
                LanguagePreferences.TAG_ENGLISH,
                LanguagePreferences.TAG_ISIZULU,
                LanguagePreferences.TAG_SESOTHO
            ),
            LanguagePreferences.SUPPORTED_TAGS
        )
    }
}
