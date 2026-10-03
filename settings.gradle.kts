pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}
rootProject.name = "minecraft_recipe_dump"
include("core", "neoforge", "cli")
