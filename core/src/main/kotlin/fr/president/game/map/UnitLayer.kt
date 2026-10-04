package fr.president.game.map

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import fr.president.engine.data.Domain
import fr.president.engine.military.UnitState
import fr.president.engine.session.GameSession
import fr.president.game.ui.Theme

/**
 * Unités militaires sur la carte, façon pions : couleur selon le camp, symbole selon le domaine
 * (terre ■, air ▲, mer ●), barre d'effectifs, empilement par zone, combats et itinéraires.
 * Seules les unités connues du renseignement sont affichées.
 */
class UnitLayer(private val font: BitmapFont, private val uiScale: Float) {
    class Counter(val unitIds: List<String>, val x: Float, val y: Float)

    val counters = mutableListOf<Counter>()
    /** Destinations de nos unités en mouvement (écran) et temps restant, pour les étiquettes. */
    private class Arrival(val x: Float, val y: Float, val hours: Double)
    private val arrivals = mutableListOf<Arrival>()
    /** Batailles visibles (écran) et rapport de forces de notre camp, s'il y participe. */
    private class Battle(val x: Float, val y: Float, val ratio: Double?)
    private val battles = mutableListOf<Battle>()
    private val tmp = Vector3()
    private var time = 0f
    /** Position affichée de chaque unité (coordonnées monde), qui glisse vers sa zone réelle. */
    private val displayed = HashMap<String, FloatArray>()

