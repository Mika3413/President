package fr.president.engine.save

import fr.president.engine.session.GameSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Un président de la galerie : qui, quand, comment ça s'est fini, et le jugement de l'Histoire. */
@Serializable
data class GalleryEntry(
    val id: String,
    val name: String,
    val female: Boolean,
    val birthYear: Int,
    val portraitSeed: Long,
    val appearance: fr.president.engine.politics.Appearance? = null,
    val country: String,
    val career: String = "",
    val from: Long,
    val to: Long,
    val terms: Int,
    val grade: Double,
    val title: String,
    val ending: String,
)

/** Galerie de vos présidents, d'une partie à l'autre (fichier à côté des sauvegardes). */
class Gallery(private val directory: File) {
    private val file get() = File(directory, FILE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun entries(): List<GalleryEntry> = runCatching { json.decodeFromString(ListSerializer(GalleryEntry.serializer()), file.readText()) }.getOrDefault(emptyList())

    /** Inscrit la présidence terminée (une seule fois par partie). */
    fun record(session: GameSession) {
        val s = session.state
        val p = s.characters[s.player.presidentId] ?: return
        val id = "${p.id}@${s.meta.startTime.seconds}"
        val list = entries()
        if (list.any { it.id == id }) return
        val verdict = fr.president.engine.readout.LegacyReadout(session.context).verdict()
        val career = s.player.career?.let { c -> session.context.playerData.careers.firstOrNull { it.id == c }?.label } ?: ""
        val entry = GalleryEntry(id, p.fullName, p.female, p.birthYear, p.portraitSeed, p.appearance, session.context.playerData.definition.name, career,
            s.meta.startTime.seconds, s.time.seconds, s.player.termNumber, verdict.grade, verdict.title, s.player.gameOver?.reason ?: "")
        runCatching {
            directory.mkdirs()
            val tmp = File(directory, "$FILE.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(GalleryEntry.serializer()), (list + entry).takeLast(MAX)))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    private companion object {
        const val FILE = "galerie.json"
        const val MAX = 100
    }
}
