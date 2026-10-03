package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun evaluateMekanismRecipe(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    val data = recipe.getAsJsonObject("data")
    val serializer = recipe.get("serializer").asString
    val inputs =
        uses?.let { item ->
            val fields =
                if (serializer ==
                    "mekanism:combining"
                ) {
                    listOf("main_input", "extra_input")
                } else {
                    listOf("input")
                }
            anyMatch(fields.map { countedInput(data.get(it), dump, item, "$.$it") })
        }
    val outputs =
        produces?.let {
            if (serializer ==
                "mekanism:sawing"
            ) {
                sawingOutputs(data, it)
            } else {
                evaluateFixedItemOutput(data.get("output"), it, "$.output")
            }
        }
    return allMatch(listOfNotNull(inputs, outputs))
}

private fun countedInput(
    value: JsonElement?,
    dump: JsonObject,
    item: String,
    path: String,
): MatchEvaluation {
    val input = value.objectValue()
    return if (input?.has("count") == true &&
        positiveCount(input.get("count")) == null
    ) {
        unknown("invalid_shape", "$path.count")
    } else {
        evaluateIngredient(value, dump, item, path)
    }
}

private fun sawingOutputs(
    data: JsonObject,
    item: String,
): MatchEvaluation {
    val main = data.has("main_output")
    val secondary = data.has("secondary_output")
    val chance = finiteChance(data.get("secondary_chance"))
    return when {
        !main && !secondary -> unknown("invalid_shape", "$")
        !secondary && data.has("secondary_chance") -> unknown("invalid_shape", "$.secondary_chance")
        secondary && (chance == null || chance <= 0 || chance > 1 || (!main && chance == 1.0)) ->
            unknown(
                "invalid_shape",
                "$.secondary_chance",
            )
        else -> {
            val primary = if (main) evaluateFixedItemOutput(data.get("main_output"), item, "$.main_output") else null
            val extra =
                if (secondary) {
                    outputWithChance(
                        data.get("secondary_output"),
                        item,
                        "$.secondary_output",
                        OutputProbability(requireNotNull(chance), "secondary-output"),
                    )
                } else {
                    null
                }
            anyMatch(listOfNotNull(primary, extra))
        }
    }
}