    fun shapes(shapes: ShapeRenderer, camera: OrthographicCamera, session: GameSession, lod: Lod, layer: ThematicLayer, selectedUnit: String?, delta: Float) {
        time += delta
        counters.clear()
        arrivals.clear()
        battles.clear()
        val player = session.state.player.countryId
        val geo = session.military.geo
        val atWar = geo.isAtWar(player)
        val showForeign = layer == ThematicLayer.MILITARY || atWar || geo.activeWars().isNotEmpty() && lod <= Lod.EUROPE
        if (lod == Lod.WORLD) return
        if (lod == Lod.EUROPE && !showForeign && layer != ThematicLayer.MILITARY) return
        val visible = visibleCache(session).filter { it.countryId == player || showForeign }
        val zones = session.db.zones
        // Itinéraires des unités du joueur en mouvement.
        shapes.set(ShapeRenderer.ShapeType.Filled)
        val preview = session.military.preview
        for (u in visible.filter { it.countryId == player && it.path.isNotEmpty() }) {
            var prev = project(camera, zones.zone(u.zoneId).lon, zones.zone(u.zoneId).lat) ?: continue
            val attacking = u.order == fr.president.engine.military.UnitOrder.ATTACK
            shapes.color = if (u.id == selectedUnit) Theme.highlight else if (attacking) ATTACK_PATH else PATH
            var before = prev
            for (z in u.path) {
                val zz = zones.zones[z] ?: break
                val next = project(camera, zz.lon, zz.lat) ?: break
                shapes.rectLine(prev.x, prev.y, next.x, next.y, if (attacking) PATH_WIDTH * 1.5f else PATH_WIDTH)
                before = prev
                prev = next
            }
            // Pointe de flèche à l'arrivée, orientée selon le dernier tronçon.
            val dx = prev.x - before.x
            val dy = prev.y - before.y
            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (len > 0.5f) {
                val ux = dx / len; val uy = dy / len
                shapes.triangle(prev.x, prev.y, prev.x - ux * ARROW + uy * ARROW / 2, prev.y - uy * ARROW - ux * ARROW / 2,
                    prev.x - ux * ARROW - uy * ARROW / 2, prev.y - uy * ARROW + ux * ARROW / 2)
            }
            preview.remainingHours(u)?.let { arrivals += Arrival(prev.x, prev.y, it) }
        }
        // Combats : halo rouge pulsant.
        val pulse = (Math.sin(time * PULSE.toDouble()).toFloat() + 1) / 2
        for (zone in visible.filter { it.inCombat }.map { it.zoneId }.distinct()) {
            val z = zones.zones[zone] ?: continue
            val p = project(camera, z.lon, z.lat) ?: continue
            shapes.color = Color(Theme.bad.r, Theme.bad.g, Theme.bad.b, BATTLE_ALPHA * (1 - pulse))
            shapes.circle(p.x, p.y, BATTLE_RADIUS + pulse * BATTLE_RADIUS, SEGMENTS)
            // Épées croisées au-dessus de la bataille.
            val bx = p.x; val by = p.y + BATTLE_RADIUS + 6f
            shapes.color = Theme.border; shapes.circle(bx, by, SWORD + 3f, SEGMENTS)
            shapes.color = Theme.bad; shapes.circle(bx, by, SWORD + 2f, SEGMENTS)
            shapes.color = Color.WHITE
            shapes.rectLine(bx - SWORD, by - SWORD, bx + SWORD, by + SWORD, 1.6f)
            shapes.rectLine(bx - SWORD, by + SWORD, bx + SWORD, by - SWORD, 1.6f)
            val involved = visible.any { it.zoneId == zone && (it.countryId == player || it.countryId in geo.coBelligerents(player)) }
            battles += Battle(bx, by, if (involved) preview.battleRatio(zone, player) else null)
        }
        // Glissement des pions : la position affichée rejoint la zone réelle en quelques dixièmes de seconde.
        val follow = 1f - Math.exp((-delta * GLIDE_SPEED).toDouble()).toFloat()
        val alive = HashSet<String>()
        for ((zoneId, units) in visible.groupBy { it.zoneId }) {
            val z = zones.zones[zoneId] ?: continue
            val base = project(camera, z.lon, z.lat) ?: continue
            val tx = GeoProjection.x(z.lon)
            val ty = GeoProjection.y(z.lat)
            val sorted = units.sortedByDescending { it.countryId == player }
            val shown = sorted.take(MAX_STACK)
            shown.forEachIndexed { i, u ->
                alive += u.id
                val pos = displayed.getOrPut(u.id) { floatArrayOf(tx, ty) }
                pos[0] += (tx - pos[0]) * follow
                pos[1] += (ty - pos[1]) * follow
                val p = projectWorld(camera, pos[0], pos[1])
                val domain = session.db.unitTypes[u.type]?.domain ?: Domain.LAND
                drawCounter(shapes, u, domain, p.x + i * STACK_OFFSET, p.y - i * STACK_OFFSET, colorOf(session, u), u.id == selectedUnit)
            }
            counters += Counter(sorted.map { it.id }, base.x, base.y)
        }
        displayed.keys.retainAll(alive)
    }

    fun labels(batch: SpriteBatch, session: GameSession) {
        val units = session.state.military.units
        for (a in arrivals) {
            val h = Math.round(a.hours).toInt().coerceAtLeast(1)
            font.color = Theme.highlight
            font.draw(batch, "◷ " + if (h < 24) "$h h" else "${h / 24} j ${h % 24} h", a.x + 6f, a.y - 4f)
        }
        for (b in battles) {
            val r = b.ratio ?: continue
            font.color = if (r >= 1.3) Theme.good else if (r >= 0.8) Theme.warning else Theme.bad
            font.draw(batch, String.format(java.util.Locale.FRENCH, "%.1f : 1", r), b.x + SWORD + 6f, b.y + 5f)
        }
        for (c in counters) {
            if (c.unitIds.size > 1) {
                font.color = Color.WHITE
                font.draw(batch, c.unitIds.size.toString(), c.x + COUNTER_W / 2 + 2, c.y + COUNTER_H)
            }
            units[c.unitIds.first()]?.takeIf { it.countryId == session.state.player.countryId && it.inCombat }?.let {
                font.color = Theme.bad
                font.draw(batch, "✕", c.x - COUNTER_W, c.y + COUNTER_H)
            }
        }
    }

    private var cacheHour = -1L
    private var cacheCount = -1
    private var cache: List<UnitState> = emptyList()

