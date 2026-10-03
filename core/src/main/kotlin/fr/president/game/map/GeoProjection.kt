package fr.president.game.map

import com.badlogic.gdx.math.Vector2
import kotlin.math.cos

/**
 * Projection équirectangulaire centrée sur la France (parallèle de référence 46,5°N) :
 * simple, rapide et fidèle à l'échelle de l'Europe occidentale.
 */
object GeoProjection {
    /** Unités monde par degré de latitude. */
    const val UNITS_PER_DEGREE = 100f
    private const val REFERENCE_LATITUDE = 46.5
    private val lonScale = (cos(Math.toRadians(REFERENCE_LATITUDE)) * UNITS_PER_DEGREE).toFloat()

    fun x(lon: Double): Float = (lon * lonScale).toFloat()
    fun y(lat: Double): Float = (lat * UNITS_PER_DEGREE).toFloat()
    fun lon(x: Float): Double = x / lonScale.toDouble()
    fun lat(y: Float): Double = y / UNITS_PER_DEGREE.toDouble()
    fun project(lon: Double, lat: Double, out: Vector2 = Vector2()): Vector2 = out.set(x(lon), y(lat))

    /** Kilomètres carrés représentés par une unité monde au carré (pour les densités). */
    const val KM2_PER_UNIT2 = 111.32f * 111.32f / (UNITS_PER_DEGREE * UNITS_PER_DEGREE)
}
