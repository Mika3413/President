package fr.president.game.map

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.Gdx
import fr.president.engine.world.WorldState
import fr.president.game.ui.Theme

/**
 * Rendu de la carte en espace monde : surfaces, frontières, réseaux, sélection.
 * Seuls les éléments visibles (culling) et pertinents au niveau de zoom (LOD) sont dessinés.
 */
class MapRenderer(private val data: MapData, private val playerCountryId: String) : Disposable {
    private val polygons = PolygonSpriteBatch()
    private val shapes = ShapeRenderer()
    private val style = MapStyle(data)
    private val view = Rectangle()

    fun render(camera: OrthographicCamera, state: WorldState, layer: ThematicLayer, lod: Lod, selection: MapSelection?) {
        Gdx.gl.glClearColor(Theme.sea.r, Theme.sea.g, Theme.sea.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val w = camera.viewportWidth * camera.zoom
        val h = camera.viewportHeight * camera.zoom
        view.set(camera.position.x - w / 2, camera.position.y - h / 2, w, h)
        val showDepartments = lod != Lod.WORLD

        polygons.projectionMatrix = camera.combined
        polygons.begin()
        for (country in data.visibleCountries(view)) {
            if (showDepartments && country.id == playerCountryId) continue
            polygons.color = countryColor(country.id, state)
            country.rings.forEach { if (it.bounds.overlaps(view)) polygons.draw(it.region, 0f, 0f) }
        }
        if (showDepartments) {
            for (dept in data.visibleDepartments(view)) {
                polygons.color = style.departmentColor(dept.id, dept.parent, layer, state)
                dept.rings.forEach { polygons.draw(it.region, 0f, 0f) }
            }
        }
        polygons.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Theme.border
        for (country in data.visibleCountries(view)) {
            if (showDepartments && country.id == playerCountryId) continue
            country.rings.forEach { if (it.bounds.overlaps(view)) shapes.polygon(it.vertices) }
        }
        if (lod >= Lod.FRANCE) {
            shapes.color = Theme.departmentBorder
            data.visibleDepartments(view).forEach { d -> d.rings.forEach { shapes.polygon(it.vertices) } }
        }
        shapes.end()

        val pixel = camera.zoom
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if (showDepartments) {
            shapes.color = Theme.regionBorder
            data.regions.forEach { r -> r.rings.forEach { if (it.bounds.overlaps(view)) thickPolygon(it.vertices, pixel * REGION_BORDER_PX) } }
        }
        drawNetworks(lod, layer, pixel)
        drawSelection(selection, pixel)
        shapes.end()
    }

    private fun countryColor(id: String, state: WorldState): Color = when {
        id == playerCountryId -> Theme.france
        state.countries.containsKey(id) -> Theme.landSimulated
        else -> Theme.landForeign
    }

    private fun drawNetworks(lod: Lod, layer: ThematicLayer, pixel: Float) {
        val emphasized = layer == ThematicLayer.TRANSPORT
        for (n in data.networks) {
            val rail = n.kind == "RAIL_HIGH_SPEED"
            val visible = when {
                emphasized -> lod >= Lod.FRANCE
                rail -> lod >= Lod.FRANCE
                else -> lod >= Lod.REGION
            }
            if (!visible) continue
            shapes.color = if (rail) Theme.rail else Theme.motorway
            val width = pixel * if (emphasized) EMPHASIZED_NETWORK_PX else NETWORK_PX
            for (i in 0 until n.points.size - 2 step 2) {
                shapes.rectLine(n.points[i], n.points[i + 1], n.points[i + 2], n.points[i + 3], width)
            }
        }
    }

    private fun drawSelection(selection: MapSelection?, pixel: Float) {
        val feature = when (selection) {
            is MapSelection.Department -> data.departmentsById[selection.code]
            is MapSelection.Region -> data.regionsById[selection.code]
            is MapSelection.Country -> data.countriesById[selection.id]
            else -> null
        } ?: return
        shapes.color = Theme.highlight
        feature.rings.forEach { thickPolygon(it.vertices, pixel * SELECTION_PX) }
    }

    private fun thickPolygon(v: FloatArray, width: Float) {
        var j = v.size - 2
        var i = 0
        while (i < v.size) {
            shapes.rectLine(v[j], v[j + 1], v[i], v[i + 1], width)
            j = i
            i += 2
        }
    }

    override fun dispose() {
        polygons.dispose()
        shapes.dispose()
    }

    private companion object {
        const val REGION_BORDER_PX = 1.8f
        const val NETWORK_PX = 1.5f
        const val EMPHASIZED_NETWORK_PX = 3f
        const val SELECTION_PX = 3f
    }
}
