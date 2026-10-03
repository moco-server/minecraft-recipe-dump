package org.mocoserver.recipedump.cli

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.mocoserver.recipedump.core.DumpJson
import org.mocoserver.recipedump.core.DumpReports
import org.mocoserver.recipedump.core.DumpSearch
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
        val search = validateArguments(args)
        val dump = DumpJson.decode(Files.readString(Path.of(args[1])))
        val gsonBuilder = GsonBuilder().setPrettyPrinting()
        // レシピ行全体の保持契約では、独自codecや追加フィールドの明示的なnullも失わせない。
        if (command == "recipes") gsonBuilder.serializeNulls()
        val gson = gsonBuilder.create()
        when (command) {
            "recipes" -> printRecipeSearch(dump, requireNotNull(search), gson, out)
            else -> {
                when (command) {
                    "stations" -> out.println(gson.toJson(DumpSearch.stations(dump)))
                    "items" -> out.println(gson.toJson(DumpSearch.items(dump, search?.tag, search?.namespace)))
                    "csv" -> out.print(DumpReports.csv(dump))
                    "diff" ->
                        out.println(
                            gson.toJson(DumpReports.diff(dump, DumpJson.decode(Files.readString(Path.of(args[2]))))),
                        )
                    else -> out.println(gson.toJson(DumpReports.summary(dump)))
                }
                0
            }
        }
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

private fun printRecipeSearch(
    dump: JsonObject,
    filters: SearchArguments,
    gson: Gson,
    out: PrintStream,
): Int {
    val result =
        DumpSearch.recipes(
            dump,
            filters.id,
            filters.type,
            filters.namespace,
            filters.uses,
            filters.produces,
            filters.station,
        )
    out.println(gson.toJson(result))
    return if (result.getAsJsonObject("analysis")?.get("exhaustive")?.asBoolean == false) 2 else 0
}

private fun validateArguments(args: List<String>): SearchArguments? =
    when (val command = args.firstOrNull()) {
        "recipes", "items" -> {
            require(args.size >= 2) { CLI_USAGE }
            parseSearchArguments(command, args.drop(2))
        }
        "summary", "csv", "stations" -> {
            require(args.size == 2) { CLI_USAGE }
            null
        }
        "diff" -> {
            require(args.size == DIFF_ARGUMENT_COUNT) { CLI_USAGE }
            null
        }
        else -> throw IllegalArgumentException(CLI_USAGE)
    }
