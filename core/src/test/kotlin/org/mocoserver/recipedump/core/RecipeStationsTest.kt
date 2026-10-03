package org.mocoserver.recipedump.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeStationsTest {
    private fun dump(): JsonObject =
        DumpJson.decode(
            """
            {"schemaVersion":1,"complete":true,"errors":[],"minecraft":"1.21.1","itemTags":{},
            "mods":[{"id":"create","version":"6.0.10"},{"id":"mekanism","version":"10.7.19"},
            {"id":"farmersdelight","version":"1.3.4"}],"recipes":[
            {"id":"test:dynamic","type":"minecraft:crafting","serializer":"test:dynamic","data":{}},
            {"id":"test:crush","type":"create:crushing","serializer":"create:crushing",
            "data":{"ingredients":[{"item":"minecraft:amethyst_block"}],"results":[{"id":"minecraft:amethyst_shard"}]}}]}
            """.trimIndent(),
        )

    @Test
    fun `station listing follows captured types without version or inventory requirements`() {
        for (header in listOf("{}", """{"minecraft":"future","mods":[{"id":"create","version":"future"}]}""")) {
            val snapshot =
                JsonParser.parseString(header).asJsonObject.apply {
                    add("recipes", dump().get("recipes"))
                    add("itemTags", dump().get("itemTags"))
                }
            val result = DumpSearch.stations(snapshot)
            assertEquals(2, result.get("count").asInt)
            assertEquals(
                JsonParser.parseString(
                    """[{"id":"create:crushing_wheel","types":["create:crushing"]},
                {"id":"minecraft:crafting_table","types":["minecraft:crafting"]}]""",
                ),
                result.get("stations"),
            )
            assertEquals(1, DumpSearch.recipes(snapshot, station = "create:crushing_wheel").get("count").asInt)
            assertEquals(0, DumpSearch.recipes(snapshot, station = "minecraft:furnace").get("count").asInt)
        }
        val empty = dump().apply { add("recipes", JsonParser.parseString("[]")) }
        assertEquals(0, DumpSearch.stations(empty).get("count").asInt)
    }

    @Test
    fun `station filters combine with metadata and materials without interpreting dynamic codecs`() {
        val dynamic = DumpSearch.recipes(dump(), station = "minecraft:crafting_table")
        assertEquals(
            "test:dynamic",
            dynamic
                .getAsJsonArray("recipes")[0]
                .asJsonObject
                .get("id")
                .asString,
        )
        assertFalse(dynamic.has("analysis"))
        assertEquals(JsonParser.parseString("[\"minecraft:crafting\"]"), dynamic.get("stationTypes"))
        assertEquals("minecraft:crafting_table", dynamic.get("station").asString)
        val crushed =
            DumpSearch.recipes(
                dump(),
                station = "create:crushing_wheel",
                produces = "minecraft:amethyst_shard",
            )
        assertEquals(1, crushed.get("count").asInt)
        assertTrue(crushed.getAsJsonObject("analysis").get("exhaustive").asBoolean)
        assertEquals(
            0,
            DumpSearch
                .recipes(
                    dump(),
                    station = "create:crushing_wheel",
                    type = "minecraft:crafting",
                ).get("count")
                .asInt,
        )
        assertEquals(0, DumpSearch.recipes(dump(), station = "minecraft:furnace").get("count").asInt)
        assertThrows(IllegalArgumentException::class.java) { DumpSearch.recipes(dump(), station = "create:unknown") }
        assertThrows(IllegalArgumentException::class.java) { DumpSearch.recipes(dump(), station = "furnace") }
    }
}
