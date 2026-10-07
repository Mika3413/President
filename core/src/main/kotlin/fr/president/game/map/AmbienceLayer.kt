package fr.president.game.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import fr.president.engine.session.GameSession
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vie de la carte, en espace écran : la nuit qui balaie le globe (heure solaire de chaque
 * longitude) et allume les villes, les missiles qui filent de leur base à leur cible, le
 * champignon d'une frappe nucléaire, et le feu d'artifice sur la capitale les jours de fête.
 */
class AmbienceLayer(private val data: MapData, private val uiScale: Float) {
    private val tmp = Vector3()
    private val color = Color()
    private val random = java.util.Random(5)
    private var time = 0f

    // --- Nuit ---------------------------------------------------------------------------------------

    /** Obscurité (0 = plein jour, 1 = nuit noire) à une longitude, selon l'heure UTC de la partie. */
    private fun darkness(utcHours: Double, lon: Double): Float {
        val local = utcHours + lon / 15.0
        val sun = cos(2 * PI * (local - 12) / 24)
        return smooth(((NIGHT_EDGE - sun) / (NIGHT_EDGE - NIGHT_FULL)).toFloat())
    }

    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    private fun utcHours(session: GameSession): Double = session.state.time.toDateTime().let { it.hour + it.minute / 60.0 }

    /** Voile de nuit par bandes de longitude, puis lumières des villes dans l'ombre. */
    fun night(shapes: ShapeRenderer, camera: OrthographicCamera, session: GameSession, lod: Lod) {
        if (!fr.president.game.ui.UserSettings.dayNight) return
        val utc = utcHours(session)
        val w = Gdx.graphics.width / uiScale
        val h = Gdx.graphics.height / uiScale
        val left = GeoProjection.lon(unproject(camera, 0f)).coerceAtLeast(-180.0)
        val right = GeoProjection.lon(unproject(camera, w)).coerceAtMost(180.0)
        if (right <= left) return
        val step = ((right - left) / BANDS).coerceAtLeast(0.05)
        var lon = left
        while (lon < right) {
            val d = darkness(utc, lon + step / 2)
            if (d > 0.01f) {
                val x0 = screenX(camera, GeoProjection.x(lon))
                val x1 = screenX(camera, GeoProjection.x(lon + step))
                shapes.color = color.set(NIGHT.r, NIGHT.g, NIGHT.b, NIGHT_ALPHA * d)
                shapes.rect(x0, 0f, x1 - x0 + 0.5f, h)
            }
            lon += step
        }
        // Les villes s'allument : halo chaud, plus grand pour les grandes villes.
        for (m in data.markers) {
            if (m.kind != MarkerKind.CITY && m.kind != MarkerKind.FOREIGN_CITY) continue
            val maxRank = when (lod) { Lod.WORLD -> 1; Lod.EUROPE, Lod.FRANCE -> 2; Lod.REGION -> 3; Lod.LOCAL -> 9 }
            if (m.rank > maxRank) continue
            val d = darkness(utc, GeoProjection.lon(m.x))
            if (d < 0.2f) continue
            tmp.set(m.x, m.y, 0f); camera.project(tmp)
            val sx = tmp.x / uiScale; val sy = tmp.y / uiScale
            if (sx < -10 || sy < -10 || sx > w + 10 || sy > h + 10) continue
            val flicker = 0.85f + 0.15f * sin(time * 2.3f + m.x * 0.37f)
            val r = (if (m.rank == 1) 9f else if (m.rank == 2) 6f else 4f) * flicker
            shapes.color = color.set(1f, 0.82f, 0.45f, 0.18f * d)
            shapes.circle(sx, sy, r, 14)
            shapes.color = color.set(1f, 0.9f, 0.6f, 0.35f * d)
            shapes.circle(sx, sy, r * 0.45f, 10)
        }
    }

    private fun unproject(camera: OrthographicCamera, sx: Float): Float {
        val w = camera.viewportWidth * camera.zoom
        return camera.position.x - w / 2 + sx * uiScale / Gdx.graphics.width * w
    }

    private fun screenX(camera: OrthographicCamera, x: Float): Float { tmp.set(x, 0f, 0f); camera.project(tmp); return tmp.x / uiScale }

    // --- Frappes ------------------------------------------------------------------------------------

    private class Flight(val fx: Float, val fy: Float, val tx: Float, val ty: Float, val start: Float, val nuclear: Boolean, val ours: Boolean)
    private val flights = ArrayList<Flight>()
    private val seenStrikes = HashSet<String>()
    private var strikesPrimed = false
    private class Blast(val x: Float, val y: Float, val start: Float, val nuclear: Boolean)
    private val blasts = ArrayList<Blast>()
    private var flash = -10f

