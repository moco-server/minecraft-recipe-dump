package org.mocoserver.recipedump.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DumpReportsTest {
    private val fixture =
        """
        {"schemaVersion":1,"complete":true,"errors":[],"itemTags":{},"recipes":[{
        "id":"example:one","type":"minecraft:crafting","serializer":"minecraft:crafting_shapeless",
        "data":{"result":{"id":"minecraft:diamond","count":1}}}]}
        """.trimIndent()

    @Test
    fun `summary and CSV retain actual recipe information`() {
        val dump = DumpJson.decode(fixture)
        assertEquals(1, DumpReports.summary(dump).get("recipeCount").asInt)
        assertTrue(DumpReports.csv(dump).contains("\"\"minecraft:diamond\"\""))
    }

    @Test
    fun `diff ignores object order and detects quantity changes`() {
        val before = DumpJson.decode(fixture)
        val reordered =
            DumpJson.decode(
                fixture.replace("\"id\":\"minecraft:diamond\",\"count\":1", "\"count\":1,\"id\":\"minecraft:diamond\""),
            )
        assertEquals(0, DumpReports.diff(before, reordered).getAsJsonArray("changed").size())
        val after = DumpJson.decode(fixture.replace("\"count\":1", "\"count\":2"))
        assertEquals("example:one", DumpReports.diff(before, after).getAsJsonArray("changed")[0].asString)
    }

    @Test
    fun `incomplete unknown schema and duplicate IDs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DumpJson.decode(fixture.replace("\"complete\":true", "\"complete\":false"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DumpJson.decode(fixture.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        }
        val dump = DumpJson.decode(fixture)
        dump.getAsJsonArray("recipes").add(dump.getAsJsonArray("recipes")[0].deepCopy())
        assertThrows(IllegalArgumentException::class.java) { DumpJson.decode(dump.toString()) }
        assertThrows(IllegalArgumentException::class.java) {
            DumpJson.decode(fixture.replace("\"data\":{", "\"missingData\":{ "))
        }
    }

    @Test
    fun `atomic writer replaces a snapshot and does not leave temporary files`(
        @TempDir directory: Path,
    ) {
        val output = directory.resolve("snapshot.json")
        Files.writeString(output, "old")
        DumpJson.writeAtomic(output, DumpJson.decode(fixture))
        assertEquals(1, DumpReports.summary(DumpJson.decode(Files.readString(output))).get("recipeCount").asInt)
        Files.list(directory).use { assertEquals(1, it.count()) }
        assertThrows(java.io.IOException::class.java) { DumpJson.writeAtomic(directory, DumpJson.decode(fixture)) }
    }
}
