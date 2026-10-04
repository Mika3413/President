package fr.president.engine.save

import java.io.File

/**
 * Stockage robuste des sauvegardes : écriture dans un fichier temporaire puis renommage
 * atomique, conservation de la version précédente en secours (.bak).
 */
class SaveRepository(private val directory: File, private val codec: SaveCodec = SaveCodec()) {

    init {
        directory.mkdirs()
    }

    /** Résumé d'une partie sauvegardée, lisible sans charger toute la sauvegarde. */
    @kotlinx.serialization.Serializable
    data class SlotMeta(val title: String = "", val detail: String = "", val savedAt: Long = 0L)

    data class Slot(val id: String, val meta: SlotMeta)

    /** Partie en cours : celle que « Continuer » et la simulation d'arrière-plan reprennent. */
    var activeSlot: String
        get() = runCatching { File(directory, ACTIVE_FILE).readText().trim() }.getOrNull()?.ifBlank { null } ?: DEFAULT_SLOT
        set(v) { File(directory, ACTIVE_FILE).writeText(v) }

    fun exists(slot: String = activeSlot): Boolean = file(slot).exists() || backup(slot).exists()

    /** Toutes les parties sauvegardées, la plus récente d'abord. */
    fun slots(): List<Slot> = directory.listFiles { f -> f.name.endsWith(EXTENSION) }.orEmpty()
        .map { it.name.removeSuffix(EXTENSION) }
        .map { id -> Slot(id, meta(id) ?: SlotMeta("Partie", "", file(id).lastModified())) }
        .sortedByDescending { it.meta.savedAt }

    /** Un emplacement libre pour une nouvelle partie (les autres sont conservées). */
    fun newSlotId(): String {
        var i = 1
        while (exists("partie-$i")) i++
        return "partie-$i"
    }

    private fun meta(slot: String): SlotMeta? = runCatching {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(SlotMeta.serializer(), File(directory, "$slot$META").readText())
    }.getOrNull()

    fun writeMeta(slot: String, meta: SlotMeta) {
        runCatching { File(directory, "$slot$META").writeText(kotlinx.serialization.json.Json.encodeToString(SlotMeta.serializer(), meta)) }
    }

    @Synchronized
    fun write(save: SaveFile, slot: String = activeSlot) {
        val bytes = codec.encode(save)
        val target = file(slot)
        val temp = File(directory, "$slot$TEMP_SUFFIX")
        temp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        // Vérification : on ne remplace jamais une sauvegarde valide par un fichier illisible.
        codec.decode(temp.readBytes())
        if (target.exists()) {
            val bak = backup(slot)
            if (bak.exists()) bak.delete()
            target.renameTo(bak)
        }
        if (!temp.renameTo(target)) throw SaveException("Impossible d'écrire la sauvegarde ${target.path}")
    }

    /** Charge la sauvegarde ; en cas de corruption, se rabat sur la copie de secours. */
    @Synchronized
    fun read(slot: String = activeSlot): SaveFile {
        val primary = file(slot)
        val first = runCatching { codec.decode(primary.readBytes()) }
        if (first.isSuccess) return first.getOrThrow()
        val bak = backup(slot)
        if (bak.exists()) return codec.decode(bak.readBytes())
        throw SaveException("Aucune sauvegarde lisible", first.exceptionOrNull())
    }

    fun delete(slot: String = activeSlot) {
        file(slot).delete()
        backup(slot).delete()
        File(directory, "$slot$META").delete()
    }

    private fun file(slot: String) = File(directory, "$slot$EXTENSION")
    private fun backup(slot: String) = File(directory, "$slot$EXTENSION$BACKUP_SUFFIX")

    companion object {
        const val DEFAULT_SLOT = "president"
        private const val EXTENSION = ".save"
        private const val TEMP_SUFFIX = ".tmp"
        private const val BACKUP_SUFFIX = ".bak"
        private const val META = ".meta.json"
        private const val ACTIVE_FILE = "active-slot"
    }
}
