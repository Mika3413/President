import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.lwjgl3)
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
    runtimeOnly(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}

application {
    mainClass.set("fr.president.desktop.DesktopLauncherKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    // Répertoire de sauvegarde de développement
    systemProperty("president.saveDir", rootProject.layout.buildDirectory.dir("saves").get().asFile.absolutePath)
    // Transmet les options de développement (-Dpresident.script=..., -Dpresident.uiScale=...).
    System.getProperties().stringPropertyNames().filter { it.startsWith("president.") && it != "president.saveDir" }
        .forEach { systemProperty(it, System.getProperty(it)) }
    // Simulation d'un téléphone : ./gradlew :desktop:run -Dheap=192m
    System.getProperty("heap")?.let { maxHeapSize = it }
}
