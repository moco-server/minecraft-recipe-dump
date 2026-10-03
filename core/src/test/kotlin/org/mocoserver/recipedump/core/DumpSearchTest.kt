package org.mocoserver.recipedump.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DumpSearchTest {
    private fun fixture(): JsonObject =
        DumpJson.decode(
            """
            {"schemaVersion":1,"complete":true,"errors":[],"itemTags":{
            "minecraft:planks":["minecraft:oak_planks","minecraft:birch_planks","minecraft:oak_planks"],
            "example:empty":[]},"recipes":[
            {"id":"minecraft:c","type":"minecraft:smelting","serializer":"create:custom","data":{}},
            {"id":"create_extra:b","type":"minecraft:crafting","serializer":"create:custom","data":{}},
            {"id":"create:a","type":"minecraft:crafting","serializer":"minecraft:crafting_shapeless",
            "data":{"ingredient":{"item":"minecraft:iron_ingot"}},"extra":{"source":"probe"}}]}
            """.trimIndent(),
        )

    @Test
    fun `unfiltered recipes are sorted and counted`() {
        val result = DumpSearch.recipes(fixture())
        assertEquals(listOf("create:a", "create_extra:b", "minecraft:c"), ids(result))
        assertEquals(3, result.get("count").asInt)
    }

    @Test
    fun `ID and type filters require exact matches`() {
        val dump = fixture()
        assertEquals(listOf("create:a"), ids(DumpSearch.recipes(dump, id = "create:a")))
        assertEquals(listOf("create:a", "create_extra:b"), ids(DumpSearch.recipes(dump, type = "minecraft:crafting")))
        for (id in listOf("a", "create:", "unknown:a")) {
            assertEquals(0, DumpSearch.recipes(dump, id = id).get("count").asInt)
        }
        assertEquals(0, DumpSearch.recipes(dump, type = "crafting").get("count").asInt)
    }

    @Test
    fun `namespace matches recipe ID rather than serializer or prefix`() {
        val dump = fixture()
        assertEquals(listOf("create:a"), ids(DumpSearch.recipes(dump, namespace = "create")))
        assertEquals(listOf("minecraft:c"), ids(DumpSearch.recipes(dump, namespace = "minecraft")))
        assertEquals(0, DumpSearch.recipes(dump, namespace = "missing").get("count").asInt)
        dump.getAsJsonArray("recipes")[0].asJsonObject.addProperty("id", "create")
        assertEquals(listOf("create:a"), ids(DumpSearch.recipes(dump, namespace = "create")))
    }

    @Test
    fun `multiple filters are combined with AND`() {
        val dump = fixture()
        assertEquals(
            listOf("create:a"),
            ids(DumpSearch.recipes(dump, id = "create:a", type = "minecraft:crafting", namespace = "create")),
        )
        assertEquals(0, DumpSearch.recipes(dump, id = "create:a", type = "minecraft:smelting").get("count").asInt)
        assertEquals(
            0,
            DumpSearch.recipes(dump, type = "minecraft:crafting", namespace = "minecraft").get("count").asInt,
        )
    }

    @Test
    fun `recipe results preserve unknown fields without sharing mutable input`() {
        val dump = fixture()
        val before = dump.deepCopy()
        val result = DumpSearch.recipes(dump, id = "create:a")
        assertEquals(before.getAsJsonArray("recipes")[2], result.getAsJsonArray("recipes")[0])
        result
            .getAsJsonArray("recipes")[0]
            .asJsonObject
            .getAsJsonObject("extra")
            .addProperty("source", "changed")
        assertEquals(before, dump)
    }

    @Test
    fun `tag items are deduplicated and sorted`() {
        val dump = fixture()
        val before = dump.deepCopy()
        val result = DumpSearch.itemsByTag(dump, "minecraft:planks")
        assertEquals("minecraft:planks", result.get("tag").asString)
        assertEquals(
            listOf("minecraft:birch_planks", "minecraft:oak_planks"),
            result.getAsJsonArray("items").map {
                it.asString
            },
        )
        assertEquals(2, result.get("count").asInt)
        assertEquals(before, dump)
    }

    @Test
    fun `unknown empty and partial tags return zero items`() {
        for (tag in listOf("unknown:tag", "example:empty", "planks")) {
            val result = DumpSearch.itemsByTag(fixture(), tag)
            assertEquals(tag, result.get("tag").asString)
            assertEquals(0, result.get("count").asInt)
            assertEquals(0, result.getAsJsonArray("items").size())
        }
    }

    @Test
    fun `referenced tag must contain only string IDs`() {
        for (invalid in listOf("{}", "null", "123", "[123]", "[null]", "[true]", "[{}]")) {
            val dump = fixture()
            dump.getAsJsonObject("itemTags").add("minecraft:planks", JsonParser.parseString(invalid))
            assertThrows(IllegalArgumentException::class.java) { DumpSearch.itemsByTag(dump, "minecraft:planks") }
        }
    }

    @Test
    fun `malformed unrelated tag does not prevent selected tag search`() {
        val dump = fixture()
        dump.getAsJsonObject("itemTags").add("example:broken", JsonObject())
        assertEquals(2, DumpSearch.itemsByTag(dump, "minecraft:planks").get("count").asInt)
    }

    private fun ids(result: JsonObject): List<String> =
        result.getAsJsonArray("recipes").map {
            it.asJsonObject.get("id").asString
        }
}
