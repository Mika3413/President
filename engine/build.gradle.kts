import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Seule dépendance du moteur : sérialisation des données et des sauvegardes.
    api(libs.serialization.json)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Les tests chargent les vraies données du jeu.
    systemProperty("president.assets", rootProject.file("assets").absolutePath)
    // Sonde d'équilibrage (longue) : ./gradlew :engine:test --tests '*BalanceProbe*' -Dbalance=true
    listOf("balance", "balance.seeds", "balance.leaning", "balance.strategies").forEach { key -> System.getProperty(key)?.let { systemProperty(key, it) } }
    maxHeapSize = "2g"
}
