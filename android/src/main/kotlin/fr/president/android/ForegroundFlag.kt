package fr.president.android

import java.io.File

/**
 * Indique si le jeu est au premier plan, pour que le travail d'arrière-plan n'écrive jamais la
 * sauvegarde pendant que le joueur joue. Le jeu rafraîchit ce signal à chaque sauvegarde
 * automatique : s'il n'est plus rafraîchi (processus tué brutalement), il est ignoré.
 */
object ForegroundFlag {
    private const val FILE = "foreground.flag"
    /** Au-delà, le signal est considéré comme orphelin (autosauvegarde toutes les 60 s). */
    private const val STALE_MILLIS = 3 * 60 * 1000L

    fun set(dir: File, foreground: Boolean) {
        dir.mkdirs()
        val f = File(dir, FILE)
        if (foreground) f.writeText(System.currentTimeMillis().toString()) else f.delete()
    }

    fun isForeground(dir: File): Boolean {
        val f = File(dir, FILE)
        if (!f.exists()) return false
        return System.currentTimeMillis() - f.lastModified() < STALE_MILLIS
    }
}
