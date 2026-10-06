package dev.jacobandersen.forge.micropub

import dev.jacobandersen.mf24j.Mf2Value
import dev.jacobandersen.mf24j.json.toMf2ValueOrNull
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * Parses Micropub update operations from either the JSON form (`PATCH` with
 * `replace`/`add`/`delete`) or the form-encoded `action=update` syntax
 * (`replace[prop]`, `add[prop]`, `delete[prop]`).
 */
@Component
class MicropubUpdateParser(
    private val objectMapper: ObjectMapper,
) {
    fun fromJson(body: String): MicropubUpdateOperations {
        val node =
            objectMapper.readTree(body)
                ?: throw MicropubError("update body is not valid JSON")
        if (node !is ObjectNode) throw MicropubError("update body must be an object")
        if (node["action"]?.asString() != null && node["action"].asString() != "update") {
            throw MicropubError("unsupported action: ${node["action"].asString()}")
        }
        return MicropubUpdateOperations(
            replace = propertyMap(node["replace"]),
            add = propertyMap(node["add"]),
            delete = deleteMap(node["delete"]),
            syndicateTo = node["mp-syndicate-to"]?.let { stringList(it) },
        )
    }

    fun fromForm(params: Map<String, List<String>>): MicropubUpdateOperations {
        val replace = bracketMap(params, "replace")
        val add = bracketMap(params, "add")
        val delete = bracketMap(params, "delete")
        return MicropubUpdateOperations(
            replace = replace,
            add = add,
            delete = delete,
            syndicateTo = params["mp-syndicate-to"]?.filter { it.isNotBlank() },
        )
    }

    private fun propertyMap(node: JsonNode?): Map<String, List<Mf2Value>> {
        val objectNode = node as? ObjectNode ?: return emptyMap()
        val result = linkedMapOf<String, List<Mf2Value>>()
        objectNode.properties().forEach { (key, value) ->
            result[key] = values(value)
        }
        return result
    }

    private fun deleteMap(node: JsonNode?): Map<String, List<Mf2Value>> =
        when (node) {
            null -> emptyMap()
            is ArrayNode -> node.filter { it.isString }.associate { it.asString() to emptyList() }
            is ObjectNode -> propertyMap(node)
            else -> emptyMap()
        }

    private fun values(node: JsonNode): List<Mf2Value> {
        val array = node as? ArrayNode ?: return listOfNotNull(node.toMf2ValueOrNull())
        return array.mapNotNull { it.toMf2ValueOrNull() }
    }

    private fun stringList(node: JsonNode): List<String> =
        when (node) {
            is ArrayNode -> node.filter { it.isString }.map { it.asString() }
            else -> emptyList()
        }

    private fun bracketMap(
        params: Map<String, List<String>>,
        prefix: String,
    ): Map<String, List<Mf2Value>> {
        val pattern = Regex("""^$prefix\[([^]]+)]""")
        val result = linkedMapOf<String, MutableList<Mf2Value>>()
        params.forEach { (key, values) ->
            val match = pattern.find(key) ?: return@forEach
            val property = match.groupValues[1]
            val target = result.getOrPut(property) { mutableListOf() }
            values.filter { it.isNotBlank() }.forEach { target.add(Mf2Value.String(it)) }
        }
        return result
    }
}
