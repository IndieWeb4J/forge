# Project agent memory

Forge is the Micropub protocol facade for Jacob's site. It is **stateless**: no database, no S3,
no event bus. It parses Micropub requests, validates tokens, and issues synchronous commands to
Bastion, the authoritative content service.

## Responsibilities

- `/micropub` write (POST create; PATCH / `action=update`; DELETE / `action=delete|undelete`).
- `/micropub/media` forwards bytes to Bastion's internal media command and returns its URL.
- `GET /micropub?q=...` (`config`, `source`, `properties`, `syndicate-to`) is translated over
  `content-client`'s read API so Bastion stays Micropub-ignorant.
- Token validation via `sigil-client` (`TokenIntrospector`) in `MicropubTokenFilter`.

## Dependencies

- `mf24j` - mf2 model + parser; Forge parses to mf2 but does **no** post-type discovery.
- `content-client` - the write/read/media contract with Bastion (`dev.jacobandersen:content-client`).
- `sigil-client` - token introspection (`dev.jacobandersen:sigil-client`).

## Build and test

- `./gradlew test` runs the suite (MockMvc + mocked content/sigil clients); `./gradlew ktlintCheck`
  lints (`ktlintFormat` fixes).
- Configuration: `content.client.base-url` (Bastion), `sigil.client.base-url` (Sigil),
  `forge.media-endpoint`, `forge.syndicate-to`. See `src/main/resources/application.yaml`.
- Jackson 3 (`tools.jackson.*`), not Jackson 2.
