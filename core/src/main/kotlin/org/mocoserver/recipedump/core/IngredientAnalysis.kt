package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun evaluateIngredient(
    value: JsonElement?,
    dump: JsonObject,
    item: String,
    path: String,
    role: String = "ingredient",
): MatchEvaluation {
    if (value?.isJsonArray == true) {
        return anyMatch(value.asJsonArray.mapIndexed { index, child -> evaluateIngredient(child, dump, item, "$path[$index]", role) })
    }
    val ingredient = value.objectValue() ?: return unknown("invalid_shape", path)
    if (ingredient.has("type")) return unknown("unsupported_ingredient", "$path.type")
    if (ingredient.has("item") == ingredient.has("tag")) return unknown("invalid_shape", path)
    val field = if (ingredient.has("item")) "item" else "tag"
    val id = ingredient.get(field).stringValue()
    if (id == null || !isItemId(id)) return unknown("invalid_shape", "$path.$field")
    val matches = if (field == "item") {
        item == id
    } else {
        val tags = dump.get("itemTags").objectValue()
        val candidates = tags?.get(id) ?: return unknown("missing_tag", "$path.tag")
        if (!candidates.isJsonArray || candidates.asJsonArray.any { it.stringValue()?.let(::isItemId) != true }) {
            return unknown("invalid_shape", "$path.tag")
        }
        candidates.asJsonArray.any { it.asString == item }
    }
    if (!matches) return MatchEvaluation(MatchState.NO_MATCH)
    return MatchEvaluation(MatchState.MATCH, listOf(JsonObject().apply {
        addProperty("item", item)
        addProperty("path", path)
        addProperty("role", role)
        if (field == "tag") addProperty("tag", id)
    }))
}
