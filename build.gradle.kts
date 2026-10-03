buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Le plugin Android n'est résolu que si le module :android est inclus (voir settings),
        // ce qui évite d'exiger le dépôt Google pour compiler le moteur.
        if (findProject(":android") != null) {
            classpath("com.android.tools.build:gradle:8.9.1")
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
