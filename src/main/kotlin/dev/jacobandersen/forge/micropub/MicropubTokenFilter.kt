package dev.jacobandersen.forge.micropub

import dev.jacobandersen.forge.config.ForgeProperties
import dev.jacobandersen.sigil.client.TokenIntrospector
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

private val logger = KotlinLogging.logger {}

/**
 * Validates the Micropub bearer token by introspection through Sigil
 * (`sigil-client`). Micropub 5.1 requires accepting the token from either the
 * `Authorization` header or an `access_token` form parameter, so both are read.
 *
 * Only active tokens pass. A token issued for an identity other than
 * [ForgeProperties.owner] is rejected with 403 `forbidden` (Micropub 3.8). When
 * the introspector is unavailable the facade refuses with 503 rather than
 * running unauthenticated. Granted scopes are stashed on the request for the
 * per-operation checks the controller performs.
 */
class MicropubTokenFilter(
    private val introspectorProvider: ObjectProvider<TokenIntrospector>,
    private val properties: ForgeProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/micropub")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = token(request)
        if (token.isNullOrBlank()) {
            writeError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "unauthorized",
                "A bearer token is required",
                """Bearer realm="micropub"""",
            )
            return
        }

        val introspector = introspectorProvider.ifAvailable
        if (introspector == null) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "server_error", "Token introspection is not configured", null)
            return
        }

        val introspection =
            try {
                introspector.introspect(token)
            } catch (e: Exception) {
                logger.warn("Token introspection failed", e)
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "server_error", "Token introspection failed", null)
                return
            }

        if (!introspection.active) {
            writeError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "invalid_token",
                "The token is not active",
                """Bearer realm="micropub", error="invalid_token"""",
            )
            return
        }

        val owner = properties.owner.trim()
        if (owner.isNotEmpty() && !sameIdentity(introspection.me, owner)) {
            logger.warn { "Rejected token for identity ${introspection.me}: not the site owner" }
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "The token does not belong to this site", null)
            return
        }

        request.setAttribute(ME_ATTRIBUTE, introspection.me)
        request.setAttribute(SCOPE_ATTRIBUTE, introspection.scope ?: "")
        filterChain.doFilter(request, response)
    }

    private fun token(request: HttpServletRequest): String? {
        val header =
            request
                .getHeader(HttpHeaders.AUTHORIZATION)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring("Bearer ".length)
                ?.trim()
        if (!header.isNullOrBlank()) return header

        // Micropub 5.1: the token may also be sent as an `access_token` form parameter.
        val contentType = request.contentType ?: return null
        if (!contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) return null
        return request.getParameter("access_token")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun sameIdentity(
        candidate: String?,
        owner: String,
    ): Boolean = candidate != null && candidate.trimEnd('/').equals(owner.trimEnd('/'), ignoreCase = true)

    private fun writeError(
        response: HttpServletResponse,
        status: Int,
        error: String,
        description: String?,
        authenticate: String?,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        if (authenticate != null) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authenticate)
        val body = linkedMapOf<String, Any>("error" to error)
        if (description != null) body["error_description"] = description
        response.writer.write(objectMapper.writeValueAsString(body))
    }

    companion object {
        const val ME_ATTRIBUTE = "micropub.me"
        const val SCOPE_ATTRIBUTE = "micropub.scope"
    }
}
