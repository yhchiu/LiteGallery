package org.iurl.litegallery

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LocaleHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun getCurrentLanguage_defaultsToSystem() {
        assertEquals(LocaleHelper.LANGUAGE_SYSTEM, LocaleHelper.getCurrentLanguage(context))
    }

    @Test
    fun setLanguage_persistsSelectedLanguage() {
        LocaleHelper.setLanguage(context, LocaleHelper.LANGUAGE_TRADITIONAL_CHINESE)

        assertEquals(LocaleHelper.LANGUAGE_TRADITIONAL_CHINESE, LocaleHelper.getCurrentLanguage(context))
    }

    @Test
    fun getLanguageDisplayName_mapsKnownAndUnknownLanguageCodes() {
        assertEquals(
            context.getString(R.string.language_english),
            LocaleHelper.getLanguageDisplayName(context, LocaleHelper.LANGUAGE_ENGLISH)
        )
        assertEquals(
            context.getString(R.string.language_traditional_chinese),
            LocaleHelper.getLanguageDisplayName(context, LocaleHelper.LANGUAGE_TRADITIONAL_CHINESE)
        )
        assertEquals(
            context.getString(R.string.language_simplified_chinese),
            LocaleHelper.getLanguageDisplayName(context, LocaleHelper.LANGUAGE_SIMPLIFIED_CHINESE)
        )
        assertEquals(
            context.getString(R.string.language_system_default),
            LocaleHelper.getLanguageDisplayName(context, "unsupported")
        )
    }

    @Test
    fun applyLocale_acceptsAllStoredLanguageValuesWithoutChangingPreference() {
        listOf(
            LocaleHelper.LANGUAGE_SYSTEM,
            LocaleHelper.LANGUAGE_ENGLISH,
            LocaleHelper.LANGUAGE_TRADITIONAL_CHINESE,
            LocaleHelper.LANGUAGE_SIMPLIFIED_CHINESE
        ).forEach { language ->
            LocaleHelper.setLanguage(context, language)

            LocaleHelper.applyLocale(context)

            assertEquals(language, LocaleHelper.getCurrentLanguage(context))
        }
    }

    private fun clearState() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
    }
}
