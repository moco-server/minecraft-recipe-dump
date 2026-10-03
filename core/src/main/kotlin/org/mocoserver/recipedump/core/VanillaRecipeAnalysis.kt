package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

private val vanillaSerializers =
    setOf(
        "minecraft:crafting_shaped",
        "minecraft:crafting_shapeless",
        "minecraft:smelting",
        "minecraft:blasting",
        "minecraft:smoking",
        "minecraft:campfire_cooking",
        "minecraft:stonecutting",
        "minecraft:smithing_transform",
        "minecraft:smithing_trim",
    )

internal fun evaluateVanillaRecipe(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    val serializer = recipe.get("serializer").asString
    return when {
        dump.get("minecraft").stringValue() != "1.21.1" -> unknown("unsupported_version")
        serializer !in vanillaSerializers -> unknown("unsupported_serializer")
        else -> evaluateVanillaDirections(dump, recipe, uses, produces)
    }
}

private fun evaluateVanillaDirections(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    val serializer = recipe.get("serializer").asString
    val data = recipe.getAsJsonObject("data")
    val inputs =
        uses?.let { item ->
            when (serializer) {
                "minecraft:crafting_shaped" -> evaluateShapedInputs(data, dump, item)
                "minecraft:crafting_shapeless" ->
                    evaluateIngredientList(
                        data.get("ingredients"),
                        dump,
                        item,
                        "$.ingredients",
                    )
                "minecraft:smithing_transform", "minecraft:smithing_trim" ->
                    anyMatch(
                        listOf(
                            "template",
                            "base",
                            "addition",
                        ).map {
                            evaluateIngredient(data.get(it), dump, item, "$.$it")
                        },
                    )
                else -> evaluateIngredient(data.get("ingredient"), dump, item, "$.ingredient")
            }
        }
    val outputs =
        produces?.let { item ->
            if (serializer == "minecraft:smithing_trim") {
                unknown("unsupported_direction")
            } else {
                evaluateItemOutput(data.get("result"), item, "$.result")
            }
        }
    return allMatch(listOfNotNull(inputs, outputs))
}

internal fun evaluateIngredientList(
    value: JsonElement?,
    dump: JsonObject,
    item: String,
    path: String,
): MatchEvaluation {
    if (value?.isJsonArray != true || value.asJsonArray.isEmpty) return unknown("invalid_shape", path)
    return anyMatch(
        value.asJsonArray.mapIndexed {
            index,
            ingredient,
            ->
            evaluateIngredient(ingredient, dump, item, "$path[$index]")
        },
    )
}

internal fun evaluateShapedInputs(
    data: JsonObject,
    dump: JsonObject,
    item: String,
): MatchEvaluation {
    val pattern = data.get("pattern")
    val key = data.get("key").objectValue()
    val rows = if (pattern?.isJsonArray == true) pattern.asJsonArray.map { it.stringValue() } else emptyList()
    val symbols =
        rows
            .filterNotNull()
            .flatMap { it.toList() }
            .filter { it != ' ' }
            .distinct()
    return when {
        key == null -> unknown("invalid_shape", "$.key")
        rows.isEmpty() ||
            rows.any { it.isNullOrEmpty() } ||
            rows.map { it?.length }.distinct().size != 1 ||
            symbols.isEmpty() -> unknown("invalid_shape", "$.pattern")
        // key欠落は候補の未知枝ではなくpattern自体の不正。別keyの一致で構造違反を隠さない。
        symbols.any { !key.has(it.toString()) } ->
            MatchEvaluation(
                MatchState.UNKNOWN,
                issues = symbols.filter { !key.has(it.toString()) }.map { AnalysisIssue("invalid_shape", "$.key.$it") },
            )
        else -> anyMatch(symbols.map { evaluateIngredient(key.get(it.toString()), dump, item, "$.key.$it") })
    }
}
