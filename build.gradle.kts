plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
    id("com.modrinth.minotaur") version "2.9.0"
}

group = "i18nautoupdatemod"
version = providers.gradleProperty("version").get() + if ("false" == System.getenv("IS_SNAPSHOT")) "" else "-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
}

tasks.shadowJar {
    manifest {
        attributes(
            "TweakClass" to "i18nautoupdatemod.launchwrapper.LaunchWrapperTweaker",
            "TweakOrder" to 33,
            "Automatic-Module-Name" to "i18nautoupdatemod",
            "Implementation-Title" to "I18nAutoUpdateMod",
            "Implementation-Version" to project.version,
        )
    }
    minimize()
    archiveBaseName.set("I18nAutoUpdateMod")
    relocate("com.google.archivepatcher", "include.com.google.archivepatcher")
    dependencies {
        include(dependency("net.runelite.archive-patcher:archive-patcher-applier:.*"))
    }
    exclude("LICENSE")
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://maven.fabricmc.net/")
    maven("https://files.minecraftforge.net/maven")
    maven("https://maven.neoforged.net/releases")
    maven("https://repo.runelite.net/")
}

configurations.configureEach {
    isTransitive = name.startsWith("test")
}

configurations.compileClasspath {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
    implementation("net.runelite.archive-patcher:archive-patcher-applier:1.2")
    compileOnly("org.jetbrains:annotations:24.1.0")

    implementation("net.fabricmc:fabric-loader:0.15.9")
    implementation("cpw.mods:modlauncher:8.1.3")
    implementation("net.minecraft:launchwrapper:1.12")
    compileOnly("net.neoforged.fancymodloader:loader:10.0.36")
    compileOnly("net.neoforged:mergetool:2.0.0:api")

    implementation("commons-io:commons-io:2.16.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("com.google.code.gson:gson:2.11.0")

}

tasks.test {
    useJUnitPlatform()
}

val expandedVersion = version.toString()

tasks.processResources {
    inputs.property("version", expandedVersion)
    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
        expand(
            "version" to expandedVersion,
        )
    }
}

val supportMinecraftVersions = providers.gradleProperty("minecraft").get().split(",")

// Modrinth rejects the fabric loader on versions before 18w43b, so the same jar is
// uploaded twice: once for Forge/NeoForge on every version, once for Fabric/Quilt on 1.14+
val modrinthFabric = System.getenv("MODRINTH_TARGET") == "fabric"

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("mEn7eS3l")
    versionNumber.set("${project.version}")
    versionName.set("I18nAutoUpdateMod ${project.version} " + if (modrinthFabric) "Fabric/Quilt" else "Forge/NeoForge")
    versionType.set("release")
    uploadFile.set(tasks["shadowJar"])
    gameVersions.set(
        if (modrinthFabric) supportMinecraftVersions.drop(supportMinecraftVersions.indexOf("1.14"))
        else supportMinecraftVersions
    )
    loaders.set(if (modrinthFabric) listOf("fabric", "quilt") else listOf("forge", "neoforge"))
    syncBodyFrom.set(rootProject.file("README.md").readText())
    changelog.set(System.getenv("CHANGE_LOG"))
}
