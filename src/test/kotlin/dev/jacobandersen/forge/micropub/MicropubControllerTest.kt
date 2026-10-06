package dev.jacobandersen.forge.micropub

import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.ContentWriteClient
import dev.jacobandersen.content.client.WritePostResult
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
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
        given(tokenIntrospector.introspect("token"))
            .willReturn(IntrospectionResponse(active = true, me = "https://me.example"))
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
}
