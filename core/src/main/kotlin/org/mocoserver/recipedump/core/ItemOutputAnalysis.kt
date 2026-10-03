package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun positiveCount(value: JsonElement?): Int? {
    if (value?.isJsonPrimitive != true || !value.asJsonPrimitive.isNumber) return null
    return try {
        value.asBigDecimal.intValueExact().takeIf { it > 0 }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

internal fun evaluateItemOutput(value: JsonElement?, item: String, path: String): MatchEvaluation {
    val output = value.objectValue() ?: return unknown("invalid_shape", path)
    val id = output.get("id").stringValue()
    if (id == null || !isItemId(id)) return unknown("invalid_shape", "$path.id")
    val count = if (output.has("count")) positiveCount(output.get("count")) else 1
    if (count == null) return unknown("invalid_shape", "$path.count")
    if (id != item) return MatchEvaluation(MatchState.NO_MATCH)
    return MatchEvaluation(MatchState.MATCH, listOf(JsonObject().apply {
        addProperty("item", item)
        addProperty("path", path)
        addProperty("count", count)
    }))
}
