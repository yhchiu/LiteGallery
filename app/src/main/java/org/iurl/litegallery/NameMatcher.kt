package org.iurl.litegallery

sealed class NameMatcher {
    abstract fun matches(input: String): Boolean

    class Contains(private val needle: String) : NameMatcher() {
        override fun matches(input: String): Boolean =
            input.contains(needle, ignoreCase = true)
    }

    class Wildcard(
        private val segments: Array<String>,
        private val anchorStart: Boolean,
        private val anchorEnd: Boolean
    ) : NameMatcher() {
        override fun matches(input: String): Boolean {
            if (segments.isEmpty()) return true

            val lastIndex = segments.size - 1
            var cursor = 0
            for (index in segments.indices) {
                val segment = segments[index]
                when {
                    index == 0 && anchorStart -> {
                        if (input.length < segment.length) return false
                        if (!input.regionMatches(0, segment, 0, segment.length, ignoreCase = true)) {
                            return false
                        }
                        cursor = segment.length
                    }
                    index == lastIndex && anchorEnd -> {
                        val position = input.length - segment.length
                        if (position < cursor) return false
                        if (!input.regionMatches(position, segment, 0, segment.length, ignoreCase = true)) {
                            return false
                        }
                        cursor = input.length
                    }
                    else -> {
                        val foundAt = input.indexOf(segment, startIndex = cursor, ignoreCase = true)
                        if (foundAt < 0) return false
                        cursor = foundAt + segment.length
                    }
                }
            }
            return true
        }
    }

    companion object {
        private val STAR_COLLAPSE = Regex("\\*+")

        fun normalizePattern(pattern: String): String {
            val trimmed = pattern.trim()
            if (trimmed.isEmpty()) return ""
            if ('*' !in trimmed) return trimmed

            val collapsed = trimmed.replace(STAR_COLLAPSE, "*")
            return if (collapsed == "*") "" else collapsed
        }

        fun compile(pattern: String): NameMatcher? {
            val normalized = normalizePattern(pattern)
            if (normalized.isEmpty()) return null
            if ('*' !in normalized) return Contains(normalized)

            val segments = normalized.split('*')
                .filter { it.isNotEmpty() }
                .toTypedArray()
            return Wildcard(
                segments = segments,
                anchorStart = !normalized.startsWith('*'),
                anchorEnd = !normalized.endsWith('*')
            )
        }
    }
}
