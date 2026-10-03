package org.mocoserver.recipedump.tests

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.mocoserver.recipedump.RecipeDumpMod
import org.mocoserver.recipedump.SnapshotCollector
import org.mocoserver.recipedump.core.DumpJson

/** 本来のレシピ収集とcodecの往復を、ゲーム内の状態で確認する。 */
@GameTestHolder(RecipeDumpMod.ID)
@PrefixGameTestTemplate(false)
object RecipeDumpGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun collectsLoadedRecipes(helper: GameTestHelper) {
        val server = requireNotNull(helper.level.server)
        val dump = DumpJson.decode(SnapshotCollector.collect(server).toString())
        val ids = dump.getAsJsonArray("recipes").map { it.asJsonObject.get("id").asString }
        helper.assertTrue("minecraft:diamond_block" in ids, "Vanilla recipe missing from snapshot")
        helper.assertTrue(dump.get("loadDiagnostics").asString == "not-tracked", "Load diagnostics must remain unknown")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun preservesActualCrafting(helper: GameTestHelper) {
        val recipe =
            helper.level.recipeManager
                .byKey(ResourceLocation.parse("minecraft:iron_block"))
                .orElseThrow()
        val input = CraftingInput.of(3, 3, List(9) { ItemStack(Items.IRON_INGOT) })
        val matching =
            helper.level.recipeManager
                .getRecipeFor(RecipeType.CRAFTING, input, helper.level)
                .orElseThrow()
        helper.assertTrue(recipe.id() == matching.id(), "Collector must preserve recipe lookup")
        helper.assertTrue(
            matching.value().assemble(input, helper.level.registryAccess()).`is`(Items.IRON_BLOCK),
            "Crafting changed",
        )
        helper.succeed()
    }
}
