package dev.jacobandersen.forge.micropub

/** A malformed Micropub request; mapped to 400 `invalid_request`. */
class MicropubError(
    message: String,
) : RuntimeException(message)

/** A Micropub target that does not exist; mapped to 404. */
class MicropubNotFound(
    message: String,
) : RuntimeException(message)

/** The token lacks the scope the request requires; mapped to 401 `insufficient_scope`. */
class MicropubInsufficientScope(
    val scope: String,
) : RuntimeException("The token is missing the '$scope' scope")

/** The authenticated identity may not perform the request; mapped to 403 `forbidden`. */
class MicropubForbidden(
    message: String,
) : RuntimeException(message)
