package fr.president.game.map

import com.badlogic.gdx.graphics.g2d.PolygonRegion
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Rectangle

/** Polygone projeté et triangulé, prêt à être dessiné. */
class GeoRing(val vertices: FloatArray, val region: PolygonRegion) {
    val bounds: Rectangle = boundsOf(vertices)

    fun contains(x: Float, y: Float): Boolean =
        bounds.contains(x, y) && Intersector.isPointInPolygon(vertices, 0, vertices.size, x, y)

    /** Aire signée absolue (unités monde²). */
    val area: Float by lazy {
        var a = 0f
        var j = vertices.size - 2
        var i = 0
        while (i < vertices.size) {
            a += (vertices[j] + vertices[i]) * (vertices[j + 1] - vertices[i + 1])
            j = i
            i += 2
        }
        kotlin.math.abs(a / 2f)
    }

    private companion object {
        fun boundsOf(v: FloatArray): Rectangle {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in v.indices step 2) {
                minX = minOf(minX, v[i]); maxX = maxOf(maxX, v[i])
                minY = minOf(minY, v[i + 1]); maxY = maxOf(maxY, v[i + 1])
            }
            return Rectangle(minX, minY, maxX - minX, maxY - minY)
        }
    }
}

/** Entité géographique (pays, région, département) composée d'un ou plusieurs anneaux. */
class GeoFeature(val id: String, val name: String, val parent: String, val rings: List<GeoRing>) {
    val bounds: Rectangle = rings.map { it.bounds }.reduce { a, b -> Rectangle(a).merge(b) }
    val area: Float = rings.sumOf { it.area.toDouble() }.toFloat()

    /** Point d'ancrage de l'étiquette : centre de la boîte du plus grand anneau. */
    val labelX: Float
    val labelY: Float

    init {
        val main = rings.maxByOrNull { it.area }!!
        labelX = main.bounds.x + main.bounds.width / 2
        labelY = main.bounds.y + main.bounds.height / 2
    }

    fun contains(x: Float, y: Float): Boolean = bounds.contains(x, y) && rings.any { it.contains(x, y) }
}
