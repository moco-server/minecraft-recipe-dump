package org.mocoserver.recipedump.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class CliTest {
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
