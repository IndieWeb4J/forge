package dev.jacobandersen.forge.micropub

import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.ContentWriteClient
import dev.jacobandersen.content.client.CreatePostCommand
import dev.jacobandersen.content.client.MediaUploadResult
import dev.jacobandersen.content.client.UpdatePostCommand
import dev.jacobandersen.content.client.WritePostResult
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream

/**
 * Orchestrates a Micropub write against the content service: uploads any
 * attached media first (Bastion owns the bytes), then issues the content
 * command. Forge holds no state; every call is a single, synchronous command.
 */
@Service
class MicropubService(
    private val contentReadClient: ContentReadClient,
    private val contentWriteClient: ContentWriteClient,
    private val updater: MicropubUpdater,
) {
    fun create(request: MicropubRequest): WritePostResult =
        contentWriteClient.create(
            CreatePostCommand(
                post = uploadMedia(request),
                slugHint = request.slugHint,
                status = request.status,
                visibility = request.visibility,
                publishedHint = request.publishedHint,
                syndicationTargets = request.syndicationTargets,
            ),
        )

    fun update(
        id: String,
        operations: MicropubUpdateOperations,
    ): WritePostResult {
        val current =
            contentReadClient.postById(id)
                ?: throw MicropubNotFound("post $id not found")
        val updated = updater.apply(current.post, operations)
        return contentWriteClient.update(
            id,
            UpdatePostCommand(
                post = updated,
                syndicationTargets = operations.syndicateTo,
            ),
        )
    }

    fun delete(id: String): WritePostResult = contentWriteClient.delete(id)

    fun undelete(id: String): WritePostResult = contentWriteClient.undelete(id)

    fun uploadMedia(file: MultipartFile): MediaUploadResult =
        contentWriteClient.uploadMedia(
            filename = file.originalFilename ?: "file",
            contentType = file.contentType,
            bytes = ByteArrayInputStream(file.bytes),
        )

    private fun uploadMedia(request: MicropubRequest): Mf2Object {
        if (request.media.isEmpty()) return request.post
        var post = request.post
        for (m in request.media) {
            val result =
                contentWriteClient.uploadMedia(
                    filename = m.filename ?: "file",
                    contentType = m.contentType,
                    bytes = ByteArrayInputStream(m.bytes),
                )
            post = post.addProperty(m.property, Mf2Value.String(result.url))
        }
        return post
    }
}

/** A Micropub target that does not exist (404). */
class MicropubNotFound(
    message: String,
) : RuntimeException(message)
