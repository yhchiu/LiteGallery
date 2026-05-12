package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    @Test
    fun normalizePatternTrimsAndCollapsesStars() {
        assertEquals("", NameMatcher.normalizePattern(""))
        assertEquals("", NameMatcher.normalizePattern("  "))
        assertEquals("", NameMatcher.normalizePattern("*"))
        assertEquals("", NameMatcher.normalizePattern("**"))
        assertEquals("", NameMatcher.normalizePattern("  *  "))
        assertEquals("a*b", NameMatcher.normalizePattern("a**b"))
        assertEquals("*.jpg", NameMatcher.normalizePattern("*.jpg"))
        assertEquals("abc", NameMatcher.normalizePattern("  abc  "))
    }

    @Test
    fun compileReturnsNullForEmptyAndPureWildcard() {
        assertNull(NameMatcher.compile(""))
        assertNull(NameMatcher.compile("   "))
        assertNull(NameMatcher.compile("*"))
        assertNull(NameMatcher.compile("**"))
        assertNull(NameMatcher.compile("***"))
    }

    @Test
    fun containsMatcherIsCaseInsensitive() {
        val matcher = NameMatcher.compile("IMG")!!

        assertTrue(matcher.matches("My_IMG_001.jpg"))
        assertFalse(matcher.matches("photo.jpg"))
    }

    @Test
    fun wildcardHonorsStartAndEndAnchors() {
        assertTrue(NameMatcher.compile("IMG*")!!.matches("IMG_001.jpg"))
        assertFalse(NameMatcher.compile("IMG*")!!.matches("My_IMG.jpg"))

        assertTrue(NameMatcher.compile("*.jpg")!!.matches("foo.jpg"))
        assertTrue(NameMatcher.compile("*.jpg")!!.matches("foo.jpg.backup.jpg"))
        assertFalse(NameMatcher.compile("*.jpg")!!.matches("foo.jpg.bak"))

        assertTrue(NameMatcher.compile("A*B")!!.matches("A-B"))
        assertTrue(NameMatcher.compile("A*B")!!.matches("A-B-B"))
        assertFalse(NameMatcher.compile("A*B")!!.matches("A-X-B-X"))
    }

    @Test
    fun wildcardMatchesOrderedSegments() {
        assertTrue(NameMatcher.compile("A*B*C")!!.matches("AxxBxxC"))
        assertFalse(NameMatcher.compile("A*B*C")!!.matches("BxxAxxC"))
        assertTrue(NameMatcher.compile("A*BC*D")!!.matches("ABCXBCD"))
    }

    @Test
    fun wildcardTreatsRegexCharactersAsLiterals() {
        assertTrue(NameMatcher.compile("file?.[jpg]")!!.matches("my-file?.[jpg]-copy"))
        assertFalse(NameMatcher.compile("file?.[jpg]")!!.matches("fileXajpg"))
        assertTrue(NameMatcher.compile("a\\b*")!!.matches("a\\b-value"))
    }

    @Test
    fun wildcardHandlesShortInputWithoutThrowing() {
        assertFalse(NameMatcher.compile("ABC*")!!.matches("AB"))
    }
}
