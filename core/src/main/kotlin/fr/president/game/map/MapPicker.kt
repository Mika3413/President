package fr.president.game.map

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import fr.president.engine.data.GameDatabase

/** Détermine l'élément touché : marqueur d'abord, puis territoire selon le niveau de détail. */
class MapPicker(private val data: MapData, private val db: GameDatabase, private val playerCountryId: String) {
    private val tmp = Vector3()
    private val infraTypes = db.country(playerCountryId).let { c ->
        (c.energy?.items.orEmpty() + c.transport?.items.orEmpty()).map { it.id }.toSet()
    }

    fun pick(camera: OrthographicCamera, overlay: OverlayRenderer, screenX: Float, screenY: Float, uiScale: Float, lod: Lod): MapSelection? {
        val sx = screenX / uiScale
        val sy = (camera.viewportHeight - screenY) / uiScale
        overlay.units.counters
            .map { it to dist2(it.x, it.y, sx, sy) }
            .filter { it.second < UNIT_RADIUS * UNIT_RADIUS }
            .minByOrNull { it.second }
            ?.let { return MapSelection.Unit(it.first.unitIds.first()) }
        overlay.visibleMarkers
            .map { it to dist2(it.second.x, it.second.y, sx, sy) }
            .filter { it.second < TOUCH_RADIUS * TOUCH_RADIUS }
            .minByOrNull { it.second }
            ?.first?.first?.let { return selectionFor(it) }

        tmp.set(screenX, screenY, 0f)
        camera.unproject(tmp)
        if (lod >= Lod.EUROPE) {
            data.departmentGrid.at(tmp.x, tmp.y).firstOrNull { it.contains(tmp.x, tmp.y) }?.let { dept ->
                return if (lod == Lod.EUROPE || lod == Lod.FRANCE) MapSelection.Region(dept.parent) else MapSelection.Department(dept.id)
            }
        }
        return data.countryGrid.at(tmp.x, tmp.y).firstOrNull { it.contains(tmp.x, tmp.y) }?.let { MapSelection.Country(it.id) }
    }

    /** Position géographique (lon, lat) d'un toucher, pour désigner une zone cible. */
    fun lonLat(camera: OrthographicCamera, screenX: Float, screenY: Float): Pair<Double, Double> {
        tmp.set(screenX, screenY, 0f)
        camera.unproject(tmp)
        return GeoProjection.lon(tmp.x) to GeoProjection.lat(tmp.y)
    }

    private fun selectionFor(m: MapMarker): MapSelection = when {
        m.kind == MarkerKind.CITY -> MapSelection.City(m.id)
        m.kind == MarkerKind.FOREIGN_CITY -> MapSelection.ForeignCity(m.id)
        m.kind == MarkerKind.MILITARY -> MapSelection.Base(m.id)
        m.id in infraTypes -> MapSelection.Infrastructure(m.id)
        else -> MapSelection.City(m.id)
    }

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float) = (ax - bx) * (ax - bx) + (ay - by) * (ay - by)

    private companion object {
        const val TOUCH_RADIUS = 16f
        const val UNIT_RADIUS = 14f
    }
}
