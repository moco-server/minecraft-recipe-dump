package org.mocoserver.recipedump.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject

private val unsupportedCodes = setOf("unsupported_serializer", "unsupported_direction", "unsupported_version")

internal fun analyzeRecipes(
    dump: JsonObject,
    candidates: List<JsonObject>,
    uses: String?,
    produces: String?,
): JsonObject {
    val evaluated =
        candidates.map { row ->
            val evaluation =
                if (row.get("serializer").asString.startsWith("minecraft:")) {
                    evaluateVanillaRecipe(dump, row, uses, produces)
                } else {
                    evaluateModRecipe(dump, row, uses, produces)
                }
            row to evaluation
        }
    val matches = evaluated.filter { it.second.state == MatchState.MATCH }
    val undecidable = evaluated.filter { it.second.state == MatchState.UNKNOWN }
    val unresolved = undecidable.count { (_, evaluation) -> evaluation.issues.any { it.code !in unsupportedCodes } }
    val diagnostics =
        undecidable
            .flatMap { (row, evaluation) -> rowDiagnostics(row, evaluation) }
            .sortedWith(
                compareBy({
                    it.get("id").asString
                }, { it.get("path").stringValue().orEmpty() }, { it.get("code").asString }),
            )
    return JsonObject().apply {
        add("recipes", JsonArray().apply { matches.forEach { add(it.first.deepCopy()) } })
        addProperty("count", matches.size)
        add(
            "analysis",
            JsonObject().apply {
                addProperty("candidateCount", candidates.size)
                addProperty("analyzedCount", candidates.size - undecidable.size)
                addProperty("unsupportedCount", undecidable.size - unresolved)
                addProperty("unresolvedCount", unresolved)
                addProperty("exhaustive", undecidable.isEmpty())
            },
        )
        add("diagnostics", JsonArray().apply { diagnostics.forEach(::add) })
        add(
            "evidence",
            JsonArray().apply {
                matches.forEach { (row, evaluation) ->
                    add(rowEvidence(row, evaluation, uses, produces))
                }
            },
        )
    }
}

private fun rowDiagnostics(
    row: JsonObject,
    evaluation: MatchEvaluation,
): List<JsonObject> =
    evaluation.issues.distinct().map { issue ->
        JsonObject().apply {
            addProperty("id", row.get("id").asString)
            addProperty("serializer", row.get("serializer").asString)
            addProperty("code", issue.code)
            if (issue.path != null) addProperty("path", issue.path)
        }
    }

private fun rowEvidence(
    row: JsonObject,
    evaluation: MatchEvaluation,
    uses: String?,
    produces: String?,
): JsonObject =
    JsonObject().apply {
        addProperty("id", row.get("id").asString)
        if (uses !=
            null
        ) {
            add("uses", JsonArray().apply { evaluation.evidence.filter { it.has("role") }.forEach(::add) })
        }
        if (produces !=
            null
        ) {
            add("produces", JsonArray().apply { evaluation.evidence.filterNot { it.has("role") }.forEach(::add) })
        }
    }
