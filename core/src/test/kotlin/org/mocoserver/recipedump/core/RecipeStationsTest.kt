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
    fun `station table is version selected and sorted without inventory requirement`() {
        val expected =
            mapOf(
                "minecraft:crafting_table" to "minecraft:crafting",
                "minecraft:furnace" to "minecraft:smelting",
                "minecraft:blast_furnace" to "minecraft:blasting",
                "minecraft:smoker" to "minecraft:smoking",
                "minecraft:campfire" to "minecraft:campfire_cooking",
                "minecraft:soul_campfire" to "minecraft:campfire_cooking",
                "minecraft:stonecutter" to "minecraft:stonecutting",
                "minecraft:smithing_table" to "minecraft:smithing",
                "create:crushing_wheel" to "create:crushing",
                "create:millstone" to "create:milling",
                "create:mechanical_saw" to "create:cutting",
                "create:deployer" to "create:deploying",
                "mekanism:crusher" to "mekanism:crushing",
                "mekanism:enrichment_chamber" to "mekanism:enriching",
                "mekanism:precision_sawmill" to "mekanism:sawing",
                "farmersdelight:cooking_pot" to "farmersdelight:cooking",
                "farmersdelight:cutting_board" to "farmersdelight:cutting",
            )
        val result = DumpSearch.stations(dump())
        assertEquals(17, result.get("count").asInt)
        val rows = result.getAsJsonArray("stations").map { it.asJsonObject }
        assertEquals(expected.keys.sorted(), rows.map { it.get("id").asString })
        rows.forEach {
            assertEquals(
                listOf(expected[it.get("id").asString]),
                it.getAsJsonArray("types").map { type ->
                    type.asString
                },
            )
        }
        val old = dump().apply { remove("mods") }
        assertEquals(8, DumpSearch.stations(old).get("count").asInt)
        assertThrows(
            IllegalArgumentException::class.java,
        ) { DumpSearch.recipes(old, station = "create:crushing_wheel") }
        for (version in listOf("1.20.1", null)) {
            val unsupported =
                dump().apply {
                    if (version ==
                        null
                    ) {
                        remove("minecraft")
                    } else {
                        addProperty("minecraft", version)
                    }
                }
            assertThrows(IllegalArgumentException::class.java) { DumpSearch.stations(unsupported) }
            assertThrows(
                IllegalArgumentException::class.java,
            ) { DumpSearch.recipes(unsupported, station = "minecraft:furnace") }
        }
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
