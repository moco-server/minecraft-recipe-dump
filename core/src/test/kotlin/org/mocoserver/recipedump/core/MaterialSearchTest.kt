package org.mocoserver.recipedump.core

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaterialSearchTest {
    private fun dump() =
        DumpJson.decode(
            """
            {"schemaVersion":1,"complete":true,"errors":[],"minecraft":"1.21.1","itemTags":{},"recipes":[
            {"id":"test:z","type":"minecraft:smelting","serializer":"minecraft:smelting","data":{"ingredient":{"tag":"test:missing"},"result":{"id":"test:out"}}},
            {"id":"test:a","type":"minecraft:smelting","serializer":"minecraft:smelting","data":{"ingredient":{"item":"test:in"},"result":{"id":"test:out","count":2},"custom":null}},
            {"id":"test:b","type":"minecraft:crafting","serializer":"test:unknown","data":{"result":{"id":"test:out"}}},
            {"id":"test:c","type":"minecraft:smelting","serializer":"minecraft:smelting","data":{"ingredient":{"item":"test:other"},"result":{"id":"test:out"}}},
            {"id":"test:d","type":"minecraft:smithing","serializer":"minecraft:smithing_trim","data":{"template":{"type":"test:unknown"},"base":{"item":"test:in"},"addition":[]}}]}
            """.trimIndent(),
        )

    @Test
    fun `material partition and diagnostics disclose every undecidable candidate`() {
        val source = dump()
        val result = DumpSearch.recipes(source, uses = "test:in", produces = "test:out")
        assertEquals(
            JsonParser.parseString(
                """{"candidateCount":5,"analyzedCount":2,"unsupportedCount":2,"unresolvedCount":1,
                "exhaustive":false}""",
            ),
            result.get("analysis"),
        )
        assertEquals(1, result.get("count").asInt)
        assertEquals(
            listOf("test:b", "test:d", "test:z"),
            result.getAsJsonArray("diagnostics").map {
                it.asJsonObject.get("id").asString
            },
        )
        val evidence = result.getAsJsonArray("evidence")[0].asJsonObject
        assertEquals(
            "$.ingredient",
            evidence
                .getAsJsonArray("uses")[0]
                .asJsonObject
                .get("path")
                .asString,
        )
        assertEquals(
            2,
            evidence
                .getAsJsonArray("produces")[0]
                .asJsonObject
                .get("count")
                .asInt,
        )
        assertTrue(
            result
                .getAsJsonArray("recipes")[0]
                .asJsonObject
                .getAsJsonObject("data")
                .get("custom")
                .isJsonNull,
        )
        result.getAsJsonArray("recipes")[0].asJsonObject.addProperty("id", "changed")
        assertEquals(
            "test:a",
            source
                .getAsJsonArray("recipes")[1]
                .asJsonObject
                .get("id")
                .asString,
        )
    }

    @Test
    fun `metadata filters precede analysis and known false dominates unknown`() {
        val zero = DumpSearch.recipes(dump(), namespace = "missing", uses = "test:in")
        assertTrue(zero.getAsJsonObject("analysis").get("exhaustive").asBoolean)
        assertEquals(0, zero.getAsJsonObject("analysis").get("candidateCount").asInt)
        assertFalse(DumpSearch.recipes(dump()).has("analysis"))
        val falseAndUnknown = DumpSearch.recipes(dump(), id = "test:z", uses = "test:in", produces = "test:other")
        assertTrue(falseAndUnknown.getAsJsonObject("analysis").get("exhaustive").asBoolean)
        assertEquals(1, falseAndUnknown.getAsJsonObject("analysis").get("analyzedCount").asInt)
        val row = dump()
        row
            .getAsJsonArray(
                "recipes",
            )[4]
            .asJsonObject
            .getAsJsonObject("data")
            .add("base", JsonParser.parseString("""{"tag":"test:missing"}"""))
        val bothUnknown = DumpSearch.recipes(row, id = "test:d", uses = "test:in", produces = "test:out")
        assertEquals(1, bothUnknown.getAsJsonObject("analysis").get("unresolvedCount").asInt)
        assertEquals(0, bothUnknown.getAsJsonObject("analysis").get("unsupportedCount").asInt)
    }
}
