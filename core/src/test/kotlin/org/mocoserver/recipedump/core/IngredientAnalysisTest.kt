package org.mocoserver.recipedump.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IngredientAnalysisTest {
    private val dump = JsonParser.parseString("""{"itemTags":{"test:present":["test:a"],"test:empty":[],"test:bad":[null]}}""").asJsonObject
    private fun evaluate(json: String) = evaluateIngredient(JsonParser.parseString(json), dump, "test:a", "$.ingredient")

    @Test
    fun `three valued OR prefers proof and AND prefers disproof`() {
        val match = MatchEvaluation(MatchState.MATCH)
        val no = MatchEvaluation(MatchState.NO_MATCH)
        val unknown = MatchEvaluation(MatchState.UNKNOWN, issues = listOf(AnalysisIssue("missing_tag")))
        assertEquals(MatchState.MATCH, anyMatch(listOf(unknown, match)).state)
        assertEquals(emptyList<AnalysisIssue>(), anyMatch(listOf(unknown, match)).issues)
        assertEquals(MatchState.UNKNOWN, anyMatch(listOf(unknown, no)).state)
        assertEquals(MatchState.NO_MATCH, anyMatch(listOf(no, no)).state)
        assertEquals(MatchState.NO_MATCH, allMatch(listOf(unknown, no)).state)
        assertEquals(MatchState.UNKNOWN, allMatch(listOf(unknown, match)).state)
        assertEquals(MatchState.MATCH, allMatch(listOf(match, match)).state)
    }

    @Test
    fun `ingredient items tags and alternatives return exact evidence`() {
        val item = evaluate("""{"item":"test:a"}""")
        assertEquals(MatchState.MATCH, item.state)
        assertEquals(JsonParser.parseString("""{"item":"test:a","path":"$.ingredient","role":"ingredient"}"""), item.evidence.single())
        assertEquals(MatchState.NO_MATCH, evaluate("""{"item":"test:b"}""").state)
        val tag = evaluate("""{"tag":"test:present"}""")
        assertEquals(MatchState.MATCH, tag.state)
        assertEquals("test:present", tag.evidence.single().get("tag").asString)
        assertEquals(MatchState.MATCH, evaluate("""[{"type":"test:unknown"},{"item":"test:a"}]""").state)
        assertEquals(MatchState.NO_MATCH, evaluate("[]").state)
        assertEquals(MatchState.NO_MATCH, evaluate("""{"tag":"test:empty"}""").state)
        assertEquals("missing_tag", evaluate("""{"tag":"test:missing"}""").issues.single().code)
        assertEquals("$.ingredient.tag", evaluate("""{"tag":"test:missing"}""").issues.single().path)
    }

    @Test
    fun `malformed and custom ingredients stay unknown instead of empty`() {
        for (invalid in listOf("null", "1", "true", "{}", "{\"item\":null}", "{\"item\":\"a\"}", "{\"item\":\"test:a\",\"tag\":\"test:empty\"}", "{\"tag\":\"test:bad\"}")) {
            val result = evaluate(invalid)
            assertEquals(MatchState.UNKNOWN, result.state, invalid)
            assertEquals("invalid_shape", result.issues.single().code, invalid)
        }
        assertEquals("unsupported_ingredient", evaluate("""{"type":"neoforge:components","items":"test:a"}""").issues.single().code)
        assertEquals(MatchState.MATCH, evaluateIngredient(JsonParser.parseString("""{"item":"test:a"}"""), JsonObject(), "test:a", "$", "tool").state)
    }
}
