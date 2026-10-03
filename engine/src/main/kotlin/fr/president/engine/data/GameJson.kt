package fr.president.engine.data

import kotlinx.serialization.json.Json

/** Configurations JSON partagées par les données et les sauvegardes. */
object GameJson {
    val data: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val save: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }
}
