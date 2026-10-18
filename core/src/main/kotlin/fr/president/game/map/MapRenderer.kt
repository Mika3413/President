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

        // Halo clair le long des côtes : la mer s'éclaircit près des terres.
        if (lod >= Lod.FRANCE) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            shapes.projectionMatrix = camera.combined
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            shapes.color = COAST_HALO
            for (country in data.visibleCountries(view)) country.rings.forEach { if (it.bounds.overlaps(view)) thickPolygon(it.vertices, camera.zoom * COAST_PX) }
            shapes.end()
        }

        polygons.projectionMatrix = camera.combined
        polygons.begin()
        for (country in data.visibleCountries(view)) {
            if (showDepartments && country.id == playerCountryId) continue
            polygons.color = countryColor(country.id, state)
            country.rings.forEach { if (it.bounds.overlaps(view)) polygons.draw(it.region, 0f, 0f) }
        }
        if (showDepartments) {
            for (dept in data.visibleDepartments(view)) {
                polygons.color = departmentOverride?.invoke(dept.id) ?: style.departmentColor(dept.id, dept.parent, layer, state)
                dept.rings.forEach { polygons.draw(it.region, 0f, 0f) }
            }
        }
        polygons.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapes.projectionMatrix = camera.combined
        drawRelief(lod, camera.zoom)
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
        if (showDepartments) {
            // Cadres des médaillons d'outre-mer.
            shapes.color = Theme.border
            data.insets.filter { it.overlaps(view) }.forEach { shapes.rect(it.x, it.y, it.width, it.height) }
        }
        shapes.end()

        val pixel = camera.zoom
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        if (showDepartments) {
            shapes.color = Theme.regionBorder
            data.regions.forEach { r -> r.rings.forEach { if (it.bounds.overlaps(view)) thickPolygon(it.vertices, pixel * REGION_BORDER_PX) } }
        }
        drawNetworks(lod, layer, pixel)
        drawFronts(state, lod)
        drawSelection(selection, pixel)
        shapes.end()
    }

    /**
     * Relation de chaque pays simulé avec le joueur (0 hostile → 1 allié) et pays en guerre contre
     * lui, mis à jour par l'écran principal : la carte du monde se lit d'un coup d'œil.
     */
    var relations: Map<String, Double> = emptyMap()
    var enemies: Set<String> = emptySet()
    private val relationColor = Color()

    private fun countryColor(id: String, state: WorldState): Color {
        if (id == playerCountryId) return Theme.france
        if (id in enemies) return Theme.relationWar
        val r = relations[id] ?: return if (state.countries.containsKey(id)) Theme.landSimulated else Theme.landForeign
        return Theme.relation(r.toFloat(), relationColor)
    }

    private fun drawNetworks(lod: Lod, layer: ThematicLayer, pixel: Float) {
        val emphasized = layer == ThematicLayer.TRANSPORT
        // Fleuves d'abord (dessous), puis autoroutes, puis LGV.
        for (n in data.networks.sortedBy { when (it.kind) { "RIVER" -> 0; "MOTORWAY" -> 1; else -> 2 } }) {
            val rail = n.kind == "RAIL_HIGH_SPEED"
            val river = n.kind == "RIVER"
            val visible = when {
                river -> lod >= Lod.FRANCE
                emphasized -> lod >= Lod.FRANCE
                else -> lod >= Lod.REGION
            }
            if (!visible) continue
            shapes.color = if (river) Theme.river else if (rail) Theme.rail else Theme.motorway
            val width = pixel * if (river) RIVER_PX else if (emphasized) EMPHASIZED_NETWORK_PX else NETWORK_PX
            for (i in 0 until n.points.size - 2 step 2) {
                shapes.rectLine(n.points[i], n.points[i + 1], n.points[i + 2], n.points[i + 3], width)
            }
        }
    }

    /** Zones occupées ou annexées : carrés colorés selon le camp, dessinant les lignes de front. */
    private fun drawFronts(state: WorldState, lod: Lod) {
        if (lod == Lod.WORLD) return
        val military = state.military
        val fronts = occupiedOverride ?: (military.occupied + military.annexed)
        if (fronts.isEmpty()) return
        val zones = zoneLookup ?: return
        for ((zoneId, holder) in fronts) {
            val z = zones.zones[zoneId] ?: continue
            val x0 = GeoProjection.x(z.lon - HALF_CELL); val x1 = GeoProjection.x(z.lon + HALF_CELL)
            val y0 = GeoProjection.y(z.lat - HALF_CELL); val y1 = GeoProjection.y(z.lat + HALF_CELL)
            if (!view.overlaps(com.badlogic.gdx.math.Rectangle(x0, y0, x1 - x0, y1 - y0))) continue
            val annexed = occupiedOverride == null && military.annexed[zoneId] == holder && military.occupied[zoneId] == null
            val base = when (holder) { playerCountryId -> Theme.accent; else -> if (holderHostile(holder)) Theme.bad else Theme.warning }
            shapes.color = Color(base.r, base.g, base.b, if (annexed) ANNEXED_ALPHA else OCCUPIED_ALPHA)
            shapes.rect(x0, y0, x1 - x0, y1 - y0)
        }
    }

    /** Relecture du mandat : couleurs des départements et zones occupées d'une semaine passée. */
    var departmentOverride: ((String) -> Color?)? = null
    var occupiedOverride: Map<String, String>? = null

    /** Reliefs (montagnes, collines, forêts, déserts), fournis par l'écran. */
    var relief: List<fr.president.engine.military.TerrainArea> = emptyList()

    /** Estompage discret : halos concentriques, hachures sur les sommets de près. */
    private fun drawRelief(lod: Lod, pixel: Float) {
        if (relief.isEmpty() || lod == Lod.WORLD) return
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        for (a in relief) {
            val cx = GeoProjection.x(a.lon); val cy = GeoProjection.y(a.lat)
            val w = GeoProjection.x(a.rx) - GeoProjection.x(0.0); val h = GeoProjection.y(a.ry) - GeoProjection.y(0.0)
            if (!view.overlaps(com.badlogic.gdx.math.Rectangle(cx - w, cy - h, w * 2, h * 2))) continue
            val base = when (a.terrain) { "MOUNTAIN" -> RELIEF_MOUNTAIN; "HILLS" -> RELIEF_HILLS; "FOREST" -> RELIEF_FOREST; else -> RELIEF_DESERT }
            for (k in 0 until RELIEF_RINGS) {
                val f = 1f - k / RELIEF_RINGS.toFloat()
                shapes.color = reliefColor.set(base.r, base.g, base.b, base.a * (0.35f + 0.65f * (k + 1) / RELIEF_RINGS))
                shapes.ellipse(cx - w * f, cy - h * f, w * 2 * f, h * 2 * f, 28)
            }
            // De près, de petites hachures de sommets.
            if (a.terrain == "MOUNTAIN" && lod >= Lod.FRANCE) {
                shapes.color = PEAK
                val step = (w / 3).coerceAtLeast(pixel * 14)
                var x = cx - w * 0.6f
                var i = 0
                while (x < cx + w * 0.6f) {
                    val y = cy + (if (i % 2 == 0) 0.15f else -0.2f) * h
                    val s = pixel * 7
                    shapes.triangle(x - s, y - s * 0.6f, x + s, y - s * 0.6f, x, y + s)
                    x += step; i++
                }
            }
        }
        shapes.end()
    }
    private val reliefColor = Color()

    /** Graphe des zones (fourni par l'écran) et test d'hostilité envers le joueur. */
    var zoneLookup: fr.president.engine.military.ZoneGraph? = null
    var holderHostile: (String) -> Boolean = { false }

    private fun drawSelection(selection: MapSelection?, pixel: Float) {
        val feature = when (selection) {
            is MapSelection.Department -> data.departmentsById[selection.code]
            is MapSelection.Region -> data.regionsById[selection.code]
            is MapSelection.Country -> data.countriesById[selection.id]
            else -> null
        } ?: return
        // Contour de sélection légèrement pulsant.
        val pulse = (Math.sin(System.nanoTime() / NANOS_PER_SECOND * SELECTION_PULSE_SPEED) + 1.0).toFloat() / 2f
        shapes.color = Color(Theme.highlight.r, Theme.highlight.g, Theme.highlight.b, SELECTION_MIN_ALPHA + (1f - SELECTION_MIN_ALPHA) * pulse)
        feature.rings.forEach { thickPolygon(it.vertices, pixel * SELECTION_PX * (1f + SELECTION_PULSE_WIDTH * pulse)) }
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
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val SELECTION_PULSE_SPEED = 3.0
        const val SELECTION_MIN_ALPHA = 0.55f
        const val SELECTION_PULSE_WIDTH = 0.6f
        const val REGION_BORDER_PX = 1.8f
        const val NETWORK_PX = 1.5f
        const val RIVER_PX = 1.8f
        const val EMPHASIZED_NETWORK_PX = 3f
        const val SELECTION_PX = 3f
        const val HALF_CELL = 0.5
        const val OCCUPIED_ALPHA = 0.45f
        const val ANNEXED_ALPHA = 0.25f
        const val COAST_PX = 7f
        const val RELIEF_RINGS = 4
        val COAST_HALO = Color(0.42f, 0.66f, 0.85f, 0.22f)
        val RELIEF_MOUNTAIN = Color(0.42f, 0.32f, 0.22f, 0.15f)
        val RELIEF_HILLS = Color(0.5f, 0.42f, 0.28f, 0.05f)
        val RELIEF_FOREST = Color(0.1f, 0.3f, 0.12f, 0.07f)
        val RELIEF_DESERT = Color(0.85f, 0.7f, 0.4f, 0.06f)
        val PEAK = Color(0.97f, 0.97f, 0.97f, 0.55f)
    }
}
