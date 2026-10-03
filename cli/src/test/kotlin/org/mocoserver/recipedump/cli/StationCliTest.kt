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

class StationCliTest {
    @Test
    fun `station listing and dynamic station material searches use proper exit codes`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("dump.json")
        Files.writeString(
            file,
            """
            {"schemaVersion":1,"complete":true,"errors":[],"minecraft":"1.21.1","itemTags":{},"recipes":[
            {"id":"test:a","type":"minecraft:crafting","serializer":"test:dynamic","data":{}}]}
            """.trimIndent(),
        )
        for ((args, code, count) in listOf(
            Triple(listOf("stations", file.toString()), 0, 8),
            Triple(listOf("recipes", file.toString(), "--station", "minecraft:crafting_table"), 0, 1),
            Triple(
                listOf(
                    "recipes",
                    file.toString(),
                    "--station",
                    "minecraft:crafting_table",
                    "--produces",
                    "minecraft:diamond",
                ),
                2,
                0,
            ),
            Triple(listOf("recipes", file.toString(), "--station", "minecraft:furnace"), 0, 0),
        )) {
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            assertEquals(code, runCli(args, PrintStream(out), PrintStream(err)))
            assertEquals("", err.toString())
            assertEquals(
                count,
                JsonParser
                    .parseString(out.toString())
                    .asJsonObject
                    .get("count")
                    .asInt,
            )
        }
    }

    @Test
    fun `invalid station arguments and unknown station fail explicitly`(
        @TempDir directory: Path,
    ) {
        for (args in listOf(
            listOf("stations", "missing.json", "extra"),
            listOf("recipes", "missing.json", "--station", "furnace"),
            listOf("recipes", "missing.json", "--station", "minecraft:furnace", "--station", "minecraft:furnace"),
        )) {
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            assertEquals(1, runCli(args, PrintStream(out), PrintStream(err)))
            assertEquals("", out.toString())
            assertTrue(err.toString().contains("Usage:"))
        }
        val file = directory.resolve("dump.json")
        Files.writeString(
            file,
            """
            {"schemaVersion":1,"complete":true,"errors":[],"minecraft":"1.21.1","itemTags":{},"recipes":[]}
            """.trimIndent(),
        )
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        assertEquals(
            1,
            runCli(listOf("recipes", file.toString(), "--station", "test:unknown"), PrintStream(out), PrintStream(err)),
        )
        assertEquals("", out.toString())
        assertTrue(err.toString().contains("station"))
    }
}
