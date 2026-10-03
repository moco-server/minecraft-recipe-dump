package org.mocoserver.recipedump.cli

internal const val CLI_USAGE: String =
    "Usage: recipe-dump summary|csv <dump.json> OR diff <before.json> <after.json>\n" +
        "       recipe-dump recipes <dump.json> [--id <id>] [--type <type>] [--namespace <namespace>]\n" +
        "       recipe-dump items <dump.json> [--tag <tag>] [--namespace <namespace>]"

internal data class SearchArguments(
    val id: String? = null,
    val type: String? = null,
    val namespace: String? = null,
    val tag: String? = null,
)

/** 使用法エラーをI/Oより先に診断するため、オプションと値の組をここで検証する。 */
internal fun parseSearchArguments(
    command: String,
    args: List<String>,
): SearchArguments {
    val allowed = if (command == "recipes") setOf("--id", "--type", "--namespace") else setOf("--tag", "--namespace")
    val values = mutableMapOf<String, String>()
    for (pair in args.chunked(2)) {
        val option = pair[0]
        require(option in allowed) { "Unknown option $option\n$CLI_USAGE" }
        require(option !in values) { "Duplicate option $option\n$CLI_USAGE" }
        val value = pair.getOrNull(1)
        require(value != null && value.isNotEmpty() && !value.startsWith("--")) {
            "Missing value for $option\n$CLI_USAGE"
        }
        values[option] = value
    }
    return SearchArguments(values["--id"], values["--type"], values["--namespace"], values["--tag"])
}
