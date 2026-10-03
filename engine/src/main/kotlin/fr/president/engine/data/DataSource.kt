package fr.president.engine.data

import java.io.File

/** Accès en lecture aux fichiers de données du jeu, indépendamment de la plateforme. */
fun interface DataSource {
    fun read(path: String): String
}

class FileDataSource(private val root: File) : DataSource {
    override fun read(path: String): String = File(root, path).readText(Charsets.UTF_8)
}