    fun strikes(shapes: ShapeRenderer, camera: OrthographicCamera, session: GameSession, delta: Float) {
        time += delta
        val zones = session.db.zones.zones
        val player = session.state.player.countryId
        for (st in session.state.military.strikes) {
            val key = "${st.at.seconds}|${st.to}"
            if (!seenStrikes.add(key) || !strikesPrimed) continue
            if (st.at.daysUntil(session.state.time) > 1.0) continue
            val a = zones[st.from] ?: continue
            val b = zones[st.to] ?: continue
            val ax = GeoProjection.x(a.lon); val ay = GeoProjection.y(a.lat)
            val bx = GeoProjection.x(b.lon); val by = GeoProjection.y(b.lat)
            // Une salve : trois missiles décalés (un seul vecteur pour l'arme nucléaire).
            val n = if (st.nuclear) 1 else 3
            repeat(n) { i ->
                val j = if (st.from == st.to) 60f else 0f
                flights += Flight(ax - j + (random.nextFloat() - 0.5f) * 8, ay - j * 0.5f + (random.nextFloat() - 0.5f) * 8,
                    bx + (random.nextFloat() - 0.5f) * 10 * (n - 1), by + (random.nextFloat() - 0.5f) * 10 * (n - 1), time + i * 0.25f, st.nuclear, st.actor == player)
            }
        }
        strikesPrimed = true
        val done = ArrayList<Flight>()
        for (f in flights) {
            val t = ((time - f.start) / FLIGHT_SECONDS).coerceIn(0f, 1f)
            if (time < f.start) continue
            if (t >= 1f) { done += f; blasts += Blast(f.tx, f.ty, time, f.nuclear); if (f.nuclear) flash = time; continue }
            val p0 = project(camera, f.fx, f.fy); val p1 = project(camera, f.tx, f.ty)
            val dist = Math.hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat()
            val lift = (dist * 0.35f).coerceIn(20f, 160f)
            // Traînée : points de plus en plus pâles derrière la tête.
            for (k in TRAIL downTo 0) {
                val tt = (t - k * 0.025f).coerceAtLeast(0f)
                val x = p0.x + (p1.x - p0.x) * tt
                val y = p0.y + (p1.y - p0.y) * tt + sin(PI * tt).toFloat() * lift
                val fade = 1f - k / (TRAIL + 1f)
                shapes.color = if (k == 0) color.set(1f, 1f, 0.85f, 1f)
                else if (f.ours) color.set(0.55f, 0.75f, 1f, 0.55f * fade) else color.set(1f, 0.45f, 0.3f, 0.55f * fade)
                shapes.circle(x, y, if (k == 0) 2.6f else 1.8f * fade + 0.5f, 8)
            }
        }
        flights.removeAll(done.toSet())
        // Impacts : boule de feu (et champignon pour l'arme nucléaire).
        blasts.removeAll { time - it.start > if (it.nuclear) NUKE_SECONDS else BLAST_SECONDS }
        for (b in blasts) {
            val p = project(camera, b.x, b.y)
            val age = time - b.start
            if (!b.nuclear) {
                val t = age / BLAST_SECONDS
                shapes.color = color.set(1f, 0.4f + 0.4f * (1 - t), 0.1f, 0.8f * (1 - t))
                shapes.circle(p.x, p.y, 4f + t * 10f, 14)
                shapes.color = color.set(1f, 0.95f, 0.7f, (1 - t) * (1 - t))
                shapes.circle(p.x, p.y, 2f + t * 4f, 10)
                continue
            }
            val t = age / NUKE_SECONDS
            // Onde de choc.
            val ring = 10f + age * 60f
            shapes.color = color.set(1f, 1f, 1f, (0.5f * (1 - age / 1.5f)).coerceAtLeast(0f))
            for (k in 0 until 32) {
                val a0 = k * 2 * PI / 32; val a1 = (k + 1) * 2 * PI / 32
                shapes.rectLine(p.x + cos(a0).toFloat() * ring, p.y + sin(a0).toFloat() * ring, p.x + cos(a1).toFloat() * ring, p.y + sin(a1).toFloat() * ring, 2f)
            }
            // Pied, tige et chapeau du champignon qui monte et s'assombrit.
            val rise = (age / 2f).coerceAtMost(1f) * 46f
            val fade = (1 - t).coerceIn(0f, 1f)
            val heat = (1 - age / 3f).coerceIn(0f, 1f)
            shapes.color = color.set(0.55f + 0.45f * heat, 0.35f + 0.3f * heat, 0.2f, 0.75f * fade)
            shapes.circle(p.x, p.y, 10f + age * 2f, 18)
            shapes.rect(p.x - 4f, p.y, 8f, rise)
            shapes.color = color.set(0.6f + 0.4f * heat, 0.4f + 0.35f * heat, 0.25f + 0.1f * heat, 0.85f * fade)
            shapes.ellipse(p.x - 18f - age * 3, p.y + rise - 6f, 36f + age * 6, 18f + age * 2, 20)
            shapes.color = color.set(1f, 0.95f, 0.7f, 0.8f * heat * fade)
            shapes.circle(p.x, p.y + rise + 2f, 6f * heat + 1f, 12)
        }
    }

