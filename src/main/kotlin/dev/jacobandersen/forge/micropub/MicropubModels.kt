package dev.jacobandersen.forge.micropub

import dev.jacobandersen.microformats2.Mf2Object

/**
 * A media part of a Micropub request: the property it targets, the client
 * filename/content type, and its bytes. Forge forwards these to Bastion's
 * internal media command and substitutes the returned URL into the mf2.
 */
data class MicropubMedia(
    val property: String,
    val filename: String?,
    val contentType: String?,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MicropubMedia) return false
        return property == other.property &&
            filename == other.filename &&
            contentType == other.contentType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = property.hashCode()
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * A parsed Micropub write request: the canonical mf2 document plus the command
 * hints Bastion honors (slug/status/visibility/published/syndication) and any
 * attached media to upload first.
 */
data class MicropubRequest(
    val post: Mf2Object,
    val slugHint: String? = null,
    val status: String? = null,
    val visibility: String? = null,
    val publishedHint: String? = null,
    val syndicationTargets: List<String> = emptyList(),
    val media: List<MicropubMedia> = emptyList(),
)
