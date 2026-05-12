package org.iurl.litegallery

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MediaAdapterTest {

    @Test
    fun selectionPayloadUsesMaintainedPathIndexAfterListMutations() {
        val adapter = MediaAdapter(onMediaClick = { _, _ -> })
        val changedPositions = mutableListOf<Int>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                repeat(itemCount) { offset ->
                    changedPositions.add(positionStart + offset)
                }
            }
        })

        adapter.submitList(listOf(item("a"), item("b")))
        adapter.appendSkeletons(listOf(item("c")))
        adapter.renameSkeleton(path("b"), item("renamed"))
        adapter.removeSkeletonPaths(setOf(path("a")))

        changedPositions.clear()
        adapter.notifySelectionChanged(listOf(path("renamed"), path("c"), path("missing")))

        assertEquals(listOf(0, 1), changedPositions)
    }

    private fun item(name: String): MediaItemSkeleton =
        MediaItemSkeleton(
            id = name.hashCode().toLong(),
            path = path(name),
            name = "$name.jpg",
            dateModified = 1L,
            size = 1L,
            isVideo = false
        )

    private fun path(name: String): String = "/media/$name.jpg"
}
