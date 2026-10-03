package org.mocoserver.recipedump.cli

import com.google.gson.JsonNull
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class CliTest {
    private val fixture =
        """
        {"schemaVersion":1,"complete":true,"errors":[],"itemTags":{
        "minecraft:planks":["minecraft:oak_planks","minecraft:birch_planks","minecraft:oak_planks"]},"recipes":[
        {"id":"minecraft:c","type":"minecraft:smelting","serializer":"create:custom","data":{}},
        {"id":"create_extra:b","type":"minecraft:crafting","serializer":"create:custom","data":{}},
        {"id":"create:a","type":"minecraft:crafting","serializer":"minecraft:crafting_shapeless",
        "data":{"result":{"id":"minecraft:diamond","count":1}},"extra":"retained"}]}
        """.trimIndent()

    @Test
    fun `recipe commands apply exact filters and emit sorted JSON`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        Files.writeString(file, fixture)
        val cases =
            listOf(
                emptyList<String>() to listOf("create:a", "create_extra:b", "minecraft:c"),
                listOf("--id", "create:a") to listOf("create:a"),
                listOf("--type", "minecraft:crafting") to listOf("create:a", "create_extra:b"),
                listOf("--namespace", "create") to listOf("create:a"),
                listOf("--namespace", "create", "--type", "minecraft:crafting", "--id", "create:a") to
                    listOf("create:a"),
                listOf("--namespace", "minecraft", "--type", "minecraft:crafting") to emptyList(),
                listOf("--id", "a") to emptyList(),
                listOf("--id", "missing:recipe") to emptyList(),
                listOf("--type", "missing:type") to emptyList(),
                listOf("--namespace", "missing") to emptyList(),
            )
        for ((options, expectedIds) in cases) {
            val result = execute(listOf("recipes", file.toString()) + options)
            assertEquals(0, result.code, options.toString())
            assertEquals("", result.errors)
            val json = JsonParser.parseString(result.output).asJsonObject
            assertEquals(expectedIds, json.getAsJsonArray("recipes").map { it.asJsonObject.get("id").asString })
            assertEquals(expectedIds.size, json.get("count").asInt)
            if ("create:a" in expectedIds) {
                assertEquals(
                    "retained",
                    json
                        .getAsJsonArray("recipes")[0]
                        .asJsonObject
                        .get("extra")
                        .asString,
                )
            }
        }
    }

    @Test
    fun `recipe JSON preserves explicit null fields`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        val input = JsonParser.parseString(fixture).asJsonObject
        val recipe = input.getAsJsonArray("recipes")[2].asJsonObject
        recipe.add("nullable", JsonNull.INSTANCE)
        recipe.getAsJsonObject("data").add("custom", JsonNull.INSTANCE)
        recipe.getAsJsonObject("data").add("list", JsonParser.parseString("[{\"custom\":null},null]"))
        recipe.getAsJsonObject("data").getAsJsonObject("result").add("custom", JsonNull.INSTANCE)
        Files.writeString(file, input.toString())
        val result = execute(listOf("recipes", file.toString(), "--id", "create:a"))
        assertEquals(0, result.code)
        assertEquals("", result.errors)
        val row =
            JsonParser
                .parseString(result.output)
                .asJsonObject
                .getAsJsonArray("recipes")[0]
                .asJsonObject
        assertEquals(recipe, row)
        assertEquals(JsonNull.INSTANCE, row.get("nullable"))
        assertEquals(JsonNull.INSTANCE, row.getAsJsonObject("data").get("custom"))
    }

    @Test
    fun `item command resolves tag and accepts zero matches`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        Files.writeString(file, fixture)
        for ((tag, expected) in listOf(
            "minecraft:planks" to listOf("minecraft:birch_planks", "minecraft:oak_planks"),
            "missing:tag" to emptyList(),
            "planks" to emptyList(),
        )) {
            val result = execute(listOf("items", file.toString(), "--tag", tag))
            assertEquals(0, result.code)
            assertEquals("", result.errors)
            val json = JsonParser.parseString(result.output).asJsonObject
            assertEquals(tag, json.get("tag").asString)
            assertEquals(expected, json.getAsJsonArray("items").map { it.asString })
            assertEquals(expected.size, json.get("count").asInt)
        }
    }

    @Test
    fun `bad search arguments are rejected before input is opened`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("missing.json").toString()
        val invalid =
            listOf(
                listOf("recipes"),
                listOf("items"),
                listOf("recipes", file, "--unknown", "value"),
                listOf("recipes", file, "--id", "create:a", "--id", "create:a"),
                listOf("recipes", file, "--type", "minecraft:crafting", "--type", "minecraft:crafting"),
                listOf("recipes", file, "--namespace", "create", "--namespace", "create"),
                listOf("recipes", file, "--id"),
                listOf("recipes", file, "--id", "--type", "minecraft:crafting"),
                listOf("recipes", file, "--id", ""),
                listOf("recipes", file, "extra"),
                listOf("recipes", file, "--id=create:a"),
                listOf("recipes", file, "--tag", "minecraft:planks"),
                listOf("items", file, "--tag"),
                listOf("items", file, "--tag", ""),
                listOf("items", file, "--tag", "minecraft:planks", "--tag", "minecraft:planks"),
                listOf("items", file, "--id", "minecraft:oak_planks"),
            )
        for (args in invalid) {
            val result = execute(args)
            assertEquals(1, result.code, args.toString())
            assertEquals("", result.output)
            assertTrue(result.errors.contains("Usage:"), args.toString())
        }
    }

    @Test
    fun `search rejects incomplete dumps and malformed selected tags`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        Files.writeString(file, fixture.replace("\"complete\":true", "\"complete\":false"))
        for (args in listOf(
            listOf("recipes", file.toString()),
            listOf("items", file.toString(), "--tag", "missing:tag"),
        )) {
            val result = execute(args)
            assertEquals(1, result.code)
            assertEquals("", result.output)
            assertTrue(result.errors.isNotBlank())
        }
        val malformed = JsonParser.parseString(fixture).asJsonObject
        malformed.getAsJsonObject("itemTags").add("minecraft:planks", JsonParser.parseString("[123]"))
        Files.writeString(file, malformed.toString())
        val result = execute(listOf("items", file.toString(), "--tag", "minecraft:planks"))
        assertEquals(1, result.code)
        assertEquals("", result.output)
        assertTrue(result.errors.contains("minecraft:planks"))
    }

    @Test
    fun `existing reports retain their output contracts`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        Files.writeString(file, fixture)
        val summary = execute(listOf("summary", file.toString()))
        assertEquals(0, summary.code)
        assertEquals("", summary.errors)
        assertEquals(
            3,
            JsonParser
                .parseString(summary.output)
                .asJsonObject
                .get("recipeCount")
                .asInt,
        )
        val csv = execute(listOf("csv", file.toString()))
        assertEquals(0, csv.code)
        assertEquals("", csv.errors)
        assertTrue(csv.output.startsWith("id,type,serializer,data\n"))
        assertTrue(csv.output.contains("\"\"minecraft:diamond\"\""))
        val diff = execute(listOf("diff", file.toString(), file.toString()))
        assertEquals(0, diff.code)
        assertEquals("", diff.errors)
        assertEquals(
            JsonParser.parseString("{\"added\":[],\"removed\":[],\"changed\":[]}"),
            JsonParser.parseString(diff.output),
        )
    }

    @Test
    fun `inventory commands preserve old tags and require recollection for old listing`(@TempDir directory: Path) {
        val file = directory.resolve("dump.json")
        Files.writeString(file, fixture)
        val old = execute(listOf("items", file.toString()))
        assertEquals(1, old.code)
        assertTrue(old.errors.contains("recollect"))
        assertEquals("", old.output)
        val tag = execute(listOf("items", file.toString(), "--tag", "minecraft:planks", "--namespace", "minecraft"))
        assertEquals(0, tag.code)
        assertEquals(2, JsonParser.parseString(tag.output).asJsonObject.get("count").asInt)
        val input = JsonParser.parseString(fixture).asJsonObject
        input.add("items", JsonParser.parseString("[\"create:z\",\"minecraft:air\",\"create:a\"]"))
        Files.writeString(file, input.toString())
        for ((options, expected) in listOf(
            emptyList<String>() to listOf("create:a", "create:z", "minecraft:air"),
            listOf("--namespace", "create") to listOf("create:a", "create:z"),
            listOf("--namespace", "missing") to emptyList(),
        )) {
            val result = execute(listOf("items", file.toString()) + options)
            assertEquals(0, result.code)
            assertEquals("", result.errors)
            assertEquals(expected, JsonParser.parseString(result.output).asJsonObject.getAsJsonArray("items").map { it.asString })
        }
    }

    private data class Result(
        val code: Int,
        val output: String,
        val errors: String,
    )

    private fun execute(args: List<String>): Result {
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val code = runCli(args, PrintStream(output), PrintStream(errors))
        return Result(code, output.toString(Charsets.UTF_8), errors.toString(Charsets.UTF_8))
    }

    @Test
    fun `missing input and invalid usage return failure`(
        @TempDir directory: Path,
    ) {
        val errors = ByteArrayOutputStream()
        val output = ByteArrayOutputStream()
        assertEquals(
            1,
            runCli(
                listOf("summary", directory.resolve("missing.json").toString()),
                PrintStream(output),
                PrintStream(errors),
            ),
        )
        assertEquals(1, runCli(listOf("unknown"), PrintStream(output), PrintStream(errors)))
        assertTrue(output.toString().isEmpty())
        assertTrue(errors.toString().contains("Usage:"))
    }

    @Test
    fun `invalid JSON returns an error without output`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("broken.json")
        Files.writeString(file, "{broken")
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        assertEquals(1, runCli(listOf("summary", file.toString()), PrintStream(output), PrintStream(errors)))
        assertTrue(output.toString().isEmpty())
        assertTrue(errors.toString().isNotBlank())
    }
}
