package org.mocoserver.recipedump.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** schema 1のスナップショットを扱う。completeは書き出し完了のみを示す。 */
public object DumpJson {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** 不完全・未対応形式・重複IDを拒否する。入力の読み込み診断は保証しない。 */
    public fun decode(text: String): JsonObject {
        val element = JsonParser.parseString(text)
        require(element.isJsonObject) { "Dump must be an object" }
        val dump = element.asJsonObject
        require(dump.get("schemaVersion")?.toString() == "1") { "Unsupported dump schemaVersion" }
        require(dump.get("complete")?.toString() == "true") { "Incomplete dump" }
        require(dump.get("errors")?.isJsonArray == true && dump.getAsJsonArray("errors").isEmpty) {
            "Dump has export errors"
        }
        require(dump.get("recipes")?.isJsonArray == true) { "Missing recipes array" }
        val ids = mutableSetOf<String>()
        for (row in dump.getAsJsonArray("recipes")) {
            require(row.isJsonObject) { "Recipe must be an object" }
            val recipe = row.asJsonObject
            for (field in listOf("id", "type", "serializer")) {
                val value = recipe.get(field)
                require(
                    value?.isJsonPrimitive == true && value.asJsonPrimitive.isString && value.asString.isNotBlank(),
                ) {
                    "Recipe requires a string $field"
                }
            }
            require(ids.add(recipe.get("id").asString)) { "Duplicate recipe ID" }
            require(recipe.get("data")?.isJsonObject == true) { "Recipe requires codec data" }
        }
        require(dump.get("itemTags")?.isJsonObject == true) { "Missing itemTags object" }
        return dump
    }

    /** 親ディレクトリを作り、完成したJSONだけをatomic moveで置き換える。失敗は呼出元へ返す。 */
    public fun writeAtomic(
        output: Path,
        dump: JsonObject,
    ) {
        val destination = output.toAbsolutePath()
        Files.createDirectories(destination.parent)
        val pending = Files.createTempFile(destination.parent, "recipe-dump-", ".tmp")
        try {
            Files.writeString(pending, gson.toJson(dump))
            Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(pending)
        }
    }
}
