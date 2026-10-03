pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "president"

// Moteur de simulation pur (aucune dépendance Android/libGDX)
include("engine")
// Rendu, carte et interface (libGDX)
include("core")
// Lanceur PC de développement
include("desktop")

// Le module Android n'est inclus que si un SDK Android est disponible,
// afin que le moteur et les tests restent compilables partout.
val hasAndroidSdk = file("local.properties").let { f ->
    f.exists() && f.readText().contains("sdk.dir")
} || System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include("android")
}
