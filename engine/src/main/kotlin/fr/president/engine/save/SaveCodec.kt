package fr.president.engine.save

import fr.president.engine.data.GameJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Encodage des sauvegardes : JSON compressé (gzip), migré si nécessaire au chargement. */
class SaveCodec(private val migrator: SaveMigrator = SaveMigrator()) {

    fun encode(save: SaveFile): ByteArray {
        val json = GameJson.save.encodeToString(SaveFile.serializer(), save)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): SaveFile {
        val text = try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            throw SaveException("Sauvegarde illisible", e)
        }
        return try {
            val tree = GameJson.save.parseToJsonElement(text).jsonObject
            val upgraded: JsonObject = migrator.upgrade(tree)
            GameJson.save.decodeFromJsonElement(SaveFile.serializer(), upgraded)
        } catch (e: SaveException) {
            throw e
        } catch (e: Exception) {
            throw SaveException("Sauvegarde corrompue : ${e.message}", e)
        }
    }
}
