package org.iurl.litegallery

data class FastScrollSection(
    val adapterPosition: Int,
    val title: String
)

object FastScrollSectionIndex {
    fun titleForPosition(sections: List<FastScrollSection>, position: Int): String? {
        if (position < 0 || sections.isEmpty()) return null

        var low = 0
        var high = sections.lastIndex
        var resultIndex = -1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (sections[mid].adapterPosition <= position) {
                resultIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return if (resultIndex >= 0) sections[resultIndex].title else null
    }
}
