package dev.jacobandersen.forge.micropub

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import tools.jackson.databind.ObjectMapper

/**
 * The Micropub facade endpoint. Handles create (JSON / form / multipart), update
 * (PATCH JSON or `action=update` form), delete/undelete, the media endpoint, and
 * the `q=` queries. All content work is delegated to the content service; Forge
 * only parses, forwards, and shapes the response.
 */
@RestController
@RequestMapping("/micropub")
class MicropubController(
    private val parser: MicropubParser,
    private val updateParser: MicropubUpdateParser,
    private val service: MicropubService,
    private val queries: MicropubQueryService,
    private val objectMapper: ObjectMapper,
) {
    // ---------------------------------------------------------------- queries

    @GetMapping
    fun query(
        @RequestParam("q") q: String,
        @RequestParam("url", required = false) url: String?,
        @RequestParam("properties", required = false) requested: List<String>?,
    ): ResponseEntity<Any> =
        when (q) {
            "config" -> {
                ResponseEntity.ok(queries.config())
            }

            "syndicate-to" -> {
                ResponseEntity.ok(mapOf("syndicate-to" to queries.syndicateTo()))
            }

            "source" -> {
                val source = url?.let { queries.source(it) }
                if (source == null) ResponseEntity.notFound().build() else ResponseEntity.ok(source)
            }

            "properties" -> {
                val source = url?.let { queries.sourceProperties(it, requested ?: emptyList()) }
                if (source == null) ResponseEntity.notFound().build() else ResponseEntity.ok(mapOf("properties" to source))
            }

            else -> {
                ResponseEntity.badRequest().body(mapOf("error" to "unsupported query: $q"))
            }
        }

    // -------------------------------------------------------------- write ops

    @PostMapping
    fun post(request: HttpServletRequest): ResponseEntity<Any> =
        if (request.isJson()) {
            handleJson(request)
        } else {
            handleForm(request)
        }

    @PatchMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun patch(
        @RequestBody body: String,
    ): ResponseEntity<Any> {
        val url = objectMapper.readTree(body)["url"]?.asString()
        return update(url, updateParser.fromJson(body))
    }

    @DeleteMapping
    fun delete(
        @RequestParam("url") url: String,
        @RequestParam("action", required = false, defaultValue = "delete") action: String,
    ): ResponseEntity<Any> {
        val result =
            if (action ==
                "undelete"
            ) {
                service.undelete(url.substringAfterLast('/'))
            } else {
                service.delete(url.substringAfterLast('/'))
            }
        return ResponseEntity.ok(result)
    }

    // ------------------------------------------------------------------ media

    @PostMapping("/media")
    fun media(request: MultipartHttpServletRequest): ResponseEntity<Any> {
        val file = request.fileMap.values.firstOrNull() ?: return ResponseEntity.badRequest().body(mapOf("error" to "no file"))
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
                location(service.create(parser.fromJson(body)))
            }

            "update" -> {
                val url = objectMapper.readTree(body)["url"]?.asString()
                update(url, updateParser.fromJson(body))
            }

            "delete" -> {
                ResponseEntity.ok(service.delete(requireUrl(body)))
            }

            "undelete" -> {
                ResponseEntity.ok(service.undelete(requireUrl(body)))
            }

            else -> {
                ResponseEntity.badRequest().body(mapOf("error" to "unsupported action: $action"))
            }
        }
    }

    private fun handleForm(request: HttpServletRequest): ResponseEntity<Any> {
        val params = request.parameterMap.mapValues { it.value.toList() }
        return when (params["action"]?.firstOrNull()) {
            null, "create" -> {
                val media = mediaParts(request)
                location(service.create(parser.fromForm(params, media)))
            }

            "update" -> {
                val url = params["url"]?.firstOrNull()
                update(url, updateParser.fromForm(params))
            }

            "delete" -> {
                ResponseEntity.ok(service.delete(requireUrl(params["url"]?.firstOrNull())))
            }

            "undelete" -> {
                ResponseEntity.ok(service.undelete(requireUrl(params["url"]?.firstOrNull())))
            }

            else -> {
                ResponseEntity.badRequest().body(mapOf("error" to "unsupported action"))
            }
        }
    }

    private fun update(
        url: String?,
        operations: MicropubUpdateOperations,
    ): ResponseEntity<Any> {
        val id = requireUrl(url).substringAfterLast('/')
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
            property = property,
            filename = originalFilename,
            contentType = contentType,
            bytes = bytes,
        )

    private fun requireUrl(url: String?): String = url?.takeIf { it.isNotBlank() } ?: throw MicropubError("url is required")

    private fun HttpServletRequest.isJson(): Boolean = (contentType ?: "").contains(MediaType.APPLICATION_JSON_VALUE, ignoreCase = true)

    @ExceptionHandler(MicropubError::class)
    fun onBadRequest(e: MicropubError): ResponseEntity<Any> = ResponseEntity.badRequest().body(mapOf("error" to e.message))

    @ExceptionHandler(MicropubNotFound::class)
    fun onNotFound(e: MicropubNotFound): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
}