    /** Éclair blanc qui couvre l'écran à l'impact d'une arme nucléaire. */
    fun flash(shapes: ShapeRenderer) {
        val age = time - flash
        if (age > FLASH_SECONDS) return
        shapes.color = color.set(1f, 1f, 0.95f, 0.75f * (1 - age / FLASH_SECONDS))
        shapes.rect(0f, 0f, Gdx.graphics.width / uiScale, Gdx.graphics.height / uiScale)
    }

    // --- Feu d'artifice ------------------------------------------------------------------------------

    private class Rocket(val x: Float, val y: Float, val launch: Float, val height: Float, val drift: Float, val color: Color)
    private class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val start: Float, val color: Color)
    private val rockets = ArrayList<Rocket>()
    private val sparks = ArrayList<Spark>()

    /** Lance une salve de fusées au-dessus de la capitale du joueur. */
    fun fireworks() {
        val capital = data.markersById[data.capitalId] ?: return
        repeat(ROCKETS) { i ->
            val c = PALETTE[random.nextInt(PALETTE.size)]
            rockets += Rocket(capital.x, capital.y, time + i * 0.45f + random.nextFloat() * 0.3f, 28f + random.nextFloat() * 34f, (random.nextFloat() - 0.5f) * 60f, c)
        }
    }

    fun drawFireworks(shapes: ShapeRenderer, camera: OrthographicCamera) {
        val gone = ArrayList<Rocket>()
        for (r in rockets) {
            if (time < r.launch) continue
            val t = (time - r.launch) / ROCKET_SECONDS
            val base = project(camera, r.x, r.y)
            if (t >= 1f) {
                gone += r
                val px = base.x + r.drift; val py = base.y + r.height
                repeat(SPARKS) { k ->
                    val a = k * 2 * PI / SPARKS + random.nextFloat() * 0.2
                    val speed = 22f + random.nextFloat() * 18f
                    sparks += Spark(px - base.x, py - base.y, cos(a).toFloat() * speed, sin(a).toFloat() * speed, time, r.color)
                }
                continue
            }
            val ease = 1 - (1 - t) * (1 - t)
            shapes.color = color.set(1f, 0.9f, 0.7f, 0.9f)
            shapes.circle(base.x + r.drift * ease, base.y + r.height * ease, 1.6f, 6)
        }
        rockets.removeAll(gone.toSet())
        sparks.removeAll { time - it.start > SPARK_SECONDS }
        val capital = data.markersById[data.capitalId] ?: return
        val origin = project(camera, capital.x, capital.y)
        for (s in sparks) {
            val age = time - s.start
            val x = origin.x + s.x + s.vx * age
            val y = origin.y + s.y + s.vy * age - 18f * age * age
            val fade = 1 - age / SPARK_SECONDS
            shapes.color = color.set(s.color.r, s.color.g, s.color.b, fade)
            shapes.circle(x, y, 2.4f * fade + 0.8f, 6)
        }
    }

    private fun project(camera: OrthographicCamera, x: Float, y: Float): Vector3 {
        tmp.set(x, y, 0f); camera.project(tmp)
        return Vector3(tmp.x / uiScale, tmp.y / uiScale, 0f)
    }

    private companion object {
        val NIGHT = Color(0.02f, 0.04f, 0.16f, 1f)
        const val NIGHT_ALPHA = 0.38f
        /** Hauteur du soleil (cosinus) où la nuit commence, et où elle est complète. */
        const val NIGHT_EDGE = 0.08
        const val NIGHT_FULL = -0.3
        const val BANDS = 60
        const val FLIGHT_SECONDS = 1.8f
        const val TRAIL = 8
        const val BLAST_SECONDS = 0.9f
        const val NUKE_SECONDS = 6f
        const val FLASH_SECONDS = 0.9f
        const val ROCKETS = 14
        const val ROCKET_SECONDS = 0.9f
        const val SPARKS = 22
        const val SPARK_SECONDS = 1.6f
        val PALETTE = listOf(Color(1f, 0.3f, 0.3f, 1f), Color(0.35f, 0.6f, 1f, 1f), Color(1f, 1f, 1f, 1f), Color(1f, 0.85f, 0.3f, 1f), Color(0.5f, 1f, 0.5f, 1f))
    }
}
