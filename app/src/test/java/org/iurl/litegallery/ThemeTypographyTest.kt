package org.iurl.litegallery

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ThemeTypographyTest {

    @Test
    fun prismThemeOverridesWarmPaperSupportFontFamily() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_LiteGallery_Pack_WarmPaper_NoActionBar)
        activity.setTheme(R.style.Theme_LiteGallery_Pack_Prism_NoActionBar)

        assertEquals("sans-serif", activity.resolveStringAttr(androidx.appcompat.R.attr.fontFamily))
        assertTextAppearanceFontFamily(
            activity,
            com.google.android.material.R.attr.textAppearanceHeadlineLarge,
            "sans-serif",
        )
        assertTextAppearanceFontFamily(
            activity,
            com.google.android.material.R.attr.textAppearanceTitleMedium,
            "sans-serif-medium",
        )
    }

    private fun assertTextAppearanceFontFamily(
        context: Context,
        textAppearanceAttr: Int,
        expectedFontFamily: String,
    ) {
        val styleValue = TypedValue()
        assertTrue(context.theme.resolveAttribute(textAppearanceAttr, styleValue, true))

        val attrs = context.obtainStyledAttributes(
            styleValue.resourceId,
            intArrayOf(androidx.appcompat.R.attr.fontFamily, android.R.attr.fontFamily),
        )
        try {
            assertEquals(expectedFontFamily, attrs.getString(0))
            assertEquals(expectedFontFamily, attrs.getString(1))
        } finally {
            attrs.recycle()
        }
    }

    private fun Context.resolveStringAttr(attr: Int): String? {
        val value = TypedValue()
        if (!theme.resolveAttribute(attr, value, true)) return null
        return value.string?.toString() ?: value.coerceToString()?.toString()
    }
}
