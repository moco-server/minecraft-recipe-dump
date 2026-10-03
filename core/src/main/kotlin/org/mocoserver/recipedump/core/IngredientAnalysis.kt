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
    val ingredient = value.objectValue()
    return when {
        value?.isJsonArray == true ->
            anyMatch(
                value.asJsonArray.mapIndexed { index, child ->
                    evaluateIngredient(child, dump, item, "$path[$index]", role)
                },
            )
        ingredient == null -> unknown("invalid_shape", path)
        ingredient.has("type") -> unknown("unsupported_ingredient", "$path.type")
        else -> evaluateSimpleIngredient(ingredient, dump, item, path, role)
    }
}

private fun evaluateSimpleIngredient(
    ingredient: JsonObject,
    dump: JsonObject,
    item: String,
    path: String,
    role: String,
): MatchEvaluation {
    val field = if (ingredient.has("item")) "item" else "tag"
    val id = ingredient.get(field).stringValue()
    return when {
        ingredient.has("item") == ingredient.has("tag") -> unknown("invalid_shape", path)
        id == null || !isItemId(id) -> unknown("invalid_shape", "$path.$field")
        field == "tag" -> evaluateTag(dump, id, item, path, role)
        else -> ingredientMatch(id == item, item, path, role)
    }
}

private fun evaluateTag(
    dump: JsonObject,
    tag: String,
    item: String,
    path: String,
    role: String,
): MatchEvaluation {
    val candidates = dump.get("itemTags").objectValue()?.get(tag)
    return when {
        candidates == null -> unknown("missing_tag", "$path.tag")
        !candidates.isJsonArray -> unknown("invalid_shape", "$path.tag")
        candidates.asJsonArray.any {
            it.stringValue()?.let(
                ::isItemId,
            ) != true
        } -> unknown("invalid_shape", "$path.tag")
        else -> ingredientMatch(candidates.asJsonArray.any { it.asString == item }, item, path, role, tag)
    }
}

private fun ingredientMatch(
    matches: Boolean,
    item: String,
    path: String,
    role: String,
    tag: String? = null,
): MatchEvaluation =
    if (!matches) {
        MatchEvaluation(MatchState.NO_MATCH)
    } else {
        MatchEvaluation(
            MatchState.MATCH,
            listOf(
                JsonObject().apply {
                    addProperty("item", item)
                    addProperty("path", path)
                    addProperty("role", role)
                    if (tag != null) addProperty("tag", tag)
                },
            ),
        )
    }
