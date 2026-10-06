package dev.jacobandersen.forge.micropub

import dev.jacobandersen.mf24j.Mf2Value
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MicropubParserTest {
    private val parser = MicropubParser(JsonMapper.builder().build())

    @Test
    fun `parses form create into h-entry with hints`() {
        val request =
            parser.fromForm(
                mapOf(
                    "h" to listOf("entry"),
                    "content" to listOf("Hello world"),
                    "category" to listOf("kotlin", "indieweb"),
                    "mp-slug" to listOf("hello"),
                    "post-status" to listOf("draft"),
                    "visibility" to listOf("unlisted"),
                    "mp-syndicate-to" to listOf("https://twitter.example"),
                ),
            )

        assertEquals(listOf("h-entry"), request.post.type)
        assertEquals(listOf(Mf2Value.String("Hello world")), request.post.getProperty("content"))
        assertEquals(listOf(Mf2Value.String("kotlin"), Mf2Value.String("indieweb")), request.post.getProperty("category"))
        assertEquals("hello", request.slugHint)
        assertEquals("DRAFT", request.status)
        assertEquals("UNLISTED", request.visibility)
        assertEquals(listOf("https://twitter.example"), request.syndicationTargets)
        // reserved props are not stored
        assertNull(request.post.getProperty("mp-slug").firstOrNull())
        assertNull(request.post.getProperty("post-status").firstOrNull())
    }

    @Test
    fun `parses json create and strips reserved properties`() {
        val body =
            """
            {
              "type": ["h-entry"],
              "properties": {
                "content": ["JSON post"],
                "mp-slug": ["json-slug"],
                "post-status": ["published"]
              }
            }
            """.trimIndent()

        val request = parser.fromJson(body)

        assertEquals(listOf("h-entry"), request.post.type)
        assertEquals(listOf(Mf2Value.String("JSON post")), request.post.getProperty("content"))
        assertEquals("json-slug", request.slugHint)
        assertEquals("PUBLISHED", request.status)
        assertNull(request.post.getProperty("mp-slug").firstOrNull())
    }

    @Test
    fun `defaults to h-entry when h missing`() {
        val request = parser.fromForm(mapOf("content" to listOf("x")))
        assertEquals(listOf("h-entry"), request.post.type)
    }
}
