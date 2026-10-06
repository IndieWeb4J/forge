package dev.jacobandersen.forge.micropub

import dev.jacobandersen.content.client.ContentClientException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.core.JacksonException

private val logger = KotlinLogging.logger {}

/**
 * Maps Micropub and framework failures to the spec's JSON error shape
 * (`{"error": ..., "error_description": ...}`, Micropub 3.8). Micropub defines
 * `invalid_request` (400), `unauthorized` / `insufficient_scope` (401) and
 * `forbidden` (403); unhandled framework errors would otherwise return Spring
 * Boot's default error document instead.
 *
 * This is intentionally a single global advice: the token filter writes its own
 * responses (filters bypass `@ControllerAdvice`), and Forge has no other
 * application controllers.
 */
@RestControllerAdvice
class MicropubExceptionHandler {
    @ExceptionHandler(MicropubError::class)
    fun onInvalidRequest(e: MicropubError): ResponseEntity<Map<String, Any>> = error(HttpStatus.BAD_REQUEST, "invalid_request", e.message)

    @ExceptionHandler(JacksonException::class)
    fun onMalformedJson(e: JacksonException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", "The request body is not valid JSON")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", "The request body could not be read")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun onMissingParameter(e: MissingServletRequestParameterException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", "The '${e.parameterName}' parameter is required")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun onTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "invalid_request", "The '${e.name}' parameter is invalid")

    @ExceptionHandler(MicropubNotFound::class)
    fun onNotFound(e: MicropubNotFound): ResponseEntity<Map<String, Any>> = error(HttpStatus.NOT_FOUND, "not_found", e.message)

    @ExceptionHandler(MicropubInsufficientScope::class)
    fun onInsufficientScope(e: MicropubInsufficientScope): ResponseEntity<Map<String, Any>> {
        val body =
            linkedMapOf<String, Any>(
                "error" to "insufficient_scope",
                "error_description" to "The token is missing the '${e.scope}' scope",
                "scope" to e.scope,
            )
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, """Bearer realm="micropub", error="insufficient_scope", scope="${e.scope}"""")
            .body(body)
    }

    @ExceptionHandler(MicropubForbidden::class)
    fun onForbidden(e: MicropubForbidden): ResponseEntity<Map<String, Any>> = error(HttpStatus.FORBIDDEN, "forbidden", e.message)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun onMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<Map<String, Any>> {
        val builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        e.supportedHttpMethods?.let { builder.header(HttpHeaders.ALLOW, it.joinToString(", ") { method -> method.name() }) }
        return builder.body(errorBody("invalid_request", "The HTTP method is not allowed for this endpoint"))
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun onMediaTypeNotSupported(e: HttpMediaTypeNotSupportedException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "invalid_request", "The request content type is not supported")

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun onMediaTypeNotAcceptable(e: HttpMediaTypeNotAcceptableException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_ACCEPTABLE, "invalid_request", "No acceptable response content type was requested")

    @ExceptionHandler(NoResourceFoundException::class)
    fun onNoResource(e: NoResourceFoundException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.NOT_FOUND, "not_found", "No such endpoint")

    @ExceptionHandler(ContentClientException::class)
    fun onDownstreamFailure(e: ContentClientException): ResponseEntity<Map<String, Any>> {
        logger.error(e) { "Content service call failed" }
        return error(HttpStatus.BAD_GATEWAY, "server_error", "The content service could not be reached")
    }

    @ExceptionHandler(Exception::class)
    fun onUnexpected(e: Exception): ResponseEntity<Map<String, Any>> {
        logger.error(e) { "Unhandled Micropub request failure" }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "An unexpected error occurred")
    }

    private fun error(
        status: HttpStatus,
        error: String,
        description: String?,
    ): ResponseEntity<Map<String, Any>> = ResponseEntity.status(status).body(errorBody(error, description))

    private fun errorBody(
        error: String,
        description: String?,
    ): Map<String, Any> {
        val body = linkedMapOf<String, Any>("error" to error)
        if (description != null) body["error_description"] = description
        return body
    }
}
