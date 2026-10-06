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

## Micropub conformance

- Authentication accepts the token from the `Authorization` header or an `access_token`
  form parameter (Micropub 5.1 requires both). The filter introspects via Sigil, rejects a
  token whose `me` is not `forge.owner` with `403 forbidden`, and stashes granted scopes on
  the request.
- Writes require the matching scope (`create`/`update`/`delete`/`undelete`/`media`); a
  missing scope is `401 insufficient_scope` (Micropub 3.8 uses 401, not 403), with the
  required scope in the body and `WWW-Authenticate`.
- `MicropubExceptionHandler` (global `@RestControllerAdvice`) shapes every error as
  `{"error": ..., "error_description": ...}`; malformed JSON and unknown `action`/missing
  `url` are `400`, downstream `ContentClientException` is `502`.
- `q=source`/`q=properties` accept both `properties` and `properties[]`.
- `forge.owner` (env `FORGE_OWNER`) must be set in every non-local deployment; blank
  disables identity enforcement.

## Dependencies

- `microformats2` - mf2 model + parser; Forge parses to mf2 but does **no** post-type discovery.
- `content-client` - the write/read/media contract with Bastion (`dev.jacobandersen:content-client`).
- `sigil-client` - token introspection (`dev.jacobandersen:sigil-client`).

## Build and test

- `./gradlew test` runs the suite (MockMvc + mocked content/sigil clients); `./gradlew ktlintCheck`
  lints (`ktlintFormat` fixes).
- Configuration: `content.client.base-url` (Bastion), `sigil.client.base-url` (Sigil),
  `forge.media-endpoint`, `forge.syndicate-to`, `forge.owner`. See `src/main/resources/application.yaml`.
- Jackson 3 (`tools.jackson.*`), not Jackson 2.
