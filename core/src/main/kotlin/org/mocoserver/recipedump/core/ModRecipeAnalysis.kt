package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

private val modSerializers =
    mapOf(
        "create" to
            setOf(
                "crushing",
                "cutting",
                "haunting",
                "item_application",
                "milling",
                "pressing",
                "sandpaper_polishing",
                "splashing",
                "deploying",
                "mechanical_crafting",
            ),
        "mekanism" to setOf("crushing", "enriching", "combining", "sawing"),
        "farmersdelight" to setOf("cooking", "cutting"),
    )

internal fun evaluateModRecipe(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    val serializer = recipe.get("serializer").asString
    val mod = serializer.substringBefore(':')
    return when {
        serializer.substringAfter(':') !in modSerializers[mod].orEmpty() -> unknown("unsupported_serializer")
        mod == "create" -> evaluateCreateRecipe(dump, recipe, uses, produces)
        mod == "mekanism" -> evaluateMekanismRecipe(dump, recipe, uses, produces)
        else -> evaluateFarmersDelightRecipe(dump, recipe, uses, produces)
    }
}

internal fun finiteChance(value: JsonElement?): Double? =
    if (value?.isJsonPrimitive == true &&
        value.asJsonPrimitive.isNumber
    ) {
        value.asDouble.takeIf { it.isFinite() }
    } else {
        null
    }

internal data class OutputProbability(
    val chance: Double,
    val kind: String,
    val conditions: String? = null,
)

internal fun outputWithChance(
    value: JsonElement?,
    item: String,
    path: String,
    probability: OutputProbability,
): MatchEvaluation {
    val result = evaluateItemOutput(value, item, path)
    return result.copy(
        evidence =
            result.evidence.map {
                it.deepCopy().apply {
                    addProperty("chance", probability.chance)
                    addProperty("chanceKind", probability.kind)
                    if (probability.conditions != null) addProperty("conditions", probability.conditions)
                }
            },
    )
}
