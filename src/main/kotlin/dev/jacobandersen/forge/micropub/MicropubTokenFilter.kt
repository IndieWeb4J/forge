package dev.jacobandersen.forge.micropub

import dev.jacobandersen.sigil.client.TokenIntrospector
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Validates the Micropub bearer token by introspection through Sigil
 * (`sigil-client`). Only active tokens pass; when no [TokenIntrospector] is
 * configured the facade refuses (503) rather than running unauthenticated.
 * The token may also arrive as an `access_token` form parameter (Micropub
 * permits it), which is read by the controller after this filter passes.
 */
class MicropubTokenFilter(
    private val introspectorProvider: ObjectProvider<TokenIntrospector>,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/micropub")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = bearerToken(request)
        if (token.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token required")
            return
        }

        val introspector =
            introspectorProvider.ifAvailable
                ?: run {
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "token introspection is not configured")
                    return
                }

        val introspection =
            try {
                introspector.introspect(token)
            } catch (e: Exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "token introspection failed")
                return
            }

        if (!introspection.active) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "token is not active")
            return
        }

        request.setAttribute(MicropubTokenFilter.ME_ATTRIBUTE, introspection.me)
        filterChain.doFilter(request, response)
    }

    private fun bearerToken(request: HttpServletRequest): String? =
        request
            .getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring("Bearer ".length)
            ?.trim()

    companion object {
        const val ME_ATTRIBUTE = "micropub.me"
    }
}
