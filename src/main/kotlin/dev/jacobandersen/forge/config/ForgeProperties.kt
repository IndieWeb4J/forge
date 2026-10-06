package dev.jacobandersen.forge.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Forge facade configuration. Forge owns no content, so these are only the
 * presentation values the Micropub `q=config` / `q=syndicate-to` queries need;
 * content itself comes from the content service via `content-client`.
 */
@ConfigurationProperties(prefix = "forge")
data class ForgeProperties(
    /** The advertised media endpoint (the facade's own). */
    val mediaEndpoint: String = "/micropub/media",
    /** Syndication targets advertised by `q=config` / `q=syndicate-to`. */
    val syndicateTo: List<SyndicateTarget> = emptyList(),
    /**
     * The site owner's canonical profile URL. Access tokens must be issued for
     * this identity (Micropub 3.8 `forbidden` otherwise). Blank disables the
     * check, which is only appropriate for local development.
     */
    val owner: String = "",
)

/** An advertised syndication target. */
data class SyndicateTarget(
    val uid: String,
    val name: String,
)
