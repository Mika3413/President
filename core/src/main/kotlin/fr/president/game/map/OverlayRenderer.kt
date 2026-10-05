package fr.president.game.map

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.Gdx
import fr.president.engine.territory.ProjectStatus
import fr.president.engine.world.WorldState
import fr.president.game.ui.Theme

/**
 * Éléments dessinés en espace écran (taille constante quel que soit le zoom) :
 * marqueurs, chantiers, crises récentes et étiquettes sans chevauchement.
 */
class OverlayRenderer(
    private val data: MapData,
    private val labelFont: BitmapFont,
    private val smallFont: BitmapFont,
    private val uiScale: Float,
) : Disposable {
    private val shapes = ShapeRenderer()
    private val batch = SpriteBatch()
    private val screen = Matrix4()
    private val tmp = Vector3()
    private val layout = GlyphLayout()
    private val placedLabels = mutableListOf<Rectangle>()
    private var time = 0f

    /** Marqueurs effectivement visibles à l'écran (pour la sélection au toucher). */
    val visibleMarkers = mutableListOf<Pair<MapMarker, Vector3>>()

    val units = UnitLayer(smallFont, uiScale)

    fun render(camera: OrthographicCamera, session: fr.president.engine.session.GameSession, layer: ThematicLayer, lod: Lod, delta: Float, selectedId: String?) {
        val state = session.state
        time += delta
        val w = Gdx.graphics.width / uiScale
        val h = Gdx.graphics.height / uiScale
        screen.setToOrtho2D(0f, 0f, w, h)
        visibleMarkers.clear()
        placedLabels.clear()
        Gdx.gl.glEnable(GL20.GL_BLEND)

        shapes.projectionMatrix = screen
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawCrises(camera, state, lod)
        drawProjects(camera, state)
        selectedId?.let { id -> data.markersById[id]?.let { m -> toScreen(camera, m.x, m.y)?.let { p ->
            shapes.color = Theme.highlight
            shapes.circle(p.x, p.y, SELECTION_RADIUS, SEGMENTS)
        } } }
        for (m in data.markers) {
            if (!visible(m, lod, layer) && m.id != selectedId) continue
            val p = toScreen(camera, m.x, m.y) ?: continue
            visibleMarkers += m to p
            drawMarker(m, p, state, session)
        }
        units.shapes(shapes, camera, session, lod, layer, selectedId, delta)
        shapes.end()

        batch.projectionMatrix = screen
        batch.begin()
        // Les villes sont prioritaires sur les noms de territoires en cas de chevauchement.
        for ((m, p) in visibleMarkers.sortedBy { it.first.rank }) {
            if (m.kind == MarkerKind.CITY || m.kind == MarkerKind.FOREIGN_CITY || lod == Lod.LOCAL) {
                label(m.label, p.x + LABEL_OFFSET, p.y + LABEL_OFFSET, if (m.rank == 1) labelFont else smallFont, if (m.kind == MarkerKind.FOREIGN_CITY && m.rank > 1) Theme.textMuted else Theme.text)
            }
        }
        drawAreaLabels(camera, lod, layer, state)
        drawNetworkLabels(camera, lod)
        units.labels(batch, session)
        batch.end()
    }

    /** Numéros d'autoroutes et noms des fleuves, au milieu de leur plus long tronçon visible. */
    private fun drawNetworkLabels(camera: OrthographicCamera, lod: Lod) {
        if (lod < Lod.REGION) return
        for (n in data.networks) {
            if (n.kind == "RAIL_HIGH_SPEED" && lod < Lod.LOCAL) continue
            var best = -1f; var bx = 0f; var by = 0f
            for (i in 0 until n.points.size - 2 step 2) {
                val a = toScreen(camera, n.points[i], n.points[i + 1]) ?: continue
                val b = toScreen(camera, n.points[i + 2], n.points[i + 3]) ?: continue
                val len = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
                if (len > best) { best = len; bx = (a.x + b.x) / 2; by = (a.y + b.y) / 2 }
            }
            if (best < MIN_SEGMENT_PX * MIN_SEGMENT_PX) continue
            val text = when (n.kind) {
                "MOTORWAY" -> n.name.substringBefore(' ')
                "RAIL_HIGH_SPEED" -> "LGV"
                else -> n.name
            }
            val color = when (n.kind) { "RIVER" -> Theme.river; "MOTORWAY" -> Theme.motorway; else -> Theme.rail }
            label(text, bx, by, smallFont, color)
        }
    }

    private fun visible(m: MapMarker, lod: Lod, layer: ThematicLayer): Boolean = when (m.kind) {
        MarkerKind.CITY -> when (lod) {
            Lod.WORLD -> false
            Lod.EUROPE -> m.rank == 1 && m.id == CAPITAL
            Lod.FRANCE -> m.rank == 1
            Lod.REGION -> m.rank <= 3
            Lod.LOCAL -> true
        }
        // Villes étrangères : capitales vues de loin, grandes villes à l'échelle de l'Europe.
        MarkerKind.FOREIGN_CITY -> when (lod) {
            Lod.WORLD -> m.rank == 1
            Lod.EUROPE -> m.rank <= 2
            else -> true
        }
        MarkerKind.NUCLEAR, MarkerKind.POWER, MarkerKind.INDUSTRY ->
            lod >= Lod.REGION || (lod == Lod.FRANCE && layer == ThematicLayer.ENERGY)
        MarkerKind.PORT, MarkerKind.AIRPORT -> lod >= Lod.REGION || (lod == Lod.FRANCE && layer == ThematicLayer.TRANSPORT)
        MarkerKind.MILITARY -> lod >= Lod.REGION || (lod == Lod.FRANCE && layer == ThematicLayer.MILITARY)
    }

    /** Couleur de l'anneau d'une ville étrangère selon qui la tient (rien si son propriétaire). */
    private fun holderRing(session: fr.president.engine.session.GameSession, id: String): Color? {
        val owner = data.foreignCountry[id] ?: return null
        val holder = session.worldCities.holder(id) ?: return null
        val player = session.state.player.countryId
        return when {
            holder == player -> Theme.accent
            session.military.geo.atWar(player, holder) -> Theme.bad
            holder != owner -> Theme.warning
            else -> null
        }
    }

    private fun drawMarker(m: MapMarker, p: Vector3, state: WorldState, session: fr.president.engine.session.GameSession) {
        val offline = state.infrastructure[m.id]?.let { !it.isOperational(state.time) } ?: false
        when (m.kind) {
            MarkerKind.CITY -> {
                val r = if (m.rank == 1) CITY_MAJOR else if (m.rank == 2) CITY_MEDIUM else CITY_MINOR
                shapes.color = Theme.border; shapes.circle(p.x, p.y, r + 1.5f, SEGMENTS)
                shapes.color = if (m.id == CAPITAL) Theme.highlight else Color.WHITE; shapes.circle(p.x, p.y, r, SEGMENTS)
            }
            MarkerKind.FOREIGN_CITY -> {
                holderRing(session, m.id)?.let { ring -> shapes.color = ring; shapes.circle(p.x, p.y, CITY_MAJOR + RING, SEGMENTS) }
                if (m.rank == 1) {
                    // Capitale : carré blanc cerclé, comme sur les cartes de Supremacy.
                    square(p, Color.WHITE, CITY_MEDIUM)
                    shapes.color = Theme.border; shapes.rect(p.x - 1.5f, p.y - 1.5f, 3f, 3f)
                } else {
                    val r = if (m.rank == 2) CITY_MEDIUM else CITY_MINOR
                    shapes.color = Theme.border; shapes.circle(p.x, p.y, r + 1.5f, SEGMENTS)
                    shapes.color = FOREIGN_CITY_COLOR; shapes.circle(p.x, p.y, r, SEGMENTS)
                }
            }
            MarkerKind.NUCLEAR, MarkerKind.POWER -> square(p, if (offline) Theme.bad else Color.valueOf("ffd23f"), ICON)
            MarkerKind.INDUSTRY -> square(p, if (offline) Theme.bad else Color.valueOf("b08968"), ICON * 0.8f)
            MarkerKind.PORT, MarkerKind.AIRPORT -> diamond(p, if (offline) Theme.bad else Color.valueOf("4cc9f0"))
            MarkerKind.MILITARY -> {
                shapes.color = Theme.border; shapes.triangle(p.x - ICON - 1, p.y - ICON, p.x + ICON + 1, p.y - ICON, p.x, p.y + ICON + 1.5f)
                shapes.color = Color.valueOf("90be6d"); shapes.triangle(p.x - ICON, p.y - ICON + 1, p.x + ICON, p.y - ICON + 1, p.x, p.y + ICON)
            }
        }
    }

    private fun square(p: Vector3, c: Color, size: Float) {
        shapes.color = Theme.border; shapes.rect(p.x - size - 1, p.y - size - 1, (size + 1) * 2, (size + 1) * 2)
        shapes.color = c; shapes.rect(p.x - size, p.y - size, size * 2, size * 2)
    }

    private fun diamond(p: Vector3, c: Color) {
        shapes.color = c
        shapes.triangle(p.x - ICON, p.y, p.x + ICON, p.y, p.x, p.y + ICON)
        shapes.triangle(p.x - ICON, p.y, p.x + ICON, p.y, p.x, p.y - ICON)
    }

    /** Crises récentes : cercle rouge pulsant sur le lieu concerné. */
    private fun drawCrises(camera: OrthographicCamera, state: WorldState, lod: Lod) {
        if (lod == Lod.WORLD) return
        val pulse = (Math.sin(time * PULSE_SPEED.toDouble()).toFloat() + 1f) / 2f
        state.events.news.filter { it.focusId != null && it.time.daysUntil(state.time) < CRISIS_DAYS }.forEach { n ->
            val (x, y) = locate(n.focusId!!) ?: return@forEach
            val p = toScreen(camera, x, y) ?: return@forEach
            shapes.color = Color(Theme.bad.r, Theme.bad.g, Theme.bad.b, CRISIS_ALPHA * (1 - pulse))
            shapes.circle(p.x, p.y, CRISIS_RADIUS + pulse * CRISIS_RADIUS, SEGMENTS)
        }
    }

    private fun drawProjects(camera: OrthographicCamera, state: WorldState) {
        state.projects.filter { it.status == ProjectStatus.IN_PROGRESS }.forEach { project ->
            val (x, y) = locate(project.locationId) ?: return@forEach
            val p = toScreen(camera, x, y) ?: return@forEach
            shapes.color = PROJECT_COLOR
            shapes.arc(p.x, p.y, PROJECT_RADIUS, 90f, -360f * project.progress(state.time).toFloat(), SEGMENTS)
        }
    }

    /** Position monde d'un identifiant de carte (marqueur ou département). */
    fun locate(id: String): Pair<Float, Float>? {
        data.markersById[id]?.let { return it.x to it.y }
        data.departmentsById[id]?.let { return it.labelX to it.labelY }
        data.countriesById[id]?.let { return it.labelX to it.labelY }
        return null
    }

    private fun drawAreaLabels(camera: OrthographicCamera, lod: Lod, layer: ThematicLayer, state: fr.president.engine.world.WorldState) {
        // Couche thématique : le chiffre de chaque département, lisible sans rien toucher.
        if (layer.heatmap && lod != Lod.WORLD && lod != Lod.EUROPE) {
            val named = lod != Lod.FRANCE
            data.departments.forEach { f ->
                val value = state.territory.departments[f.id]?.let { layer.figure(it) } ?: return@forEach
                toScreen(camera, f.labelX, f.labelY)?.let {
                    label(if (named) "${f.name} · $value" else value, it.x, it.y - REGION_LABEL_DROP, if (named) smallFont else labelFont, Theme.text, centered = true)
                }
            }
            return
        }
        when (lod) {
            Lod.WORLD, Lod.EUROPE -> data.countries.filter { it.area > MIN_LABEL_AREA * (if (lod == Lod.WORLD) WORLD_LABEL_FACTOR else 1f) }
                .sortedByDescending { it.area }.forEach { f -> toScreen(camera, f.labelX, f.labelY)?.let { label(f.name.uppercase(), it.x, it.y, smallFont, Theme.textMuted, centered = true) } }
            Lod.FRANCE -> data.regions.forEach { f -> toScreen(camera, f.labelX, f.labelY)?.let { label(f.name, it.x, it.y - REGION_LABEL_DROP, smallFont, Theme.textMuted, centered = true) } }
            Lod.REGION, Lod.LOCAL -> data.departments.forEach { f -> toScreen(camera, f.labelX, f.labelY)?.let { label("${f.name} (${f.id})", it.x, it.y - REGION_LABEL_DROP, smallFont, Theme.textMuted, centered = true) } }
        }
    }

    private fun label(text: String, x: Float, y: Float, font: BitmapFont, color: Color, centered: Boolean = false) {
        layout.setText(font, text)
        val left = if (centered) x - layout.width / 2 else x
        val rect = Rectangle(left - LABEL_MARGIN, y - layout.height - LABEL_MARGIN, layout.width + 2 * LABEL_MARGIN, layout.height + 2 * LABEL_MARGIN)
        if (placedLabels.any { it.overlaps(rect) }) return
        placedLabels += rect
        font.color = Color.BLACK
        font.draw(batch, text, left + 1, y - 1)
        font.color = color
        font.draw(batch, text, left, y)
    }

    private fun toScreen(camera: OrthographicCamera, x: Float, y: Float): Vector3? {
        tmp.set(x, y, 0f)
        camera.project(tmp)
        val sx = tmp.x / uiScale
        val sy = tmp.y / uiScale
        val w = Gdx.graphics.width / uiScale
        val h = Gdx.graphics.height / uiScale
        if (sx < -MARGIN || sy < -MARGIN || sx > w + MARGIN || sy > h + MARGIN) return null
        return Vector3(sx, sy, 0f)
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
    }

    private companion object {
        const val MIN_SEGMENT_PX = 60f
        const val CAPITAL = "paris"
        const val SEGMENTS = 16
        const val CITY_MAJOR = 4.5f
        const val CITY_MEDIUM = 3.5f
        const val CITY_MINOR = 2.5f
        const val ICON = 4.5f
        const val LABEL_OFFSET = 5f
        const val LABEL_MARGIN = 2f
        const val REGION_LABEL_DROP = -4f
        const val MARGIN = 40f
        const val CRISIS_DAYS = 10.0
        const val CRISIS_RADIUS = 9f
        const val CRISIS_ALPHA = 0.8f
        const val PULSE_SPEED = 3f
        const val PROJECT_RADIUS = 8f
        const val SELECTION_RADIUS = 10f
        val PROJECT_COLOR: Color = Color.valueOf("f77f00b3")
        val FOREIGN_CITY_COLOR: Color = Color.valueOf("e6e1d3")
        const val RING = 3f
        const val MIN_LABEL_AREA = 400f
        const val WORLD_LABEL_FACTOR = 8f
    }
}
