package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

private val vanillaSerializers = setOf(
    "minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting", "minecraft:blasting",
    "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting", "minecraft:smithing_transform", "minecraft:smithing_trim",
)

internal fun evaluateVanillaRecipe(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    if (dump.get("minecraft").stringValue() != "1.21.1") return unknown("unsupported_version")
    val serializer = recipe.get("serializer").asString
    if (serializer !in vanillaSerializers) return unknown("unsupported_serializer")
    val data = recipe.getAsJsonObject("data")
    val inputs = uses?.let { item ->
        when (serializer) {
            "minecraft:crafting_shaped" -> evaluateShapedInputs(data, dump, item)
            "minecraft:crafting_shapeless" -> evaluateIngredientList(data.get("ingredients"), dump, item, "$.ingredients")
            "minecraft:smithing_transform", "minecraft:smithing_trim" -> anyMatch(listOf("template", "base", "addition").map { evaluateIngredient(data.get(it), dump, item, "$.$it") })
            else -> evaluateIngredient(data.get("ingredient"), dump, item, "$.ingredient")
        }
    }
    val outputs = produces?.let { item ->
        if (serializer == "minecraft:smithing_trim") unknown("unsupported_direction")
        else evaluateItemOutput(data.get("result"), item, "$.result")
    }
    return allMatch(listOfNotNull(inputs, outputs))
}

internal fun evaluateIngredientList(value: JsonElement?, dump: JsonObject, item: String, path: String): MatchEvaluation {
    if (value?.isJsonArray != true || value.asJsonArray.isEmpty) return unknown("invalid_shape", path)
    return anyMatch(value.asJsonArray.mapIndexed { index, ingredient -> evaluateIngredient(ingredient, dump, item, "$path[$index]") })
}

internal fun evaluateShapedInputs(data: JsonObject, dump: JsonObject, item: String): MatchEvaluation {
    val pattern = data.get("pattern")
    val key = data.get("key").objectValue() ?: return unknown("invalid_shape", "$.key")
    if (pattern?.isJsonArray != true || pattern.asJsonArray.isEmpty) return unknown("invalid_shape", "$.pattern")
    val rows = pattern.asJsonArray.map { it.stringValue() }
    if (rows.any { it == null || it.isEmpty() } || rows.map { it?.length }.distinct().size != 1) return unknown("invalid_shape", "$.pattern")
    val symbols = rows.filterNotNull().flatMap { it.toList() }.filter { it != ' ' }.distinct()
    if (symbols.isEmpty()) return unknown("invalid_shape", "$.pattern")
    return anyMatch(symbols.map { symbol -> evaluateIngredient(key.get(symbol.toString()), dump, item, "$.key.$symbol") })
}
