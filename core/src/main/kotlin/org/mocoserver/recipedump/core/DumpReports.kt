package org.mocoserver.recipedump.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** decode済みのスナップショットから、ゲームを実行せず表示用情報を作る。 */
public object DumpReports {
    /** レシピ数・アイテムタグ数・RecipeType別件数を返す。 */
    public fun summary(dump: JsonObject): JsonObject {
        val recipes = dump.getAsJsonArray("recipes")
        val byType = JsonObject()
        val counts = recipes.map { it.asJsonObject.get("type").asString }.groupingBy { it }.eachCount()
        counts.toSortedMap().forEach { (type, count) ->
            byType.addProperty(type, count)
        }
        return JsonObject().apply {
            addProperty("recipeCount", recipes.size())
            addProperty("itemTagCount", dump.getAsJsonObject("itemTags").size())
            add("byType", byType)
        }
    }

    /** 独自codecのJSONをdata列に保持したCSVを返す。末尾に改行を含む。 */
    public fun csv(dump: JsonObject): String =
        buildString {
            appendLine("id,type,serializer,data")
            for (element in dump.getAsJsonArray("recipes")) {
                val recipe = element.asJsonObject
                val values =
                    listOf("id", "type", "serializer").map { recipe.get(it).asString } + recipe.get("data").toString()
                appendLine(values.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        }

    /** ID単位の追加・削除・変更。object key順序を無視し、配列順序を維持する。タグ・構成差分は含まない。 */
    public fun diff(
        before: JsonObject,
        after: JsonObject,
    ): JsonObject {
        val old = index(before)
        val current = index(after)
        return JsonObject().apply {
            add("added", array((current.keys - old.keys).sorted()))
            add("removed", array((old.keys - current.keys).sorted()))
            add("changed", array(current.keys.filter { it in old && old[it] != current[it] }.sorted()))
        }
    }

    private fun index(dump: JsonObject): Map<String, JsonObject> =
        dump.getAsJsonArray("recipes").associate { it.asJsonObject.get("id").asString to it.asJsonObject }

    private fun array(values: List<String>): JsonArray = JsonArray().apply { values.forEach(::add) }
}
