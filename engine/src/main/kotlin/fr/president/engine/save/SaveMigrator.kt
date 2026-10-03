package fr.president.engine.save

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Migration d'un format de sauvegarde vers le suivant, appliquée sur l'arbre JSON brut. */
fun interface SaveMigration {
    fun migrate(root: JsonObject): JsonObject
}

/**
 * Chaîne de migrations : une sauvegarde ancienne est mise à niveau pas à pas
 * jusqu'au format courant avant d'être désérialisée.
 */
class SaveMigrator(private val migrations: Map<Int, SaveMigration> = emptyMap()) {

    fun upgrade(root: JsonObject): JsonObject {
        var current = root
        var version = current["formatVersion"]?.jsonPrimitive?.int ?: LEGACY_VERSION
        if (version > SaveFile.CURRENT_FORMAT) {
            throw SaveException("Sauvegarde créée par une version plus récente du jeu (format $version)")
        }
        while (version < SaveFile.CURRENT_FORMAT) {
            val migration = migrations[version] ?: throw SaveException("Aucune migration depuis le format $version")
            current = migration.migrate(current)
            version++
            current = JsonObject(current + ("formatVersion" to JsonPrimitive(version)))
        }
        return current
    }

    private companion object {
        const val LEGACY_VERSION = 0
    }
}

class SaveException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
