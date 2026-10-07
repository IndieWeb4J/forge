package dev.jacobandersen.forge.micropub

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import tools.jackson.databind.ObjectMapper

/**
 * The Micropub facade endpoint. Handles create (JSON / form / multipart), update
 * (PATCH JSON or `action=update` form), delete/undelete, the media endpoint, and
 * the `q=` queries. All content work is delegated to the content service; Forge
 * only parses, forwards, and shapes the response.
 *
 * Every write requires the scope for its operation (Micropub 5.4 / 3.8); the
 * token filter has already authenticated the request and stashed the granted
 * scopes.
 */
@RestController
@RequestMapping("/micropub", produces = [MediaType.APPLICATION_JSON_VALUE])
class MicropubController(
    private val parser: MicropubParser,
    private val updateParser: MicropubUpdateParser,
    private val service: MicropubService,
    private val queries: MicropubQueryService,
    private val objectMapper: ObjectMapper,
) {
    // ---------------------------------------------------------------- queries

    @GetMapping
    fun query(request: HttpServletRequest): ResponseEntity<Any> {
        val q = request.getParameter("q") ?: throw MicropubError("The 'q' parameter is required")
        val requested = queryProperties(request)
        return when (q) {
            "config" -> {
                ResponseEntity.ok(queries.config(requestBaseUrl(request)))
            }

            "syndicate-to" -> {
                ResponseEntity.ok(mapOf("syndicate-to" to queries.syndicateTo()))
            }

            "source" -> {
                val url = requireUrl(request.getParameter("url"))
                ResponseEntity.ok(queries.source(url) ?: throw MicropubNotFound("No post found at $url"))
            }

            "properties" -> {
                val url = requireUrl(request.getParameter("url"))
                val properties = queries.sourceProperties(url, requested) ?: throw MicropubNotFound("No post found at $url")
                ResponseEntity.ok(mapOf("properties" to properties))
            }

            else -> {
                throw MicropubError("unsupported query: $q")
            }
        }
    }

    // -------------------------------------------------------------- write ops

    @PostMapping
    fun post(request: HttpServletRequest): ResponseEntity<Any> {
        val contentType = request.contentType?.lowercase().orEmpty()
        return when {
            contentType.contains(MediaType.APPLICATION_JSON_VALUE) -> handleJson(request)
            contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE) -> handleForm(request)
            contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE) -> handleForm(request)
            contentType.isBlank() -> handleForm(request)
            else -> throw MicropubUnsupportedMediaType("Unsupported content type: $contentType")
        }
    }

    @PatchMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun patch(
        request: HttpServletRequest,
        @RequestBody body: String,
    ): ResponseEntity<Any> {
        requireScope(request, "update")
        val url = objectMapper.readTree(body)["url"]?.asString()
        return update(url, updateParser.fromJson(body))
    }

    @DeleteMapping
    fun delete(request: HttpServletRequest): ResponseEntity<Any> {
        val id = requireUrl(request.getParameter("url")).postId()
        return when (val action = request.getParameter("action") ?: "delete") {
            "delete" -> {
                requireScope(request, "delete")
                ResponseEntity.ok(service.delete(id))
            }

            "undelete" -> {
                requireScope(request, "undelete")
                ResponseEntity.ok(service.undelete(id))
            }

            else -> {
                throw MicropubError("unsupported action: $action")
            }
        }
    }

    // ------------------------------------------------------------------ media

    @PostMapping("/media")
    fun media(request: HttpServletRequest): ResponseEntity<Any> {
        requireScope(request, "media")
        if (request !is MultipartHttpServletRequest) throw MicropubError("The media endpoint requires multipart/form-data")
        val file = request.fileMap.values.firstOrNull() ?: throw MicropubError("no file")
        val result = service.uploadMedia(file)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .header("Location", result.url)
            .build()
    }

    // ---------------------------------------------------------------- helpers

    private fun handleJson(request: HttpServletRequest): ResponseEntity<Any> {
        val body = request.reader.readText()
        val action = runCatching { objectMapper.readTree(body)["action"]?.asString() }.getOrNull()
        return when (action) {
            null, "create" -> {
                requireScope(request, "create")
                location(service.create(parser.fromJson(body)))
            }

            "update" -> {
                requireScope(request, "update")
                update(objectMapper.readTree(body)["url"]?.asString(), updateParser.fromJson(body))
            }

            "delete" -> {
                requireScope(request, "delete")
                ResponseEntity.ok(service.delete(requireUrl(objectMapper.readTree(body)["url"]?.asString()).postId()))
            }

            "undelete" -> {
                requireScope(request, "undelete")
                ResponseEntity.ok(service.undelete(requireUrl(objectMapper.readTree(body)["url"]?.asString()).postId()))
            }

            else -> {
                throw MicropubError("unsupported action: $action")
            }
        }
    }

    private fun handleForm(request: HttpServletRequest): ResponseEntity<Any> {
        val params = request.parameterMap.mapValues { it.value.toList() }
        return when (params["action"]?.firstOrNull()) {
            null, "create" -> {
                requireScope(request, "create")
                location(service.create(parser.fromForm(params, mediaParts(request))))
            }

            "update" -> {
                requireScope(request, "update")
                update(params["url"]?.firstOrNull(), updateParser.fromForm(params))
            }

            "delete" -> {
                requireScope(request, "delete")
                ResponseEntity.ok(service.delete(requireUrl(params["url"]?.firstOrNull()).postId()))
            }

            "undelete" -> {
                requireScope(request, "undelete")
                ResponseEntity.ok(service.undelete(requireUrl(params["url"]?.firstOrNull()).postId()))
            }

            else -> {
                throw MicropubError("unsupported action")
            }
        }
    }

    private fun update(
        url: String?,
        operations: MicropubUpdateOperations,
    ): ResponseEntity<Any> {
        val id = requireUrl(url).postId()
        return ResponseEntity.ok(service.update(id, operations))
    }

    private fun location(result: dev.jacobandersen.content.client.WritePostResult): ResponseEntity<Any> =
        ResponseEntity
            .created(java.net.URI.create(result.url))
            .header("Location", result.url)
            .body(result)

    private fun mediaParts(request: HttpServletRequest): List<MicropubMedia> {
        if (request !is MultipartHttpServletRequest) return emptyList()
        return request.multiFileMap.flatMap { (property, files) ->
            files.map { file -> file.toMicropubMedia(property) }
        }
    }

    private fun MultipartFile.toMicropubMedia(property: String): MicropubMedia =
        MicropubMedia(
            property = property.removeSuffix("[]"),
            filename = originalFilename,
            contentType = contentType,
            bytes = bytes,
        )

    private fun requireScope(
        request: HttpServletRequest,
        scope: String,
    ) {
        val granted = (request.getAttribute(MicropubTokenFilter.SCOPE_ATTRIBUTE) as? String).orEmpty()
        if (granted.split(' ').none { it == scope }) {
            throw MicropubInsufficientScope(scope)
        }
    }

    private fun queryProperties(request: HttpServletRequest): List<String> =
        (
            request.getParameterValues("properties").orEmpty().asList() +
                request.getParameterValues("properties[]").orEmpty().asList()
        ).filter { it.isNotBlank() }

    private fun requireUrl(url: String?): String = url?.takeIf { it.isNotBlank() } ?: throw MicropubError("url is required")

    private fun String.postId(): String = substringAfterLast('/')

    private fun requestBaseUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val host = request.serverName
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) return ""
        val port = request.serverPort
        val defaultPort = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
        return if (defaultPort) "$scheme://$host" else "$scheme://$host:$port"
    }
}
