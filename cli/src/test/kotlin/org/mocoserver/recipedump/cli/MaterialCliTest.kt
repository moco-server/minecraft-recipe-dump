package org.mocoserver.recipedump.cli

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class MaterialCliTest {
    @Test
    fun `missing shaped key remains a partial CLI result for both serializers`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        for (serializer in listOf("minecraft:crafting_shaped", "create:mechanical_crafting")) {
            Files.writeString(
                file,
                """
                {"schemaVersion":1,"complete":true,"errors":[],"minecraft":"1.21.1","itemTags":{},
                "mods":[{"id":"create","version":"6.0.10"}],"recipes":[{"id":"test:broken",
                "type":"minecraft:crafting","serializer":"$serializer","data":{"pattern":["AB"],
                "key":{"A":{"item":"minecraft:diamond"}},"result":{"id":"minecraft:diamond_block"}}}]}
                """.trimIndent(),
            )
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            assertEquals(
                2,
                runCli(
                    listOf("recipes", file.toString(), "--uses", "minecraft:diamond"),
                    PrintStream(out),
                    PrintStream(err),
                ),
            )
            assertEquals("", err.toString())
            val result = JsonParser.parseString(out.toString()).asJsonObject
            assertEquals(0, result.get("count").asInt)
            assertEquals(0, result.getAsJsonObject("analysis").get("analyzedCount").asInt)
            assertEquals(1, result.getAsJsonObject("analysis").get("unresolvedCount").asInt)
            val diagnostic = result.getAsJsonArray("diagnostics")[0].asJsonObject
            assertEquals("invalid_shape", diagnostic.get("code").asString)
            assertEquals("$.key.B", diagnostic.get("path").asString)
        }
    }

    @Test
    fun `material queries emit JSON with exhaustive or partial exit codes`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        Files.writeString(
            file,
            """
            {"schemaVersion":1,"complete":true,"errors":[],"minecraft":"1.21.1","itemTags":{},"recipes":[
            {"id":"test:a","type":"minecraft:smelting","serializer":"minecraft:smelting","data":{
            "ingredient":{"type":"test:custom"},"result":{"id":"test:out"},"custom":null}}]}
            """.trimIndent(),
        )
        for ((options, code, count) in listOf(
            Triple(listOf("--produces", "test:out"), 0, 1),
            Triple(listOf("--uses", "test:in"), 2, 0),
            Triple(listOf("--uses", "test:in", "--produces", "test:out"), 2, 0),
            Triple(listOf("--uses", "test:in", "--produces", "test:other"), 0, 0),
            Triple(listOf("--id", "test:absent", "--uses", "test:in"), 0, 0),
        )) {
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            assertEquals(
                code,
                runCli(listOf("recipes", file.toString()) + options, PrintStream(out), PrintStream(err)),
                options.toString(),
            )
            assertEquals("", err.toString())
            val result = JsonParser.parseString(out.toString()).asJsonObject
            assertEquals(count, result.get("count").asInt)
            assertEquals(code == 0, result.getAsJsonObject("analysis").get("exhaustive").asBoolean)
            if (count ==
                1
            ) {
                assertTrue(
                    result
                        .getAsJsonArray("recipes")[0]
                        .asJsonObject
                        .getAsJsonObject("data")
                        .has("custom"),
                )
            }
        }
    }

    @Test
    fun `invalid item IDs are rejected before IO`(
        @TempDir directory: Path,
    ) {
        for (option in listOf("--uses", "--produces")) {
            for (id in listOf("iron_ingot", "Minecraft:iron_ingot", "a:b:c", ":a", "a:")) {
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()
                assertEquals(
                    1,
                    runCli(
                        listOf("recipes", directory.resolve("missing.json").toString(), option, id),
                        PrintStream(out),
                        PrintStream(err),
                    ),
                )
                assertEquals("", out.toString())
                assertTrue(err.toString().contains("item ID"), err.toString())
            }
        }
    }
}
