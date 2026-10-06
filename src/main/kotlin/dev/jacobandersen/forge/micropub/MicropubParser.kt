package dev.jacobandersen.forge.micropub

import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.microformats2.json.toMf2Object
import dev.jacobandersen.microformats2.plainTextOrNull
import dev.jacobandersen.microformats2.texts
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Turns a Micropub request into [MicropubRequest]: the mf2 document plus the
 * command hints Bastion honors. Supports both the Micropub JSON syntax (mf2
 * JSON) and the form-encoded / multipart syntax. Forge does no PTD or storage;
 * it is a thin parse/validate/serialize adapter.
 */
@Component
class MicropubParser(
    private val objectMapper: ObjectMapper,
) {
    /** Parses a Micropub JSON body (`{type, properties, ...}`) into mf2 + hints. */
    fun fromJson(body: String): MicropubRequest {
        val node =
            objectMapper.readTree(body)
                ?: throw MicropubError("request body is not valid JSON")
        if (node !is ObjectNode) throw MicropubError("Micropub JSON body must be an object")

        val parsed = node.toMf2Object()
        val stored = parsed.withoutReserved()
        return MicropubRequest(
            post = stored,
            slugHint = parsed.text("mp-slug"),
            status = normalizeStatus(parsed.text("post-status")),
            visibility = normalizeVisibility(parsed.text("visibility")),
            publishedHint = parsed.text("published"),
            syndicationTargets = parsed.texts("mp-syndicate-to"),
            media = emptyList(),
        )
    }

    /**
     * Parses form-encoded / multipart parameters into mf2 + hints. Reserved
     * Micropub parameters (`h`, `mp-*`, `access_token`, `action`) are not stored
     * as properties; `post-status`/`visibility` are hints and left out of mf2.
     */
    fun fromForm(
        params: Map<String, List<String>>,
        media: List<MicropubMedia> = emptyList(),
    ): MicropubRequest {
        val h = first(params, "h")?.takeIf { it.isNotBlank() } ?: "entry"
        val properties = linkedMapOf<String, List<Mf2Value>>()
        params.forEach { (key, values) ->
            if (key in RESERVED || key.startsWith("mp-")) return@forEach
            val vs = values.filter { it.isNotBlank() }.map { Mf2Value.String(it) }
            if (vs.isNotEmpty()) properties[key] = vs
        }
        val post = Mf2Object(type = listOf("h-$h"), properties = properties)
        return MicropubRequest(
            post = post,
            slugHint = first(params, "mp-slug"),
            status = normalizeStatus(first(params, "post-status")),
            visibility = normalizeVisibility(first(params, "visibility")),
            publishedHint = first(params, "published"),
            syndicationTargets = params["mp-syndicate-to"].orEmpty().filter { it.isNotBlank() },
            media = media,
        )
    }

    private fun first(
        params: Map<String, List<String>>,
        key: String,
    ): String? = params[key]?.firstOrNull()

    private fun Mf2Object.text(key: String): String? = getProperty(key).firstNotNullOfOrNull { it.plainTextOrNull }

    private fun Mf2Object.withoutReserved(): Mf2Object =
        copy(
            properties =
                properties.filterKeys {
                    it != "mp-slug" &&
                        it != "mp-syndicate-to" &&
                        it != "post-status" &&
                        it != "visibility"
                },
        )

    private fun normalizeStatus(raw: String?): String? =
        when (raw?.lowercase()) {
            "draft" -> "DRAFT"
            "published" -> "PUBLISHED"
            else -> null
        }

    private fun normalizeVisibility(raw: String?): String? =
        when (raw?.lowercase()) {
            "public" -> "PUBLIC"
            "unlisted" -> "UNLISTED"
            "private" -> "PRIVATE"
            else -> null
        }

    private companion object {
        val RESERVED = setOf("h", "access_token", "action", "post-status", "visibility")
    }
}

/** A malformed Micropub request; mapped to 400 by the controller. */
class MicropubError(
    message: String,
) : RuntimeException(message)
