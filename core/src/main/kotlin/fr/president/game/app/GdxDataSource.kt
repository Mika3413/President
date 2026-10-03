package fr.president.game.app

import com.badlogic.gdx.Gdx
import fr.president.engine.data.DataSource

/** Lecture des données du jeu dans les assets internes (APK sur Android, dossier assets sur PC). */
class GdxDataSource : DataSource {
    override fun read(path: String): String = Gdx.files.internal(path).readString(Charsets.UTF_8.name())
}
