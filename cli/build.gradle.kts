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
