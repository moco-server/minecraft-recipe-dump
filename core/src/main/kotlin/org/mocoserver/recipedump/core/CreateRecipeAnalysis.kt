package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun evaluateCreateRecipe(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    val data = recipe.getAsJsonObject("data")
    val serializer = recipe.get("serializer").asString
    val inputs =
        uses?.let {
            if (serializer ==
                "create:mechanical_crafting"
            ) {
                evaluateShapedInputs(data, dump, it)
            } else {
                createInputs(
                    data,
                    dump,
                    it,
                    serializer == "create:deploying",
                )
            }
        }
    val outputs =
        produces?.let {
            if (serializer ==
                "create:mechanical_crafting"
            ) {
                evaluateFixedItemOutput(data.get("result"), it, "$.result")
            } else {
                createOutputs(data.get("results"), it)
            }
        }
    return allMatch(listOfNotNull(inputs, outputs))
}

private fun createInputs(
    data: JsonObject,
    dump: JsonObject,
    item: String,
    deploying: Boolean,
): MatchEvaluation {
    val ingredients = data.get("ingredients")
    val held = data.get("keep_held_item")
    val invalidHeld = held != null && (held.isJsonPrimitive.not() || held.asJsonPrimitive.isBoolean.not())
    return when {
        ingredients?.isJsonArray != true || ingredients.asJsonArray.isEmpty -> unknown("invalid_shape", "$.ingredients")
        deploying && invalidHeld -> unknown("invalid_shape", "$.keep_held_item")
        deploying && ingredients.asJsonArray.size() != 2 -> unknown("invalid_shape", "$.ingredients")
        else ->
            anyMatch(
                ingredients.asJsonArray.mapIndexed { index, ingredient ->
                    val role =
                        if (deploying &&
                            index == 1 &&
                            held?.asBoolean == true
                        ) {
                            "retained-input"
                        } else {
                            "ingredient"
                        }
                    evaluateIngredient(ingredient, dump, item, "$.ingredients[$index]", role)
                },
            )
    }
}

private fun createOutputs(
    value: JsonElement?,
    item: String,
): MatchEvaluation =
    if (value?.isJsonArray !=
        true
    ) {
        unknown("invalid_shape", "$.results")
    } else {
        anyMatch(
            value.asJsonArray.mapIndexed { index, output ->
                val obj = output.objectValue()
                val chance = if (obj?.has("chance") == true) finiteChance(obj.get("chance")) else 1.0
                if (chance == null ||
                    chance <= 0 ||
                    !chance.toFloat().isFinite()
                ) {
                    unknown("invalid_shape", "$.results[$index].chance")
                } else {
                    outputWithChance(output, item, "$.results[$index]", OutputProbability(chance, "per-item-roll"))
                }
            },
        )
    }
