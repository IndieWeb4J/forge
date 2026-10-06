package dev.jacobandersen.forge.micropub

import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.forge.config.ForgeProperties
import dev.jacobandersen.forge.config.SyndicateTarget
import dev.jacobandersen.mf24j.Mf2Object
import org.springframework.stereotype.Service

/**
 * Translates Micropub `q=` queries over `content-client`'s read API, keeping
 * Bastion Micropub-ignorant. `q=source` fetches a post's mf2; `q=config` and
 * `q=syndicate-to` advertise the facade's media endpoint and targets.
 */
@Service
class MicropubQueryService(
    private val contentReadClient: ContentReadClient,
    private val properties: ForgeProperties,
) {
    fun config(): Map<String, Any> =
        mapOf(
            "media-endpoint" to properties.mediaEndpoint,
            "syndicate-to" to properties.syndicateTo,
        )

    fun syndicateTo(): List<SyndicateTarget> = properties.syndicateTo

    /** The canonical mf2 of the post at [url], or null when there is none. */
    fun source(url: String): Mf2Object? = contentReadClient.postByUrl(url)?.takeIf { !it.deleted }?.post

    fun sourceProperties(
        url: String,
        requested: List<String>,
    ): Map<String, Any>? {
        val post = source(url) ?: return null
        if (requested.isEmpty()) return post.properties
        return requested.mapNotNull { key -> post.properties[key]?.let { key to it } }.toMap()
    }
}
