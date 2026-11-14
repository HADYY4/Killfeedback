plugins {
    id("fabric-loom") version "1.19.4"
    kotlin("jvm") version "2.4.20"
}

val modVersion = providers.gradleProperty("mod_version").get()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()

version = "$modVersion+$minecraftVersion"
group = providers.gradleProperty("maven_group").get()

base {
    archivesName.set(providers.gradleProperty("archives_base_name").get())
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:${providers.gradleProperty("yarn_mappings").get()}:v2")

    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
