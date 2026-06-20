package org.iurl.litegallery

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TrashAdapterTest {

    @Test
    fun selectionChangeRebindsOnlyMatchingRowsViaPathIndex() {
        val adapter = TrashAdapter(
            onItemClick = {},
            onItemLongClick = {},
            onRestoreClick = {},
            onPermanentDeleteClick = {},
            isItemSelected = { false },
            isInSelectionMode = { false },
            getSourceBadgeLabel = { null },
            getSourceBadgeContentDescription = { null },
            getRemainDaysLabel = { null },
            getFromLabel = { null }
        )

        val changedPositions = mutableListOf<Int>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                repeat(itemCount) { offset -> changedPositions.add(positionStart + offset) }
            }
        })

        adapter.submitList(listOf(item("a"), item("b"), item("c")))
        // ListAdapter applies the list on a background thread by default; drain it.
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        changedPositions.clear()
        adapter.notifySelectionChanged(setOf(path("a"), path("c"), path("missing")))

        assertEquals(listOf(0, 2), changedPositions.sorted())
    }

    private fun item(name: String): MediaItem =
        MediaItem(
            id = name.hashCode().toLong(),
            name = "$name.jpg",
            path = path(name),
            dateModified = 1L,
            size = 1L,
            mimeType = "image/jpeg"
        )

    private fun path(name: String): String = "/trash/$name.jpg"
}
