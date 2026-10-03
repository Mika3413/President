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

    fun exists(slot: String = DEFAULT_SLOT): Boolean = file(slot).exists() || backup(slot).exists()

    @Synchronized
    fun write(save: SaveFile, slot: String = DEFAULT_SLOT) {
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
    fun read(slot: String = DEFAULT_SLOT): SaveFile {
        val primary = file(slot)
        val first = runCatching { codec.decode(primary.readBytes()) }
        if (first.isSuccess) return first.getOrThrow()
        val bak = backup(slot)
        if (bak.exists()) return codec.decode(bak.readBytes())
        throw SaveException("Aucune sauvegarde lisible", first.exceptionOrNull())
    }

    fun delete(slot: String = DEFAULT_SLOT) {
        file(slot).delete()
        backup(slot).delete()
    }

    private fun file(slot: String) = File(directory, "$slot$EXTENSION")
    private fun backup(slot: String) = File(directory, "$slot$EXTENSION$BACKUP_SUFFIX")

    companion object {
        const val DEFAULT_SLOT = "president"
        private const val EXTENSION = ".save"
        private const val TEMP_SUFFIX = ".tmp"
        private const val BACKUP_SUFFIX = ".bak"
    }
}
