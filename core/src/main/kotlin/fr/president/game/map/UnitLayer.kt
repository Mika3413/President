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
    /** Explosions en cours (coordonnées monde) et chiffres de pertes qui s'élèvent au-dessus des batailles. */
    private class Burst(val x: Float, val y: Float, val start: Float, val size: Float, val big: Boolean = false)
    private val bursts = ArrayList<Burst>()
    private class Floater(val x: Float, val y: Float, val text: String, val color: Color, val start: Float) { var sx = 0f; var sy = 0f }
    private val floaters = ArrayList<Floater>()
    /** Pertes déjà affichées par bataille, et issue déjà annoncée. */
    private val shownLosses = HashMap<String, IntArray>()
    private val shownOutcome = HashSet<String>()
    private val random = java.util.Random(7)

    fun shapes(shapes: ShapeRenderer, camera: OrthographicCamera, session: GameSession, lod: Lod, layer: ThematicLayer, selectedUnit: String?, delta: Float) {
        time += delta
        counters.clear()
        arrivals.clear()
        battles.clear()
        val player = session.state.player.countryId
        val geo = session.military.geo
        val atWar = geo.isAtWar(player)
        // Dès qu'une guerre fait rage quelque part, ses armées se voient à toutes les échelles.
        val showForeign = layer == ThematicLayer.MILITARY || atWar || geo.activeWars().isNotEmpty()
        if (lod == Lod.WORLD) return
        if (lod == Lod.EUROPE && !showForeign && layer != ThematicLayer.MILITARY) return
        val visible = visibleCache(session).filter { it.countryId == player || showForeign }
        val zones = session.db.zones
        // Itinéraires des unités du joueur en mouvement.
        shapes.set(ShapeRenderer.ShapeType.Filled)
        val preview = session.military.preview
        for (u in visible.filter { it.countryId == player && it.path.isNotEmpty() }) {
            val here = worldPosition(session, u)
            var prev = projectWorld(camera, here[0], here[1])
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
        if (lod >= Lod.FRANCE || layer == ThematicLayer.MILITARY || atWar) drawWorks(shapes, camera, session, lod, layer)
        drawResistance(shapes, camera, session)
        battleEffects(shapes, camera, session, visible, delta)
        // Glissement des pions : la position affichée rejoint la zone réelle en quelques dixièmes de seconde.
        val follow = 1f - Math.exp((-delta * GLIDE_SPEED).toDouble()).toFloat()
        val alive = HashSet<String>()
        // Unités en marche : chacune avance à sa vraie place sur le trajet, entre deux zones.
        val (moving, still) = visible.partition { it.path.isNotEmpty() && !it.inCombat }
        for (u in moving) {
            alive += u.id
            val target = worldPosition(session, u)
            val pos = displayed.getOrPut(u.id) { target.copyOf() }
            pos[0] += (target[0] - pos[0]) * follow
            pos[1] += (target[1] - pos[1]) * follow
            val p = projectWorld(camera, pos[0], pos[1])
            val domain = session.db.unitTypes[u.type]?.domain ?: Domain.LAND
            // Traînée derrière le pion : il avance.
            val back = projectWorld(camera, zonesX(session, u.zoneId), zonesY(session, u.zoneId))
            shapes.color = TRAIL
            shapes.rectLine(back.x, back.y, p.x, p.y, PATH_WIDTH * 2)
            drawCounter(shapes, u, domain, p.x, p.y, colorOf(session, u), u.id == selectedUnit)
            counters += Counter(listOf(u.id), p.x, p.y)
        }
        for ((zoneId, units) in still.groupBy { it.zoneId }) {
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
        for (f in floaters) {
            val age = time - f.start
            val alpha = (1f - age / FLOAT_SECONDS).coerceIn(0f, 1f)
            font.color = Color(f.color.r, f.color.g, f.color.b, alpha)
            font.draw(batch, f.text, f.sx, f.sy + age * FLOAT_RISE)
        }
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

    /** Ouvrages militaires : petits symboles sous le centre de la zone (les nôtres, nos alliés, et ceux de l'ennemi que l'on voit). */
    private fun drawWorks(shapes: ShapeRenderer, camera: OrthographicCamera, session: GameSession, lod: Lod, layer: ThematicLayer) {
        val works = session.state.military.works
        if (works.isEmpty()) return
        val player = session.state.player.countryId
        val geo = session.military.geo
        val observed = observedCache
        val zones = session.db.zones
        for ((zoneId, list) in works.groupBy { it.zoneId }) {
            val z = zones.zones[zoneId] ?: continue
            // Les lignes des pays en guerre sont de notoriété publique (images satellites, presse).
            // Les ouvrages des autres pays ne s'affichent qu'en vue rapprochée ou sur la couche militaire, pour ne pas encombrer.
            val close = lod >= Lod.REGION || layer == ThematicLayer.MILITARY
            val shown = list.filter { it.countryId == player || geo.allied(player, it.countryId) || it.countryId in geo.coBelligerents(player) ||
                geo.atWar(player, it.countryId) || close && (zoneId in observed || geo.isAtWar(it.countryId)) }
            if (shown.isEmpty()) continue
            // En vue d'ensemble, seuls les ouvrages en guerre ou les nôtres.
            if (lod < Lod.FRANCE && shown.none { it.countryId == player || geo.atWar(player, it.countryId) || geo.isAtWar(it.countryId) }) continue
            val p = project(camera, z.lon, z.lat) ?: continue
            shown.sortedBy { it.type }.forEachIndexed { i, w ->
                val x = p.x - WORK_OFFSET_X + i * (WORK_SIZE + 3f)
                val y = p.y - WORK_OFFSET_Y
                val base = when {
                    w.countryId == player -> Theme.accent
                    geo.atWar(player, w.countryId) -> Theme.bad
                    geo.allied(player, w.countryId) || w.countryId in geo.coBelligerents(player) -> Theme.good
                    else -> NEUTRAL
                }
                val alpha = if (w.level <= 0) 0.45f else 0.6f + 0.4f * w.condition.toFloat()
                drawWork(shapes, w.type, x, y, Color(base.r, base.g, base.b, alpha), w.level)
            }
        }
    }

    /** Zones occupées où la résistance agit : une flamme qui vacille (plus grande quand l'insurrection gronde). */
    private fun drawResistance(shapes: ShapeRenderer, camera: OrthographicCamera, session: GameSession) {
        val player = session.state.player.countryId
        val geo = session.military.geo
        for ((zoneId, o) in session.state.military.occupation) {
            if (o.resistance < 0.25) continue
            val occupier = session.state.military.occupied[zoneId] ?: continue
            if (occupier != player && geo.ownerOf(zoneId) != player && zoneId !in observedCache) continue
            val z = session.db.zones.zones[zoneId] ?: continue
            val p = project(camera, z.lon, z.lat) ?: continue
            val flicker = 0.8f + 0.2f * Math.sin((time * 9 + zoneId.hashCode()).toDouble()).toFloat()
            val r = (3f + 5f * o.resistance.toFloat()) * flicker
            val x = p.x + WORK_OFFSET_X; val y = p.y - WORK_OFFSET_Y
            shapes.color = Color(1f, 0.45f, 0.1f, 0.85f)
            shapes.triangle(x - r * 0.6f, y - r * 0.5f, x + r * 0.6f, y - r * 0.5f, x, y + r)
            shapes.color = Color(1f, 0.85f, 0.3f, 0.9f)
            shapes.triangle(x - r * 0.3f, y - r * 0.5f, x + r * 0.3f, y - r * 0.5f, x, y + r * 0.4f)
        }
    }

    private fun drawWork(shapes: ShapeRenderer, type: String, x: Float, y: Float, color: Color, level: Int) {
        val h = WORK_SIZE / 2
        shapes.color = Theme.border
        shapes.rect(x - h - 1, y - h - 1, WORK_SIZE + 2, WORK_SIZE + 2)
        shapes.color = color
        shapes.rect(x - h, y - h, WORK_SIZE, WORK_SIZE)
        shapes.color = Color.WHITE
        when (type) {
            "line" -> { // créneaux
                shapes.rect(x - h + 1, y - 1, WORK_SIZE - 2, 2f)
                shapes.rect(x - h + 1, y + 1, 2f, 2f); shapes.rect(x - 1, y + 1, 2f, 2f); shapes.rect(x + h - 3, y + 1, 2f, 2f)
            }
            "air_defense" -> { shapes.triangle(x - 3f, y - 3f, x + 3f, y - 3f, x, y + 3.5f) }
            "radar" -> { shapes.arc(x, y - 2.5f, 4.5f, 30f, 120f, 8); shapes.rect(x - 0.5f, y - 3.5f, 1f, 2f) }
            "airfield" -> { shapes.rectLine(x - 4f, y - 2f, x + 4f, y + 2f, 1.6f); shapes.rectLine(x - 1.5f, y + 2f, x + 1.5f, y - 1f, 1f) }
            "barracks" -> { shapes.rect(x - 3f, y - 3.5f, 6f, 3.5f); shapes.triangle(x - 4f, y, x + 4f, y, x, y + 3.5f) }
            "depot" -> { shapes.rectLine(x - 3f, y - 3f, x + 3f, y + 3f, 1.2f); shapes.rectLine(x - 3f, y + 3f, x + 3f, y - 3f, 1.2f) }
            "coastal" -> { shapes.circle(x, y + 1.5f, 2f, 8); shapes.rect(x - 0.6f, y - 3.5f, 1.2f, 4f); shapes.rect(x - 3f, y - 3.5f, 6f, 1.2f) }
        }
        // Niveau : un point par niveau sous le symbole.
        shapes.color = Theme.highlight
        repeat(level.coerceAtMost(3)) { i -> shapes.circle(x - h + 2f + i * 3.5f, y - h - 3f, 1.1f, 6) }
    }

    /**
     * Combats animés : explosions qui éclatent sur le champ de bataille, pertes qui s'affichent
     * au-dessus (rouge : les nôtres ; vert : celles de l'ennemi), issue annoncée.
     */
    private fun battleEffects(shapes: ShapeRenderer, camera: OrthographicCamera, session: GameSession, visible: List<UnitState>, delta: Float) {
        val zones = session.db.zones
        val player = session.state.player.countryId
        val camp = session.military.geo.coBelligerents(player) + player
        val fighting = visible.filter { it.inCombat }.map { it.zoneId }.toSet()
        val now = session.state.time
        // De nouvelles explosions, plus nombreuses dans les grosses batailles.
        for (zoneId in fighting) {
            val z = zones.zones[zoneId] ?: continue
            val n = visible.count { it.zoneId == zoneId }
            if (random.nextFloat() < delta * BURST_RATE * (1 + n * 0.3f) && bursts.size < MAX_BURSTS) {
                val jx = (random.nextFloat() - 0.5f) * JITTER
                val jy = (random.nextFloat() - 0.5f) * JITTER
                bursts += Burst(GeoProjection.x(z.lon) + jx, GeoProjection.y(z.lat) + jy, time, 3f + random.nextFloat() * 4f)
            }
        }
        // Pertes et issues des batailles que l'on voit.
        for (b in session.state.military.battles) {
            if (b.zoneId !in fighting && b.outcome.isEmpty()) continue
            if (now.daysUntil(b.lastAt) < -0.2) continue
            val z = zones.zones[b.zoneId] ?: continue
            val wx = GeoProjection.x(z.lon); val wy = GeoProjection.y(z.lat)
            val ours = b.attackers.any { it in camp } || b.defenders.any { it in camp }
            val weAttack = b.attackers.any { it in camp }
            val last = shownLosses.getOrPut(b.id) { intArrayOf(b.attackerLosses, b.defenderLosses) }
            val dA = b.attackerLosses - last[0]
            val dD = b.defenderLosses - last[1]
            if (dA > 0 || dD > 0) {
                last[0] = b.attackerLosses; last[1] = b.defenderLosses
                if (ours) {
                    val mine = if (weAttack) dA else dD
                    val theirs = if (weAttack) dD else dA
                    if (mine > 0) floaters += Floater(wx - 10f, wy, "−${fr.president.engine.util.Formatting.integer(mine.toDouble())}", Theme.bad, time)
                    if (theirs > 0) floaters += Floater(wx + 6f, wy, "−${fr.president.engine.util.Formatting.integer(theirs.toDouble())}", Theme.good, time)
                } else floaters += Floater(wx, wy, "−${fr.president.engine.util.Formatting.integer((dA + dD).toDouble())}", Theme.warning, time)
            }
            if (b.outcome.isNotEmpty() && shownOutcome.add(b.id)) {
                val won = (b.outcome.startsWith("Victoire") && weAttack) || (b.outcome.startsWith("Le défenseur") && ours && !weAttack)
                val text = if (!ours) b.outcome else if (won) "★ Victoire" else "✕ Défaite"
                floaters += Floater(wx - 16f, wy + 12f, text, if (!ours) Theme.warning else if (won) Theme.good else Theme.bad, time)
                bursts += Burst(wx, wy, time, 12f, big = true)
            }
        }
        shownLosses.keys.retainAll(session.state.military.battles.map { it.id }.toSet())
        floaters.removeAll { time - it.start > FLOAT_SECONDS }
        floaters.forEach { f -> projectWorld(camera, f.x, f.y).let { f.sx = it.x; f.sy = it.y + 14f } }
        // Dessin : un éclair jaune qui vire au rouge puis s'efface.
        bursts.removeAll { time - it.start > BURST_SECONDS * (if (it.big) 2 else 1) }
        for (b in bursts) {
            val life = BURST_SECONDS * (if (b.big) 2 else 1)
            val t = ((time - b.start) / life).coerceIn(0f, 1f)
            val p = projectWorld(camera, b.x, b.y)
            val r = b.size * (0.4f + t * 1.2f)
            shapes.color = Color(1f, 0.35f + 0.4f * (1 - t), 0.1f, 0.75f * (1 - t))
            shapes.circle(p.x, p.y, r, SEGMENTS)
            shapes.color = Color(1f, 0.95f, 0.6f, 0.9f * (1 - t) * (1 - t))
            shapes.circle(p.x, p.y, r * 0.45f, SEGMENTS)
        }
    }

    private var observedCache: Set<String> = emptySet()

    private var cacheHour = -1L
    private var cacheCount = -1
    private var cache: List<UnitState> = emptyList()

    /** Le renseignement est recalculé au plus une fois par heure du monde. */
    private fun visibleCache(session: GameSession): List<UnitState> {
        val hour = session.state.time.hourIndex
        val count = session.state.military.units.size
        if (hour != cacheHour || count != cacheCount) {
            cache = session.military.visibleUnits()
            observedCache = session.military.intelligence.observedZones(session.state.player.countryId)
            cacheHour = hour
            cacheCount = count
        }
        return cache.filter { !it.destroyed }
    }

    private fun drawCounter(shapes: ShapeRenderer, u: UnitState, domain: Domain, x: Float, y: Float, color: Color, selected: Boolean) {
        if (selected) { shapes.color = Theme.highlight; shapes.rect(x - COUNTER_W / 2 - 3, y - COUNTER_H / 2 - 5, COUNTER_W + 6, COUNTER_H + 9) }
        // Encerclée : un cadre orange qui clignote.
        if (u.isolatedDays > 0 && (time * 3).toInt() % 2 == 0) { shapes.color = Theme.warning; shapes.rect(x - COUNTER_W / 2 - 4, y - COUNTER_H / 2 - 4, COUNTER_W + 8, COUNTER_H + 8) }
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

    /** Position réelle d'une unité (coordonnées monde) : entre sa zone et la suivante, selon le chemin parcouru. */
    private fun worldPosition(session: GameSession, u: UnitState): FloatArray {
        val zones = session.db.zones
        val from = zones.zones[u.zoneId] ?: return floatArrayOf(0f, 0f)
        val fx = GeoProjection.x(from.lon); val fy = GeoProjection.y(from.lat)
        val next = u.path.firstOrNull()?.let { zones.zones[it] } ?: return floatArrayOf(fx, fy)
        if (u.inCombat) return floatArrayOf(fx, fy)
        val leg = zones.distanceKm(u.zoneId, next.id).coerceAtLeast(1.0)
        // Entre deux pas horaires, on prolonge le mouvement au prorata de l'heure écoulée : le pion avance en continu.
        val sinceHour = Math.floorMod(session.state.time.seconds, 3600L) / 3600.0
        val speed = (session.db.unitTypes[u.type]?.speedKmPerDay ?: 0.0) / 24.0 * (if (u.fuel < 0.05) 0.2 else 1.0)
        val t = ((u.legProgressKm + speed * sinceHour) / leg).coerceIn(0.0, 1.0).toFloat()
        val nx = GeoProjection.x(next.lon); val ny = GeoProjection.y(next.lat)
        return floatArrayOf(fx + (nx - fx) * t, fy + (ny - fy) * t)
    }

    private fun zonesX(session: GameSession, id: String) = session.db.zones.zones[id]?.let { GeoProjection.x(it.lon) } ?: 0f
    private fun zonesY(session: GameSession, id: String) = session.db.zones.zones[id]?.let { GeoProjection.y(it.lat) } ?: 0f

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
        val TRAIL: Color = Color.valueOf("ffffff55")
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
        const val WORK_SIZE = 9f
        const val WORK_OFFSET_X = 14f
        const val WORK_OFFSET_Y = 14f
        const val BURST_RATE = 5f
        const val MAX_BURSTS = 80
        const val BURST_SECONDS = 0.7f
        const val JITTER = 22f
        const val FLOAT_SECONDS = 2.2f
        const val FLOAT_RISE = 14f
    }
}
