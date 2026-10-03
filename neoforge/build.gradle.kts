plugins {
    `java-library`
    alias(libs.plugins.kotlin)
    alias(libs.plugins.moddev)
}
kotlin { jvmToolchain(21) }
val recipeModId = providers.gradleProperty("mod_id").get()
base { archivesName.set(recipeModId) }
repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        content { includeGroup("thedarkcolour") }
    }
}
dependencies {
    implementation(project(":core"))
    implementation(libs.kff)
}
val metadata =
    tasks.register<ProcessResources>("generateModMetadata") {
        val properties =
            listOf(
                "mod_id",
                "mod_name",
                "mod_version",
                "mod_authors",
                "mod_description",
                "mod_license",
                "mod_repo_url",
            ).associateWith {
                providers
                    .gradleProperty(it)
                    .get()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
            }
        inputs.properties(properties)
        filteringCharset = "UTF-8"
        expand(properties)
        from("src/main/templates")
        into(layout.buildDirectory.dir("generated/modMetadata"))
    }
sourceSets.main {
    resources.srcDir(metadata)
    resources.srcDir("src/generated/resources")
}
val gametest =
    sourceSets.create("gametest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
    }
configurations[gametest.implementationConfigurationName].extendsFrom(configurations.implementation.get())
val fixtureMetadata =
    tasks.register<ProcessResources>("generateGameTestMetadata") {
        val values = mapOf("mod_id" to recipeModId, "mod_version" to project.version.toString())
        inputs.properties(values)
        expand(values)
        from("src/gametest/templates")
        into(layout.buildDirectory.dir("generated/gameTestMetadata"))
    }
gametest.resources.srcDir(fixtureMetadata)
neoForge {
    version = providers.gradleProperty("neo_version").get()
    mods {
        create(recipeModId) {
            sourceSet(sourceSets.main.get())
            sourceSet(project(":core").sourceSets.main.get())
        }
    }
    mods.create("${recipeModId}_tests") { sourceSet(gametest) }
    runs {
        create("client") {
            client()
            gameDirectory.set(rootProject.file(".local/client"))
        }
        create("server") {
            server()
            programArgument("--nogui")
            gameDirectory.set(rootProject.file(".local/server"))
        }
        create("serverDump") {
            server()
            programArgument("--nogui")
            gameDirectory.set(rootProject.file(".local/dump-server"))
            systemProperty("recipedump.enabled", "true")
            systemProperty("recipedump.output", rootProject.file(".local/dump-server/recipes.json").absolutePath)
        }
        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory.set(rootProject.file(".local/gametest"))
            systemProperty("neoforge.enabledGameTestNamespaces", recipeModId)
        }
    }
    runs.configureEach {
        loadedMods.set(listOf(mods.getByName(recipeModId)))
    }
    runs.getByName("gameTestServer").apply {
        sourceSet = gametest
        loadedMods.set(listOf(mods.getByName(recipeModId), mods.getByName("${recipeModId}_tests")))
    }
    ideSyncTask(metadata)
}
tasks.jar {
    from(
        project(":core")
            .sourceSets.main
            .get()
            .output,
    )
    from(rootProject.file("LICENSE")) { into("META-INF") }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

val verifyModJar =
    tasks.register<VerifyModJarTask>("verifyModJar") {
        modJar.set(tasks.jar.flatMap { it.archiveFile })
        modId.set(recipeModId)
        val path = providers.gradleProperty("mod_package").get().replace('.', '/')
        packagePath.set(path)
        requiredEntries.set(
            listOf(
                "$path/core/DumpJson.class",
                "$path/core/DumpReports.class",
                "$path/RecipeDumpMod.class",
                "$path/SnapshotCollector.class",
                "META-INF/neoforge.mods.toml",
                "META-INF/LICENSE",
            ),
        )
    }
tasks.named("build") { dependsOn(verifyModJar) }

tasks.processResources { exclude("**/.cache/**") }
