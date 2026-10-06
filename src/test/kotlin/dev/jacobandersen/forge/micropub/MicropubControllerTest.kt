package dev.jacobandersen.forge.micropub

import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.ContentWriteClient
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.content.client.WritePostResult
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.sigil.client.TokenIntrospector
import dev.jacobandersen.sigil.protocol.IntrospectionResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "forge.syndicate-to[0].uid=bridgy",
        "forge.syndicate-to[0].name=Bridgy",
    ],
)
@AutoConfigureMockMvc
class MicropubControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var contentReadClient: ContentReadClient

    @MockitoBean
    private lateinit var contentWriteClient: ContentWriteClient

    @MockitoBean
    private lateinit var tokenIntrospector: TokenIntrospector

    private fun activeToken(token: String = "token") {
        given(tokenIntrospector.introspect(token))
            .willReturn(IntrospectionResponse(active = true, me = "https://me.example"))
    }

    private fun postDto(): PostDto =
        PostDto(
            id = "id-1",
            slug = "hello",
            url = "https://me.example/hello",
            h = "h-entry",
            type = "note",
            status = "PUBLISHED",
            visibility = "PUBLIC",
            deleted = false,
            categories = listOf("a"),
            version = 1,
            post =
                Mf2Object(
                    type = listOf("h-entry"),
                    properties =
                        mapOf(
                            "content" to listOf(Mf2Value.String("hi")),
                            "category" to listOf(Mf2Value.String("a")),
                        ),
                ),
        )

    @Test
    fun `rejects a request without a token`() {
        mockMvc
            .perform(
                post("/micropub")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("h", "entry")
                    .param("content", "hi"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `creates a post and returns 201 with Location`() {
        activeToken()
        given(contentWriteClient.create(any()))
            .willReturn(WritePostResult(id = "id-1", slug = "hello", url = "https://me.example/hello", version = 1))

        mockMvc
            .perform(
                post("/micropub")
                    .header("Authorization", "Bearer token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("h", "entry")
                    .param("content", "hi")
                    .param("mp-slug", "hello"),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "https://me.example/hello"))

        verify(contentWriteClient).create(any())
    }

    @Test
    fun `q=config advertises the media endpoint and syndication targets`() {
        activeToken()
        mockMvc
            .perform(get("/micropub").header("Authorization", "Bearer token").param("q", "config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.media-endpoint").value("/micropub/media"))
            .andExpect(jsonPath("$.syndicate-to[0].uid").value("bridgy"))
            .andExpect(jsonPath("$.syndicate-to[0].name").value("Bridgy"))
    }

    @Test
    fun `q=syndicate-to returns the targets`() {
        activeToken()
        mockMvc
            .perform(get("/micropub").header("Authorization", "Bearer token").param("q", "syndicate-to"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syndicate-to[0].uid").value("bridgy"))
    }

    @Test
    fun `q=source translates to the content read API and returns mf2`() {
        activeToken()
        given(contentReadClient.postByUrl("https://me.example/hello")).willReturn(postDto())

        mockMvc
            .perform(
                get("/micropub")
                    .header("Authorization", "Bearer token")
                    .param("q", "source")
                    .param("url", "https://me.example/hello"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.type[0]").value("h-entry"))
            .andExpect(jsonPath("$.properties.content[0]").value("hi"))

        verify(contentReadClient).postByUrl("https://me.example/hello")
    }

    @Test
    fun `q=properties filters the requested properties`() {
        activeToken()
        given(contentReadClient.postByUrl("https://me.example/hello")).willReturn(postDto())

        mockMvc
            .perform(
                get("/micropub")
                    .header("Authorization", "Bearer token")
                    .param("q", "properties")
                    .param("url", "https://me.example/hello")
                    .param("properties", "content"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.properties.content[0]").value("hi"))
            .andExpect(jsonPath("$.properties.category").doesNotExist())
    }

    @Test
    fun `q=source returns 404 for an unknown url`() {
        activeToken()
        given(contentReadClient.postByUrl("https://me.example/missing")).willReturn(null)

        mockMvc
            .perform(
                get("/micropub")
                    .header("Authorization", "Bearer token")
                    .param("q", "source")
                    .param("url", "https://me.example/missing"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `unsupported q is rejected`() {
        activeToken()
        mockMvc
            .perform(get("/micropub").header("Authorization", "Bearer token").param("q", "bogus"))
            .andExpect(status().isBadRequest)
    }
}
