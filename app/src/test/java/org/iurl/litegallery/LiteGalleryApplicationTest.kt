package org.iurl.litegallery

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteGalleryApplicationTest {

    @Test
    fun applicationDoesNotOwnARecyclerViewPool() {
        val fields = LiteGalleryApplication::class.java.declaredFields.toList() +
            LiteGalleryApplication::class.java.declaredClasses.flatMap { it.declaredFields.toList() }

        val globalPools = fields.filter { field ->
            field.type == RecyclerView.RecycledViewPool::class.java ||
                field.name.contains("sharedFolderViewPool", ignoreCase = true)
        }

        assertTrue(
            "RecyclerView pools must stay Activity-scoped because ViewHolders hold Glide/Activity context.",
            globalPools.isEmpty()
        )
    }
}
