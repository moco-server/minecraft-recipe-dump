package org.mocoserver.recipedump

import com.mojang.logging.LogUtils
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.mocoserver.recipedump.core.DumpJson
import java.nio.file.Path

/** 専用サーバーで明示指定された時だけ収集し、通常の保存・終了処理へ戻す。 */
@Mod(RecipeDumpMod.ID)
object RecipeDumpMod {
    const val ID = "minecraft_recipe_dump"
    private var captured = false

    init {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER && java.lang.Boolean.getBoolean("recipedump.enabled")) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ::afterTick)
        }
    }

    // 外部MODのcodecが任意の例外を投げても、この実行は保存・正常停止を試みる。
    @Suppress("TooGenericExceptionCaught")
    private fun afterTick(event: ServerTickEvent.Post) {
        if (captured || !event.server.isDedicatedServer) return
        captured = true
        try {
            val output = Path.of(System.getProperty("recipedump.output", "recipes.json")).toAbsolutePath()
            val dump = SnapshotCollector.collect(event.server)
            DumpJson.writeAtomic(output, dump)
            LogUtils.getLogger().info(
                "RECIPE_DUMP_SAVED path={} recipes={} errors={} runId={}",
                output,
                dump.getAsJsonArray("recipes").size(),
                dump.getAsJsonArray("errors").size(),
                dump.get("runId").asString,
            )
        } catch (exception: Exception) {
            LogUtils.getLogger().error("RECIPE_DUMP_FAILED", exception)
        } finally {
            // サーバースレッド自身をjoinせず、既存の停止・保存フローを利用する。
            event.server.halt(false)
        }
    }
}
