package org.mocoserver.recipedump.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** 検証済みsnapshotを検索する。Minecraftを起動せず、材料は版とserializerを限定して解釈する。 */
public object DumpSearch {
    /**
     * 完全一致の条件をANDで適用する。結果は入力と可変状態を共有しない。不正な検索アイテムIDは拒否する。
     * 既存の名前付き検索APIを維持するため、任意のAND条件を同じ入口で受ける。
     */
    @Suppress("LongParameterList")
    public fun recipes(
        dump: JsonObject,
        id: String? = null,
        type: String? = null,
        namespace: String? = null,
        uses: String? = null,
        produces: String? = null,
    ): JsonObject {
        require(uses == null || isItemId(uses)) { "Invalid uses item ID" }
        require(produces == null || isItemId(produces)) { "Invalid produces item ID" }
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
        if (uses != null || produces != null) return analyzeRecipes(dump, matches, uses, produces)
        val rows = JsonArray()
        matches.forEach { rows.add(it.deepCopy()) }
        return JsonObject().apply {
            add("recipes", rows)
            addProperty("count", rows.size())
        }
    }

    /** 対象版の完全修飾アイテムID構文を判定する。レジストリの存在確認はしない。 */
    public fun isItemId(value: String): Boolean =
        org.mocoserver.recipedump.core
            .isItemId(value)

    /** 任意のタグとnamespaceをANDで絞る。全件検索で一覧が欠落する旧ダンプは再収集が必要。 */
    public fun items(
        dump: JsonObject,
        tag: String? = null,
        namespace: String? = null,
    ): JsonObject {
        val value =
            if (tag != null) {
                dump.getAsJsonObject("itemTags").get(tag)
            } else {
                require(dump.has("items")) { "Item inventory is missing; recollect the dump to list all items" }
                dump.get("items")
            }
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
                .filter { namespace == null || (':' in it && it.substringBefore(':') == namespace) }
                .distinct()
                .sorted()
        val items = JsonArray()
        ids.forEach { items.add(it) }
        return JsonObject().apply {
            if (tag != null) addProperty("tag", tag)
            if (namespace != null) addProperty("namespace", namespace)
            add("items", items)
            addProperty("count", items.size())
        }
    }

    /** 指定タグの解決済みIDを重複除去して昇順で返す。未登録は0件、不正な参照対象タグは拒否する。 */
    public fun itemsByTag(
        dump: JsonObject,
        tag: String,
    ): JsonObject = items(dump, tag = tag)
}
