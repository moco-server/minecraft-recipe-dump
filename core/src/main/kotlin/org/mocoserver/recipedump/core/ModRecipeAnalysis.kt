package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal val supportedModVersions: Map<String, String> =
    mapOf(
        "create" to "6.0.10",
        "mekanism" to "10.7.19",
        "farmersdelight" to "1.3.4",
    )
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

internal fun supportsModVersion(
    dump: JsonObject,
    mod: String,
): Boolean {
    val mods = dump.get("mods")
    val matches =
        if (mods?.isJsonArray ==
            true
        ) {
            mods.asJsonArray.mapNotNull { it.objectValue() }.filter { it.get("id").stringValue() == mod }
        } else {
            emptyList()
        }
    return matches.size == 1 && matches.single().get("version").stringValue() == supportedModVersions[mod]
}

internal fun evaluateModRecipe(
    dump: JsonObject,
    recipe: JsonObject,
    uses: String?,
    produces: String?,
): MatchEvaluation {
    val serializer = recipe.get("serializer").asString
    val mod = serializer.substringBefore(':')
    return when {
        dump.get("minecraft").stringValue() != "1.21.1" -> unknown("unsupported_version")
        serializer.substringAfter(':') !in modSerializers[mod].orEmpty() -> unknown("unsupported_serializer")
        !supportsModVersion(dump, mod) -> unknown("unsupported_version")
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
