package org.mocoserver.recipedump.core

import com.google.gson.JsonObject

// 装置の全能力や複合設備を推測せず、確認したRecipeTypeへの検索入口だけを定義する。
private val stationTable =
    mapOf(
        "minecraft:crafting_table" to setOf("minecraft:crafting"),
        "minecraft:furnace" to setOf("minecraft:smelting"),
        "minecraft:blast_furnace" to setOf("minecraft:blasting"),
        "minecraft:smoker" to setOf("minecraft:smoking"),
        "minecraft:campfire" to setOf("minecraft:campfire_cooking"),
        "minecraft:soul_campfire" to setOf("minecraft:campfire_cooking"),
        "minecraft:stonecutter" to setOf("minecraft:stonecutting"),
        "minecraft:smithing_table" to setOf("minecraft:smithing"),
        "create:crushing_wheel" to setOf("create:crushing"),
        "create:millstone" to setOf("create:milling"),
        "create:mechanical_saw" to setOf("create:cutting"),
        "create:deployer" to setOf("create:deploying"),
        "mekanism:crusher" to setOf("mekanism:crushing"),
        "mekanism:enrichment_chamber" to setOf("mekanism:enriching"),
        "mekanism:precision_sawmill" to setOf("mekanism:sawing"),
        "farmersdelight:cooking_pot" to setOf("farmersdelight:cooking"),
        "farmersdelight:cutting_board" to setOf("farmersdelight:cutting"),
    )

internal fun availableStations(dump: JsonObject): Map<String, Set<String>> {
    require(dump.get("minecraft").stringValue() == "1.21.1") { "Station lookup requires Minecraft 1.21.1 metadata" }
    return stationTable
        .filterKeys {
            it.startsWith("minecraft:") || supportsModVersion(dump, it.substringBefore(':'))
        }.toSortedMap()
}

internal fun stationTypes(
    dump: JsonObject,
    station: String,
): Set<String> {
    require(isItemId(station)) { "Invalid station item ID $station" }
    return requireNotNull(availableStations(dump)[station]) { "Unsupported station $station for this dump's versions" }
}
