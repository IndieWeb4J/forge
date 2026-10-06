package dev.jacobandersen.forge.micropub

import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.ContentWriteClient
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.content.client.WritePostResult
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.sigil.client.TokenIntrospector
import dev.jacobandersen.sigil.protocol.IntrospectionResponse
import org.hamcrest.Matchers.containsString
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "forge.owner=https://me.example",
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

    private fun activeToken(
        token: String = "token",
        me: String = "https://me.example",
        scope: String = "create update delete undelete media",
    ) {
        given(tokenIntrospector.introspect(token))
            .willReturn(IntrospectionResponse(active = true, me = me, scope = scope))
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

    // ------------------------------------------------------------ authentication

    @Test
    fun `rejects a request without a token with unauthorized and WWW-Authenticate`() {
        mockMvc
            .perform(
                post("/micropub")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("h", "entry")
                    .param("content", "hi"),
            ).andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
            .andExpect(jsonPath("$.error").value("unauthorized"))
    }

    @Test
    fun `accepts the token as an access_token form parameter`() {
        activeToken()
        given(contentWriteClient.create(any()))
            .willReturn(WritePostResult(id = "id-1", slug = "hello", url = "https://me.example/hello", version = 1))

        mockMvc
            .perform(
                post("/micropub")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("access_token", "token")
                    .param("h", "entry")
                    .param("content", "hi"),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `rejects an inactive token`() {
        given(tokenIntrospector.introspect("dead")).willReturn(IntrospectionResponse(active = false))

        mockMvc
            .perform(
                post("/micropub")
                    .header("Authorization", "Bearer dead")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("h", "entry")
                    .param("content", "hi"),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("invalid_token"))
    }

    @Test
    fun `rejects a token issued for another identity with forbidden`() {
        activeToken(me = "https://someone-else.example")

        mockMvc
            .perform(
                post("/micropub")
                    .header("Authorization", "Bearer token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("h", "entry")
                    .param("content", "hi"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("forbidden"))
    }

    @Test
    fun `rejects a write when the token lacks the scope`() {
        activeToken(scope = "create")

        mockMvc
            .perform(
                delete("/micropub")
                    .header("Authorization", "Bearer token")
                    .param("url", "https://me.example/hello"),
            ).andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", containsString("insufficient_scope")))
            .andExpect(jsonPath("$.error").value("insufficient_scope"))
            .andExpect(jsonPath("$.scope").value("delete"))
    }

    // ------------------------------------------------------------------ writes

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
    fun `rejects malformed JSON with invalid_request`() {
        activeToken()

        mockMvc
            .perform(
                post("/micropub")
                    .header("Authorization", "Bearer token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{not json"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }

    @Test
    fun `rejects an unknown delete action`() {
        activeToken()

        mockMvc
            .perform(
                delete("/micropub")
                    .header("Authorization", "Bearer token")
                    .param("url", "https://me.example/hello")
                    .param("action", "bogus"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }

    // ----------------------------------------------------------------- queries

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
    fun `q=properties accepts array bracket notation`() {
        activeToken()
        given(contentReadClient.postByUrl("https://me.example/hello")).willReturn(postDto())

        mockMvc
            .perform(
                get("/micropub")
                    .header("Authorization", "Bearer token")
                    .param("q", "properties")
                    .param("url", "https://me.example/hello")
                    .param("properties[]", "content"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.properties.content[0]").value("hi"))
            .andExpect(jsonPath("$.properties.category").doesNotExist())
    }

    @Test
    fun `q=source without a url is a bad request`() {
        activeToken()

        mockMvc
            .perform(get("/micropub").header("Authorization", "Bearer token").param("q", "source"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
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
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }
}
