package fr.president.android

import android.content.res.AssetManager
import fr.president.engine.data.DataSource

/** Lecture des données du jeu depuis les assets de l'APK, sans libGDX (utilisé par le worker). */
class AssetDataSource(private val assets: AssetManager) : DataSource {
    override fun read(path: String): String = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
