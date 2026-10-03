package org.mocoserver.recipedump.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** 検証済みsnapshotを検索する。Minecraftの起動や独自codecの意味の解釈は行わない。 */
public object DumpSearch {
    /** 完全一致の条件をANDで適用し、ID順のレシピ行と件数を返す。結果は入力と可変状態を共有しない。 */
    public fun recipes(
        dump: JsonObject,
        id: String? = null,
        type: String? = null,
        namespace: String? = null,
    ): JsonObject {
        val matches =
            dump
                .getAsJsonArray("recipes")
                .map { it.asJsonObject }
                .filter { recipe ->
                    val recipeId = recipe.get("id").asString
                    (id == null || recipeId == id) &&
                        (type == null || recipe.get("type").asString == type) &&
                        (namespace == null || (':' in recipeId && recipeId.substringBefore(':') == namespace))
                }.sortedBy { it.get("id").asString }
        val rows = JsonArray()
        matches.forEach { rows.add(it.deepCopy()) }
        return JsonObject().apply {
            add("recipes", rows)
            addProperty("count", rows.size())
        }
    }

    /** 指定タグの解決済みIDを重複除去して昇順で返す。未登録は0件、不正な参照対象タグは拒否する。 */
    public fun itemsByTag(
        dump: JsonObject,
        tag: String,
    ): JsonObject {
        val value = dump.getAsJsonObject("itemTags").get(tag)
        require(value == null || value.isJsonArray) { "Item tag $tag must be an array" }
        val ids =
            value
                ?.asJsonArray
                ?.map { element ->
                    require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                        "Item tag $tag must contain only string IDs"
                    }
                    element.asString
                }.orEmpty()
                .distinct()
                .sorted()
        val items = JsonArray()
        ids.forEach { items.add(it) }
        return JsonObject().apply {
            addProperty("tag", tag)
            add("items", items)
            addProperty("count", items.size())
        }
    }
}
