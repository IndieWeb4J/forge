package dev.jacobandersen.forge

import dev.jacobandersen.content.client.ContentClient
import dev.jacobandersen.content.client.CreatePostCommand
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The Forge Micropub facade. Thin layer over the content service (Bastion via
 * content-client): writes are translated into [CreatePostCommand] (mf2 boundary)
 * and forwarded to Bastion's internal write API; read queries are proxied to
 * Bastion's public read API via [ContentClient]. No storage lives in Forge.
 */
@RestController
@RequestMapping("/micropub")
class MicropubController(
    private val contentClient: ContentClient,
) {
    @PostMapping
    fun create(
        @RequestBody command: CreatePostCommand,
    ) = contentClient.create(command)

    @GetMapping
    fun query() = "proxied to Bastion read API"
}