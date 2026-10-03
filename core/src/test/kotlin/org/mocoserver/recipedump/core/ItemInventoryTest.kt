package org.mocoserver.recipedump.core

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ItemInventoryTest {
    private fun dump(items: String? = null) =
        DumpJson.decode(
            """{"schemaVersion":1,"complete":true,"errors":[],"recipes":[],"itemTags":{"test:tag":["test:z","minecraft:air","test:z"]}${items?.let {
                ",\"items\":$it"
            }.orEmpty()}}""",
        )

    @Test
    fun `present inventory rejects malformed IDs null and duplicates`() {
        for (invalid in listOf(
            "null",
            "{}",
            "1",
            "[null]",
            "[1]",
            "[true]",
            "[\"\"]",
            "[\"air\"]",
            "[\"Minecraft:air\"]",
            "[\"a:b:c\"]",
            "[\"a:b\",\"a:b\"]",
        )) {
            assertThrows(IllegalArgumentException::class.java, { dump(invalid) }, invalid)
        }
        assertEquals(0, dump("[]").getAsJsonArray("items").size())
        assertFalse(dump().has("items"))
    }

    @Test
    fun `inventory search includes unreferenced items and exact namespace`() {
        val source = dump("[\"test_extra:a\",\"test:z\",\"minecraft:air\",\"test:unused\"]")
        val result = DumpSearch.items(source, namespace = "test")
        assertEquals(listOf("test:unused", "test:z"), result.getAsJsonArray("items").map { it.asString })
        assertEquals(2, result.get("count").asInt)
        assertEquals("test", result.get("namespace").asString)
        assertFalse(result.has("tag"))
        assertEquals(0, DumpSearch.items(source, namespace = "unknown").get("count").asInt)
        result.getAsJsonArray("items").add("test:changed")
        assertEquals(4, source.getAsJsonArray("items").size())
    }

    @Test
    fun `old dump tag search works but full inventory requires recollection`() {
        assertEquals(1, DumpSearch.items(dump(), tag = "test:tag", namespace = "test").get("count").asInt)
        assertEquals(DumpSearch.itemsByTag(dump(), "test:tag"), DumpSearch.items(dump(), tag = "test:tag"))
        assertThrows(IllegalArgumentException::class.java) { DumpSearch.items(dump()) }
    }

    @Test
    fun `atomic replacement retains custom null and failed save retains other files`(
        @TempDir directory: Path,
    ) {
        val output = directory.resolve("snapshot.json")
        Files.writeString(output, "old")
        val snapshot = dump()
        snapshot.add("custom", JsonParser.parseString("{\"value\":null}"))
        DumpJson.writeAtomic(output, snapshot)
        assertTrue(
            JsonParser
                .parseString(Files.readString(output))
                .asJsonObject
                .getAsJsonObject("custom")
                .has("value"),
        )
        val before = Files.readString(output)
        assertThrows(java.io.IOException::class.java) { DumpJson.writeAtomic(directory, snapshot) }
        assertEquals(before, Files.readString(output))
        Files.list(directory).use { assertEquals(1, it.count()) }
    }
}
