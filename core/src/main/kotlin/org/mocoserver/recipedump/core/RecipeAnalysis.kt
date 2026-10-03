package org.mocoserver.recipedump.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal enum class MatchState { MATCH, NO_MATCH, UNKNOWN }

internal data class AnalysisIssue(
    val code: String,
    val path: String? = null,
)

internal data class MatchEvaluation(
    val state: MatchState,
    val evidence: List<JsonObject> = emptyList(),
    val issues: List<AnalysisIssue> = emptyList(),
)

// 不明な枝を空集合にしない。確定した結果の診断は検索の不明件数に混ぜない。
internal fun anyMatch(values: List<MatchEvaluation>): MatchEvaluation =
    when {
        values.any { it.state == MatchState.MATCH } ->
            MatchEvaluation(
                MatchState.MATCH,
                values
                    .filter {
                        it.state ==
                            MatchState.MATCH
                    }.flatMap { it.evidence },
            )
        values.all { it.state == MatchState.NO_MATCH } -> MatchEvaluation(MatchState.NO_MATCH)
        else -> MatchEvaluation(MatchState.UNKNOWN, issues = values.flatMap { it.issues })
    }

internal fun allMatch(values: List<MatchEvaluation>): MatchEvaluation =
    when {
        values.any { it.state == MatchState.NO_MATCH } -> MatchEvaluation(MatchState.NO_MATCH)
        values.all { it.state == MatchState.MATCH } -> MatchEvaluation(MatchState.MATCH, values.flatMap { it.evidence })
        else -> MatchEvaluation(MatchState.UNKNOWN, issues = values.flatMap { it.issues })
    }

internal fun unknown(
    code: String,
    path: String? = null,
): MatchEvaluation = MatchEvaluation(MatchState.UNKNOWN, issues = listOf(AnalysisIssue(code, path)))

internal fun JsonElement?.stringValue(): String? =
    if (this?.isJsonPrimitive == true && asJsonPrimitive.isString) asString else null

internal fun JsonElement?.objectValue(): JsonObject? = if (this?.isJsonObject == true) asJsonObject else null
