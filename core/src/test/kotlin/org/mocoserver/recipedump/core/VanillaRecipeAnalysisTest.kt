package org.mocoserver.recipedump.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VanillaRecipeAnalysisTest {
    private val dump = JsonParser.parseString("""{"minecraft":"1.21.1","itemTags":{}}""").asJsonObject

    private fun fixture(name: String): JsonObject =
        JsonParser
            .parseString(
                requireNotNull(javaClass.getResource("/recipes/vanilla/$name.json")).readText(),
            ).asJsonObject

    private fun query(
        row: JsonObject,
        uses: String? = null,
        produces: String? = null,
    ) = evaluateVanillaRecipe(dump, row, uses, produces)

    @Test
    fun `missing used key is structural even when another key matches`() {
        val row = fixture("crafting_shaped")
        row.getAsJsonObject("data").add("pattern", JsonParser.parseString("[\"AB\"]"))
        row.getAsJsonObject("data").add("key", JsonParser.parseString("""{"A":{"item":"minecraft:diamond"}}"""))
        val result = query(row, uses = "minecraft:diamond")
        assertEquals(MatchState.UNKNOWN, result.state)
        assertEquals(listOf(AnalysisIssue("invalid_shape", "$.key.B")), result.issues)
        assertEquals(MatchState.MATCH, query(row, produces = "minecraft:acacia_boat").state)
    }

    @Test
    fun `real vanilla codecs support every declared direction`() {
        for ((serializer, input, output) in listOf(
            Triple("crafting_shaped", "minecraft:acacia_planks", "minecraft:acacia_boat"),
            Triple("crafting_shapeless", "minecraft:acacia_planks", "minecraft:acacia_button"),
            Triple("smelting", "minecraft:potato", "minecraft:baked_potato"),
            Triple("blasting", "minecraft:coal_ore", "minecraft:coal"),
            Triple("smoking", "minecraft:potato", "minecraft:baked_potato"),
            Triple("campfire_cooking", "minecraft:potato", "minecraft:baked_potato"),
            Triple("stonecutting", "minecraft:andesite", "minecraft:andesite_slab"),
            Triple("smithing_transform", "minecraft:diamond_axe", "minecraft:netherite_axe"),
        )) {
            val row = fixture(serializer)
            assertEquals(MatchState.MATCH, query(row, uses = input).state, serializer)
            assertEquals(MatchState.MATCH, query(row, produces = output).state, serializer)
            assertEquals(MatchState.MATCH, query(row, input, output).state, serializer)
            assertEquals(MatchState.NO_MATCH, query(row, produces = "test:absent").state, serializer)
            assertEquals(
                output,
                query(row, produces = output)
                    .evidence
                    .single()
                    .get("item")
                    .asString,
            )
        }
        val trim = fixture("smithing_trim")
        assertEquals(MatchState.MATCH, query(trim, uses = "minecraft:bolt_armor_trim_smithing_template").state)
        assertEquals(
            "unsupported_direction",
            query(trim, produces = "minecraft:diamond_chestplate").issues.single().code,
        )
    }

    @Test
    fun `only requested directions and shaped used keys are evaluated`() {
        val row = fixture("crafting_shaped")
        val data = row.getAsJsonObject("data")
        data.getAsJsonObject("key").add("X", JsonParser.parseString("""{"item":"test:unused"}"""))
        assertEquals(MatchState.NO_MATCH, query(row, uses = "test:unused").state)
        data.getAsJsonObject("key").remove("#")
        assertEquals("invalid_shape", query(row, uses = "minecraft:acacia_planks").issues.single().code)
        assertEquals(MatchState.MATCH, query(row, produces = "minecraft:acacia_boat").state)
        val shapeless = fixture("crafting_shapeless")
        shapeless.getAsJsonObject("data").add("ingredients", JsonParser.parseString("[]"))
        assertEquals("invalid_shape", query(shapeless, uses = "minecraft:acacia_planks").issues.single().code)
        assertEquals(
            MatchState.NO_MATCH,
            query(shapeless, uses = "minecraft:acacia_planks", produces = "test:absent").state,
        )
    }

    @Test
    fun `invalid outputs are unknown and default count is one`() {
        for (shape in listOf(
            "null",
            "\"minecraft:acacia_boat\"",
            "{}",
            "{\"id\":null}",
            "{\"id\":\"boat\"}",
            "{\"id\":\"minecraft:acacia_boat\",\"count\":0}",
            "{\"id\":\"minecraft:acacia_boat\",\"count\":-1}",
            "{\"id\":\"minecraft:acacia_boat\",\"count\":1.5}",
            "{\"id\":\"minecraft:acacia_boat\",\"count\":\"1\"}",
        )) {
            val row = fixture("crafting_shaped")
            row.getAsJsonObject("data").add("result", JsonParser.parseString(shape))
            assertEquals("invalid_shape", query(row, produces = "minecraft:acacia_boat").issues.single().code, shape)
        }
        val row = fixture("crafting_shaped")
        row.getAsJsonObject("data").getAsJsonObject("result").remove("count")
        assertEquals(
            1,
            query(row, produces = "minecraft:acacia_boat")
                .evidence
                .single()
                .get("count")
                .asInt,
        )
        row.getAsJsonObject("data").remove("result")
        assertEquals(MatchState.UNKNOWN, query(row, produces = "minecraft:acacia_boat").state)
    }

    @Test
    fun `unsupported versions and serializers are diagnosed`() {
        val row = fixture("smelting")
        for (version in listOf("1.20.1", null)) {
            val other = dump.deepCopy()
            if (version == null) other.remove("minecraft") else other.addProperty("minecraft", version)
            assertEquals(
                "unsupported_version",
                evaluateVanillaRecipe(other, row, "minecraft:potato", null).issues.single().code,
            )
        }
        row.addProperty("serializer", "minecraft:crafting_special_repairitem")
        assertEquals("unsupported_serializer", query(row, produces = "minecraft:diamond").issues.single().code)
    }
}
