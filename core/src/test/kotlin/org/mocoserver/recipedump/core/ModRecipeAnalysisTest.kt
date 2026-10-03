package org.mocoserver.recipedump.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModRecipeAnalysisTest {
    private fun fixture(name: String): JsonObject =
        JsonParser
            .parseString(
                requireNotNull(javaClass.getResource("/recipes/mods/$name.json")).readText(),
            ).asJsonObject

    private fun metadata() = fixture("metadata")

    private fun query(
        row: JsonObject,
        uses: String? = null,
        produces: String? = null,
        dump: JsonObject = metadata(),
    ) = evaluateModRecipe(dump, row, uses, produces)

    @Test
    fun `fixed MOD outputs disclose their declared count and chance one`() {
        for ((name, output) in listOf(
            "create_mechanical_crafting" to "create:crushing_wheel",
            "mekanism_crushing" to "minecraft:andesite",
            "mekanism_sawing" to "farmersdelight:cooked_chicken_cuts",
            "farmersdelight_cooking" to "farmersdelight:apple_cider",
        )) {
            val evidence = query(fixture(name), produces = output).evidence.single()
            assertEquals(1.0, evidence.get("chance")?.asDouble, name)
        }
    }

    @Test
    fun `pinned MOD codecs support every declared serializer and direction`() {
        val cases =
            listOf(
                Triple("create_crushing", "minecraft:amethyst_block", "minecraft:amethyst_shard"),
                Triple("create_cutting", "create:andesite_alloy", "create:shaft"),
                Triple("create_haunting", "minecraft:cobblestone", "minecraft:blackstone"),
                Triple("create_item_application", "create:andesite_alloy", "create:andesite_casing"),
                Triple("create_milling", "minecraft:allium", "minecraft:magenta_dye"),
                Triple("create_pressing", "create:brass_ingot", "create:brass_sheet"),
                Triple("create_sandpaper_polishing", "create:rose_quartz", "create:polished_rose_quartz"),
                Triple("create_splashing", "create:crushed_raw_copper", "create:copper_nugget"),
                Triple("create_deploying", "minecraft:exposed_chiseled_copper", "minecraft:chiseled_copper"),
                Triple("create_mechanical_crafting", "create:andesite_alloy", "create:crushing_wheel"),
                Triple("mekanism_crushing", "minecraft:polished_andesite", "minecraft:andesite"),
                Triple("mekanism_enriching", "mekanism:dust_charcoal", "minecraft:charcoal"),
                Triple("mekanism_combining", "minecraft:white_dye", "minecraft:light_gray_dye"),
                Triple("mekanism_sawing", "minecraft:cooked_chicken", "minecraft:bone_meal"),
                Triple("farmersdelight_cooking", "minecraft:apple", "farmersdelight:apple_cider"),
                Triple("farmersdelight_cutting", "minecraft:acacia_log", "minecraft:stripped_acacia_log"),
            )
        for ((name, input, output) in cases) {
            val row = fixture(name)
            assertEquals(MatchState.MATCH, query(row, uses = input).state, name)
            assertEquals(MatchState.MATCH, query(row, produces = output).state, name)
            assertEquals(MatchState.MATCH, query(row, input, output).state, name)
            assertEquals(MatchState.NO_MATCH, query(row, produces = "test:absent").state, name)
            row.addProperty("id", "other:recipe")
            assertEquals(MatchState.MATCH, query(row, produces = output).state, name)
        }
    }

    @Test
    fun `wrong versions and unlisted fluid chemical serializers stay unsupported`() {
        for (name in listOf("create_crushing", "mekanism_crushing", "farmersdelight_cutting")) {
            val row = fixture(name)
            val missing = metadata().apply { remove("mods") }
            assertEquals(
                "unsupported_version",
                query(row, produces = "minecraft:stone", dump = missing).issues.single().code,
            )
            val wrong =
                metadata().apply {
                    getAsJsonArray(
                        "mods",
                    ).forEach { it.asJsonObject.addProperty("version", "other") }
                }
            assertEquals("unsupported_version", query(row, uses = "minecraft:stone", dump = wrong).issues.single().code)
        }
        for (serializer in listOf(
            "create:mixing",
            "create:compacting",
            "create:filling",
            "create:emptying",
            "create:sequenced_assembly",
            "mekanism:crystallizing",
            "mekanism:energy_conversion",
        )) {
            val row = fixture("create_crushing").apply { addProperty("serializer", serializer) }
            assertEquals(
                "unsupported_serializer",
                query(row, produces = "minecraft:amethyst_shard").issues.single().code,
            )
        }
    }

    @Test
    fun `Create chance boundaries and held input roles remain explicit`() {
        val row = fixture("create_crushing")
        assertEquals(1.0, query(row, produces = "minecraft:amethyst_shard").evidence[0].get("chance").asDouble)
        assertEquals(0.5, query(row, produces = "minecraft:amethyst_shard").evidence[1].get("chance").asDouble)
        for (invalid in listOf("0", "-1", "null", "\"1\"", "1e999")) {
            // 不正な候補を別の一致出力で隠さないよう、各回独立した1出力の行にする。
            val single = fixture("create_cutting")
            single
                .getAsJsonObject(
                    "data",
                ).getAsJsonArray("results")[0]
                .asJsonObject
                .add("chance", JsonParser.parseString(invalid))
            assertEquals("invalid_shape", query(single, produces = "create:shaft").issues.single().code, invalid)
        }
        val single = fixture("create_cutting")
        single
            .getAsJsonObject("data")
            .getAsJsonArray("results")[0]
            .asJsonObject
            .addProperty("chance", 2.0)
        assertEquals(MatchState.MATCH, query(single, produces = "create:shaft").state)
        assertEquals(
            "per-item-roll",
            query(single, produces = "create:shaft")
                .evidence
                .single()
                .get("chanceKind")
                .asString,
        )
        val deploying = query(fixture("create_deploying"), uses = "minecraft:iron_axe")
        assertEquals(
            "retained-input",
            deploying.evidence
                .single()
                .get("role")
                .asString,
        )
    }

    @Test
    fun `Mekanism validates counted inputs and optional saw outputs`() {
        val row = fixture("mekanism_sawing")
        val data = row.getAsJsonObject("data")
        assertEquals(
            "secondary-output",
            query(row, produces = "minecraft:bone_meal")
                .evidence
                .single()
                .get("chanceKind")
                .asString,
        )
        data.remove("main_output")
        assertEquals("invalid_shape", query(row, produces = "minecraft:bone_meal").issues.single().code)
        data.addProperty("secondary_chance", 0.5)
        assertEquals(MatchState.MATCH, query(row, produces = "minecraft:bone_meal").state)
        for (invalid in listOf("0", "-0.1", "1.1", "null", "\"0.5\"", "1e999")) {
            data.add("secondary_chance", JsonParser.parseString(invalid))
            assertEquals("invalid_shape", query(row, produces = "minecraft:bone_meal").issues.single().code, invalid)
        }
        data.remove("secondary_chance")
        assertEquals("invalid_shape", query(row, produces = "minecraft:bone_meal").issues.single().code)
        data.remove("secondary_output")
        assertEquals("invalid_shape", query(row, produces = "minecraft:bone_meal").issues.single().code)
        val mainOnly = fixture("mekanism_sawing")
        mainOnly.getAsJsonObject("data").remove("secondary_output")
        assertEquals(
            "invalid_shape",
            query(mainOnly, produces = "farmersdelight:cooked_chicken_cuts").issues.single().code,
        )
        mainOnly.getAsJsonObject("data").remove("secondary_chance")
        assertEquals(MatchState.MATCH, query(mainOnly, produces = "farmersdelight:cooked_chicken_cuts").state)
        for (count in listOf("0", "-1", "1.5", "null")) {
            val crushing = fixture("mekanism_crushing")
            crushing.getAsJsonObject("data").getAsJsonObject("input").add("count", JsonParser.parseString(count))
            assertEquals("invalid_shape", query(crushing, uses = "minecraft:polished_andesite").issues.single().code)
            assertEquals(MatchState.MATCH, query(crushing, produces = "minecraft:andesite").state)
        }
    }

    @Test
    fun `FD base chance and tool candidates do not claim guaranteed output`() {
        val row = fixture("farmersdelight_cutting")
        val output = row.getAsJsonObject("data").getAsJsonArray("result")[0].asJsonObject
        output.addProperty("chance", 0.0)
        val result = query(row, produces = "minecraft:stripped_acacia_log")
        assertEquals(MatchState.MATCH, result.state)
        assertEquals(
            "base-chance",
            result.evidence
                .single()
                .get("chanceKind")
                .asString,
        )
        assertEquals(
            "fortune-and-server-config",
            result.evidence
                .single()
                .get("conditions")
                .asString,
        )
        assertEquals(
            0.0,
            result.evidence
                .single()
                .get("chance")
                .asDouble,
        )
        assertEquals(
            "tool",
            query(row, uses = "minecraft:iron_axe")
                .evidence
                .single()
                .get("role")
                .asString,
        )
        assertEquals(MatchState.UNKNOWN, query(row, uses = "test:absent").state)
        output.add("chance", JsonParser.parseString("\"invalid\""))
        row.getAsJsonObject("data").getAsJsonArray("result").remove(1)
        assertEquals("invalid_shape", query(row, produces = "minecraft:stripped_acacia_log").issues.single().code)
        val cooking = fixture("farmersdelight_cooking")
        cooking.getAsJsonObject("data").add("container", JsonParser.parseString("""{"item":"minecraft:bowl"}"""))
        assertEquals(MatchState.NO_MATCH, query(cooking, uses = "minecraft:bowl").state)
    }
}
