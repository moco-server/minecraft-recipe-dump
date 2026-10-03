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

internal fun evaluateItemOutput(
    value: JsonElement?,
    item: String,
    path: String,
): MatchEvaluation {
    val output = value.objectValue()
    val id = output?.get("id").stringValue()
    val count = if (output?.has("count") == true) positiveCount(output.get("count")) else 1
    return when {
        output == null -> unknown("invalid_shape", path)
        id == null || !isItemId(id) -> unknown("invalid_shape", "$path.id")
        count == null -> unknown("invalid_shape", "$path.count")
        id != item -> MatchEvaluation(MatchState.NO_MATCH)
        else ->
            MatchEvaluation(
                MatchState.MATCH,
                listOf(
                    JsonObject().apply {
                        addProperty("item", item)
                        addProperty("path", path)
                        addProperty("count", count)
                    },
                ),
            )
    }
}
