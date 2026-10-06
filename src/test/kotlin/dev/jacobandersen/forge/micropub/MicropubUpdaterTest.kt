package dev.jacobandersen.forge.micropub

import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import kotlin.test.Test
import kotlin.test.assertEquals

class MicropubUpdaterTest {
    private val updater = MicropubUpdater()

    private fun doc(): Mf2Object =
        Mf2Object(
            type = listOf("h-entry"),
            properties =
                mapOf(
                    "content" to listOf(Mf2Value.String("old")),
                    "category" to listOf(Mf2Value.String("a"), Mf2Value.String("b")),
                ),
        )

    @Test
    fun `replace overwrites a property`() {
        val result = updater.apply(doc(), MicropubUpdateOperations(replace = mapOf("content" to listOf(Mf2Value.String("new")))))
        assertEquals(listOf(Mf2Value.String("new")), result.getProperty("content"))
    }

    @Test
    fun `add appends to a property`() {
        val result = updater.apply(doc(), MicropubUpdateOperations(add = mapOf("category" to listOf(Mf2Value.String("c")))))
        assertEquals(
            listOf(Mf2Value.String("a"), Mf2Value.String("b"), Mf2Value.String("c")),
            result.getProperty("category"),
        )
    }

    @Test
    fun `delete with values removes only those values`() {
        val result = updater.apply(doc(), MicropubUpdateOperations(delete = mapOf("category" to listOf(Mf2Value.String("a")))))
        assertEquals(listOf(Mf2Value.String("b")), result.getProperty("category"))
    }

    @Test
    fun `delete without values removes the property`() {
        val result = updater.apply(doc(), MicropubUpdateOperations(delete = mapOf("category" to emptyList())))
        assertEquals(emptyList(), result.getProperty("category"))
    }
}
