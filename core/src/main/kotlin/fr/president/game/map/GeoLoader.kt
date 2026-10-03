package fr.president.game.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.PolygonRegion
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.EarClippingTriangulator
import fr.president.engine.data.GameJson
import kotlinx.serialization.Serializable

@Serializable
private data class GeoFile(val source: String, val features: List<GeoFeatureDef>)

@Serializable
private data class GeoFeatureDef(val id: String, val name: String, val parent: String = "", val rings: List<List<Double>>)

/** Charge les géométries produites par tools/mapgen, les projette et les triangule une fois pour toutes. */
class GeoLoader(private val white: TextureRegion) {
    private val triangulator = EarClippingTriangulator()

    fun load(path: String): List<GeoFeature> {
        val file = GameJson.data.decodeFromString(GeoFile.serializer(), Gdx.files.internal(path).readString("UTF-8"))
        return file.features.mapNotNull { def ->
            val rings = def.rings.mapNotNull { ring(it) }
            if (rings.isEmpty()) null else GeoFeature(def.id, def.name, def.parent, rings)
        }
    }

    private fun ring(coords: List<Double>): GeoRing? {
        if (coords.size < MIN_COORDS) return null
        val vertices = FloatArray(coords.size)
        for (i in coords.indices step 2) {
            vertices[i] = GeoProjection.x(coords[i])
            vertices[i + 1] = GeoProjection.y(coords[i + 1])
        }
        val triangles = triangulator.computeTriangles(vertices).toArray()
        if (triangles.isEmpty()) return null
        return GeoRing(vertices, PolygonRegion(white, vertices, triangles))
    }

    private companion object {
        const val MIN_COORDS = 6
    }
}
