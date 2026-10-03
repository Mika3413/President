package fr.president.android

import java.io.File

/**
 * Indique si le jeu est au premier plan, pour que le travail d'arrière-plan
 * n'écrive jamais la sauvegarde pendant que le joueur joue.
 */
object ForegroundFlag {
    private const val FILE = "foreground.flag"

    fun set(dir: File, foreground: Boolean) {
        dir.mkdirs()
        val f = File(dir, FILE)
        if (foreground) f.writeText(System.currentTimeMillis().toString()) else f.delete()
    }

    fun isForeground(dir: File): Boolean = File(dir, FILE).exists()
}
