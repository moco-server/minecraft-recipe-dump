package org.mocoserver.recipedump

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.SharedConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.neoforged.fml.ModList
import java.util.UUID

/** Minecraft側の登録済みcodecを使い、現在のRecipeManagerを収集する。 */
object SnapshotCollector {
    /** サーバースレッドで呼ぶ。completeは現在のスナップショットの書き出し成功だけを示す。 */
    fun collect(server: MinecraftServer): JsonObject {
        val errors = JsonArray()
        val recipes = JsonArray()
        for (holder in server.recipeManager.recipes.sortedBy { it.id().toString() }) {
            recipes.add(encode(server, holder, errors))
        }
        return JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("runId", UUID.randomUUID().toString())
            addProperty("minecraft", SharedConstants.getCurrentVersion().name)
            addProperty("capturePhase", "first-server-tick-post-lowest")
            // 1.21.1のhadErrorsLoadingは解析失敗でもfalse。成功として扱わない。
            addProperty("loadDiagnostics", "not-tracked")
            add("mods", mods())
            add("selectedPacks", JsonArray().apply { server.packRepository.selectedIds.forEach(::add) })
            add("recipes", recipes)
            add("itemTags", itemTags())
            add(
                "items",
                JsonArray().apply {
                    BuiltInRegistries.ITEM
                        .keySet()
                        .map { it.toString() }
                        .sorted()
                        .forEach(::add)
                },
            )
            add("errors", errors)
            addProperty("complete", errors.isEmpty)
        }
    }

    // 外部MODの独自codecの失敗をレシピ単位で保持し、残りの収集を続ける。
    @Suppress("TooGenericExceptionCaught")
    private fun encode(
        server: MinecraftServer,
        holder: RecipeHolder<*>,
        errors: JsonArray,
    ): JsonObject {
        val row = JsonObject().apply { addProperty("id", holder.id().toString()) }
        try {
            val recipe = holder.value()
            row.addProperty("type", BuiltInRegistries.RECIPE_TYPE.getKey(recipe.type).toString())
            row.addProperty("serializer", BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.serializer).toString())
            val ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE)
            val json = Recipe.CODEC.encodeStart(ops, recipe).orThrow
            Recipe.CODEC.parse(ops, json).orThrow
            row.add("data", json)
        } catch (exception: Exception) {
            row.addProperty("error", exception.toString())
            errors.add(
                JsonObject().apply {
                    addProperty("id", holder.id().toString())
                    addProperty("message", exception.toString())
                },
            )
        }
        return row
    }

    private fun mods(): JsonArray =
        JsonArray().apply {
            for (mod in ModList.get().mods.sortedBy { it.modId }) {
                add(
                    JsonObject().apply {
                        addProperty("id", mod.modId)
                        addProperty("version", mod.version.toString())
                    },
                )
            }
        }

    private fun itemTags(): JsonObject =
        JsonObject().apply {
            BuiltInRegistries.ITEM.tags.sorted(compareBy { it.first.location().toString() }).forEach { pair ->
                val items = JsonArray()
                pair.second
                    .stream()
                    .map {
                        BuiltInRegistries.ITEM
                            .getKey(
                                it.value(),
                            ).toString()
                    }.sorted()
                    .forEach(items::add)
                add(pair.first.location().toString(), items)
            }
        }
}