    /** Le renseignement est recalculé au plus une fois par heure du monde. */
    private fun visibleCache(session: GameSession): List<UnitState> {
        val hour = session.state.time.hourIndex
        val count = session.state.military.units.size
        if (hour != cacheHour || count != cacheCount) {
            cache = session.military.visibleUnits()
            cacheHour = hour
            cacheCount = count
        }
        return cache.filter { !it.destroyed }
    }

    private fun drawCounter(shapes: ShapeRenderer, u: UnitState, domain: Domain, x: Float, y: Float, color: Color, selected: Boolean) {
        if (selected) { shapes.color = Theme.highlight; shapes.rect(x - COUNTER_W / 2 - 3, y - COUNTER_H / 2 - 5, COUNTER_W + 6, COUNTER_H + 9) }
        shapes.color = Theme.border
        shapes.rect(x - COUNTER_W / 2 - 1, y - COUNTER_H / 2 - 1, COUNTER_W + 2, COUNTER_H + 2)
        shapes.color = color
        shapes.rect(x - COUNTER_W / 2, y - COUNTER_H / 2, COUNTER_W, COUNTER_H)
        shapes.color = Color.WHITE
        when (domain) {
            Domain.AIR -> shapes.triangle(x - 3.5f, y - 2.5f, x + 3.5f, y - 2.5f, x, y + 3f)
            Domain.SEA -> shapes.circle(x, y, 3f, SEGMENTS)
            Domain.STRATEGIC -> shapes.circle(x, y, 2f, SEGMENTS)
            Domain.LAND -> { shapes.rectLine(x - 4, y - 3, x + 4, y + 3, 1.2f); shapes.rectLine(x - 4, y + 3, x + 4, y - 3, 1.2f) }
        }
        // Barre d'effectifs.
        shapes.color = Theme.border
        shapes.rect(x - COUNTER_W / 2, y - COUNTER_H / 2 - 3, COUNTER_W, 2f)
        shapes.color = if (u.strength > 0.6) Theme.good else if (u.strength > 0.3) Theme.warning else Theme.bad
        shapes.rect(x - COUNTER_W / 2, y - COUNTER_H / 2 - 3, COUNTER_W * u.strength.toFloat(), 2f)
    }

    private fun colorOf(session: GameSession, u: UnitState): Color {
        val player = session.state.player.countryId
        val geo = session.military.geo
        return when {
            u.countryId == player -> Theme.accent
            geo.atWar(player, u.countryId) -> Theme.bad
            u.countryId in geo.coBelligerents(player) || geo.allied(player, u.countryId) -> Theme.good
            else -> NEUTRAL
        }
    }

    private fun projectWorld(camera: OrthographicCamera, x: Float, y: Float): Vector3 {
        tmp.set(x, y, 0f)
        camera.project(tmp)
        return Vector3(tmp.x / uiScale, tmp.y / uiScale, 0f)
    }

    private fun project(camera: OrthographicCamera, lon: Double, lat: Double): Vector3? {
        tmp.set(GeoProjection.x(lon), GeoProjection.y(lat), 0f)
        camera.project(tmp)
        val x = tmp.x / uiScale
        val y = tmp.y / uiScale
        return Vector3(x, y, 0f)
    }

    private companion object {
        val PATH: Color = Color.valueOf("4c9be8cc")
        val ATTACK_PATH: Color = Color.valueOf("ff6b5ee6")
        const val ARROW = 9f
        const val SWORD = 4f
        val NEUTRAL: Color = Color.valueOf("8a8f98")
        const val COUNTER_W = 14f
        const val GLIDE_SPEED = 6f
        const val COUNTER_H = 10f
        const val STACK_OFFSET = 3f
        const val MAX_STACK = 3
        const val PATH_WIDTH = 1.5f
        const val SEGMENTS = 12
        const val PULSE = 4f
        const val BATTLE_RADIUS = 10f
        const val BATTLE_ALPHA = 0.7f
    }
}
