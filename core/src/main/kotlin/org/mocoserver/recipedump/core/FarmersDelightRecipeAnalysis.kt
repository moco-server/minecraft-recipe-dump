package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun evaluateFarmersDelightRecipe(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    val data = recipe.getAsJsonObject("data")
    val cutting = recipe.get("serializer").asString == "farmersdelight:cutting"
    val inputs =
        uses?.let { item ->
            val materials = evaluateIngredientList(data.get("ingredients"), dump, item, "$.ingredients")
            if (cutting) {
                anyMatch(
                    listOf(materials, evaluateIngredient(data.get("tool"), dump, item, "$.tool", "tool")),
                )
            } else {
                materials
            }
        }
    val outputs =
        produces?.let {
            if (cutting) {
                cuttingOutputs(
                    data.get("result"),
                    it,
                )
            } else {
                evaluateFixedItemOutput(data.get("result"), it, "$.result")
            }
        }
    return allMatch(listOfNotNull(inputs, outputs))
}

private fun cuttingOutputs(
    value: JsonElement?,
    item: String,
): MatchEvaluation =
    if (value?.isJsonArray !=
        true
    ) {
        unknown("invalid_shape", "$.result")
    } else {
        anyMatch(
            value.asJsonArray.mapIndexed { index, output ->
                val obj = output.objectValue()
                val chance = if (obj?.has("chance") == true) finiteChance(obj.get("chance")) else 1.0
                if (chance == null || !chance.toFloat().isFinite()) {
                    unknown("invalid_shape", "$.result[$index].chance")
                } else {
                    outputWithChance(
                        obj?.get("item"),
                        item,
                        "$.result[$index].item",
                        OutputProbability(chance, "base-chance", "fortune-and-server-config"),
                    )
                }
            },
        )
    }
