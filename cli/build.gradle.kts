plugins {
    application
    alias(libs.plugins.kotlin)
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application {
    mainClass.set("org.mocoserver.recipedump.cli.MainKt")
    applicationName = "recipe-dump"
}
tasks.test { useJUnitPlatform() }

configurations
    .matching {
        it.name in setOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
    }.configureEach { resolutionStrategy.activateDependencyLocking() }

// 配布物だけで利用手順を読めるようにし、関連資料は同じ版のソースへ案内する。
val cliGuide = rootProject.file("docs/guides/cli.md")
val guideSource = providers.gradleProperty("mod_repo_url").get() + "/blob/v$version/"
distributions {
    main {
        contents {
            from(cliGuide) {
                rename { "README.md" }
                filteringCharset = "UTF-8"
                filter { line: String ->
                    line.replace(Regex("\\]\\((?![a-z]+:|#)([^)]+)\\)")) { link ->
                        val target = link.groupValues[1].split("#", limit = 2)
                        val path =
                            cliGuide.parentFile
                                .resolve(target[0])
                                .normalize()
                                .relativeTo(rootProject.projectDir)
                        val anchor = if (target.size == 2) "#${target[1]}" else ""
                        "]($guideSource${path.invariantSeparatorsPath}$anchor)"
                    }
                }
            }
            from(rootProject.file("LICENSE"))
        }
    }
}

// リンク先の版・repo URLだけが変わった場合もガイドを再生成する。
tasks.matching { it.name in setOf("distZip", "distTar", "installDist") }.configureEach {
    inputs.property("guideSource", guideSource)
}
