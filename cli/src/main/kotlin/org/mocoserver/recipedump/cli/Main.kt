package org.mocoserver.recipedump.cli

import com.google.gson.GsonBuilder
import org.mocoserver.recipedump.core.DumpJson
import org.mocoserver.recipedump.core.DumpReports
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val DIFF_ARGUMENT_COUNT = 3

/** 使用法・不完全ダンプ・I/Oエラーは非ゼロ終了する。Minecraftの起動は行わない。 */
fun main(args: Array<String>) {
    exitProcess(runCli(args.toList(), System.out, System.err))
}

internal fun runCli(
    args: List<String>,
    out: PrintStream,
    err: PrintStream,
): Int =
    try {
        val command = args.firstOrNull()
        require(
            (command in listOf("summary", "csv") && args.size == 2) ||
                (command == "diff" && args.size == DIFF_ARGUMENT_COUNT),
        ) { "Usage: recipe-dump summary|csv <dump.json> OR diff <before.json> <after.json>" }
        val dump = DumpJson.decode(Files.readString(Path.of(args[1])))
        val gson = GsonBuilder().setPrettyPrinting().create()
        when (command) {
            "csv" -> out.print(DumpReports.csv(dump))
            "diff" ->
                out.println(
                    gson.toJson(DumpReports.diff(dump, DumpJson.decode(Files.readString(Path.of(args[2]))))),
                )
            else -> out.println(gson.toJson(DumpReports.summary(dump)))
        }
        0
    } catch (exception: IllegalArgumentException) {
        err.println(exception.message)
        1
    } catch (exception: IOException) {
        err.println(exception.message)
        1
    } catch (exception: com.google.gson.JsonParseException) {
        err.println(exception.message)
        1
    }
