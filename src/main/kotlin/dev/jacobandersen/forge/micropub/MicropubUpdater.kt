package dev.jacobandersen.forge.micropub

import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import org.springframework.stereotype.Component

/**
 * Micropub update operations (replace/add/delete) expressed in mf2 terms. A
 * `delete` entry with no values removes the whole property; with values it
 * removes only those values.
 */
data class MicropubUpdateOperations(
    val replace: Map<String, List<Mf2Value>> = emptyMap(),
    val add: Map<String, List<Mf2Value>> = emptyMap(),
    val delete: Map<String, List<Mf2Value>> = emptyMap(),
    val syndicateTo: List<String>? = null,
)

/**
 * Applies Micropub update operations to an mf2 document. Ordering follows the
 * spec's practical expectation: delete, then replace, then add. Forge merges
 * here because Bastion's update command takes the full resulting document.
 */
@Component
class MicropubUpdater {
    fun apply(
        document: Mf2Object,
        operations: MicropubUpdateOperations,
    ): Mf2Object {
        var result = document

        operations.delete.forEach { (key, values) ->
            result =
                if (values.isEmpty()) {
                    result.deleteProperty(key)
                } else {
                    val remaining = result.getProperty(key).filterNot { it in values }
                    if (remaining.isEmpty()) result.deleteProperty(key) else result.setProperty(key, remaining)
                }
        }

        operations.replace.forEach { (key, values) ->
            result = result.setProperty(key, values)
        }

        operations.add.forEach { (key, values) ->
            result = result.addProperty(key, values)
        }

        return result
    }
}
