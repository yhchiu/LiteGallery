package org.iurl.litegallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceThemeOverlayTest {

    @Test
    fun themeOverridesLegacyAccentToPrimaryColor() {
        val themesXml = findProjectFile("app/src/main/res/values/themes.xml").readText()
        assertTrue(
            themesXml.contains(
                "<item name=\"colorAccent\">?attr/colorPrimary</item>"
            )
        )
        assertTrue(
            themesXml.contains(
                "<item name=\"android:colorAccent\">?attr/colorPrimary</item>"
            )
        )
        assertTrue(
            themesXml.contains(
                "<item name=\"colorControlActivated\">?attr/colorPrimary</item>"
            )
        )
    }

    @Test
    fun preferenceThemeOverlaySetsPreferenceCategoryTitlesToPrimaryColor() {
        val themesXml = findProjectFile("app/src/main/res/values/themes.xml").readText()
        assertTrue(
            themesXml.contains(
                "<item name=\"preferenceCategoryTitleTextColor\">?attr/colorPrimary</item>"
            )
        )
    }

    private fun findProjectFile(relativePath: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDir)) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath from $userDir")
    }
}
