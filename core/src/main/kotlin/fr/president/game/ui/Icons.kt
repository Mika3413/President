package fr.president.game.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Jeu d'icônes dessinées par programme (aucune image à distribuer) : chaque icône est tracée en
 * grand puis réduite avec lissage, en blanc sur fond transparent, pour être teintée à l'usage.
 */
object Icons {
    private const val BIG = 128
    private const val CELL = 64
    private const val COLS = 8
    val NAMES = listOf(
        "decide", "stats", "inbox", "diplomacy", "army", "more", "office", "moments",
        "agenda", "alerts", "crisis", "unrest", "press", "replay", "book", "legislation",
        "laws", "government", "actors", "elections", "economy", "trade", "eu", "un",
        "world", "defense", "intel", "academy", "settings", "help",
    )
    private var texture: Texture? = null
    private val regions = HashMap<String, TextureRegion>()

    fun region(name: String): TextureRegion? {
        if (texture == null) build()
        return regions[name]
    }

    /** Planche de toutes les icônes (aussi utile pour les vérifier en développement). */
    fun atlas(): Pixmap {
        val rows = (NAMES.size + COLS - 1) / COLS
        val atlas = Pixmap(COLS * CELL, rows * CELL, Pixmap.Format.RGBA8888)
        atlas.filter = Pixmap.Filter.BiLinear
        NAMES.forEachIndexed { i, name ->
            val p = Pixmap(BIG, BIG, Pixmap.Format.RGBA8888)
            p.blending = Pixmap.Blending.None
            p.setColor(0f, 0f, 0f, 0f); p.fill()
            p.setColor(Color.WHITE)
            Pen(p).draw(name)
            atlas.drawPixmap(p, 0, 0, BIG, BIG, (i % COLS) * CELL, (i / COLS) * CELL, CELL, CELL)
            p.dispose()
        }
        return atlas
    }

    private fun build() {
        val atlas = atlas()
        val t = Texture(atlas).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        atlas.dispose()
        texture = t
        NAMES.forEachIndexed { i, name -> regions[name] = TextureRegion(t, (i % COLS) * CELL, (i / COLS) * CELL, CELL, CELL) }
    }

    /** Petits outils de tracé sur une toile de 128 × 128 (origine en haut à gauche). */
    private class Pen(val p: Pixmap) {
        fun ink() = p.setColor(Color.WHITE)
        fun erase() = p.setColor(0f, 0f, 0f, 0f)
        fun rect(x: Int, y: Int, w: Int, h: Int) = p.fillRectangle(x, y, w, h)
        fun circle(x: Int, y: Int, r: Int) = p.fillCircle(x, y, r)
        fun tri(ax: Int, ay: Int, bx: Int, by: Int, cx: Int, cy: Int) = p.fillTriangle(ax, ay, bx, by, cx, cy)
        fun ring(x: Int, y: Int, r: Int, w: Int) { ink(); circle(x, y, r); erase(); circle(x, y, r - w); ink() }
        fun frame(x: Int, y: Int, w: Int, h: Int, t: Int) { ink(); rect(x, y, w, h); erase(); rect(x + t, y + t, w - 2 * t, h - 2 * t); ink() }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
            val dx = x2 - x1; val dy = y2 - y1
            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(0.01f)
            val nx = -dy / len * w / 2; val ny = dx / len * w / 2
            tri((x1 + nx).toInt(), (y1 + ny).toInt(), (x1 - nx).toInt(), (y1 - ny).toInt(), (x2 + nx).toInt(), (y2 + ny).toInt())
            tri((x1 - nx).toInt(), (y1 - ny).toInt(), (x2 - nx).toInt(), (y2 - ny).toInt(), (x2 + nx).toInt(), (y2 + ny).toInt())
        }
        fun star(cx: Float, cy: Float, r: Float, inner: Float = r * 0.42f) {
            for (k in 0 until 5) {
                val a = -PI / 2 + k * 2 * PI / 5
                val l = a - PI / 5; val rr = a + PI / 5
                tri(cx.toInt(), cy.toInt(), (cx + cos(a) * r).toInt(), (cy + sin(a) * r).toInt(), (cx + cos(l) * inner).toInt(), (cy + sin(l) * inner).toInt())
                tri(cx.toInt(), cy.toInt(), (cx + cos(a) * r).toInt(), (cy + sin(a) * r).toInt(), (cx + cos(rr) * inner).toInt(), (cy + sin(rr) * inner).toInt())
            }
        }
        fun globe() {
            ring(64, 64, 50, 8)
            // Méridien et parallèles, tracés point par point.
            for (k in 0 until 72) {
                val a = k * 2 * PI / 72
                circle((64 + cos(a) * 22).toInt(), (64 + sin(a) * 50).toInt(), 4)
            }
            line(14f, 64f, 114f, 64f, 7f)
            line(26f, 38f, 102f, 38f, 6f)
            line(26f, 90f, 102f, 90f, 6f)
            line(64f, 14f, 64f, 114f, 6f)
        }

        fun draw(name: String) {
            ink()
            when (name) {
                "decide" -> star(64f, 68f, 54f)
                "stats" -> { rect(18, 70, 20, 40); rect(54, 44, 20, 66); rect(90, 20, 20, 90); rect(10, 110, 108, 8) }
                "inbox" -> { frame(12, 30, 104, 70, 9); line(16f, 36f, 64f, 74f, 9f); line(112f, 36f, 64f, 74f, 9f) }
                "diplomacy", "world" -> globe()
                "un" -> { globe(); for (k in 0 until 7) { val a = PI * 0.15 + k * PI * 0.1; circle((64 - cos(a) * 60).toInt(), (64 + sin(a) * 60).toInt(), 6); circle((64 + cos(a) * 60).toInt(), (64 + sin(a) * 60).toInt(), 6) } }
                "army" -> {
                    line(24f, 24f, 104f, 104f, 12f); line(104f, 24f, 24f, 104f, 12f)
                    line(34f, 96f, 52f, 114f, 10f); line(94f, 96f, 76f, 114f, 10f)
                    circle(20, 108, 9); circle(108, 108, 9)
                }
                "more" -> { rect(16, 26, 96, 12); rect(16, 58, 96, 12); rect(16, 90, 96, 12) }
                "office", "government" -> {
                    tri(10, 40, 118, 40, 64, 10)
                    for (k in 0 until 4) rect(20 + k * 25, 48, 12, 52)
                    rect(10, 104, 108, 12)
                    if (name == "government") { erase(); circle(64, 30, 7); ink() }
                }
                "moments" -> { circle(64, 30, 20); rect(44, 30, 40, 36); circle(64, 66, 20); line(64f, 88f, 64f, 112f, 8f); rect(40, 108, 48, 8); ring(64, 56, 34, 6); erase(); rect(20, 10, 88, 50); ink(); circle(64, 30, 20); rect(44, 30, 40, 36); circle(64, 66, 20) }
                "agenda" -> { frame(14, 24, 100, 92, 9); rect(14, 24, 100, 24); rect(34, 10, 10, 26); rect(84, 10, 10, 26); for (r in 0 until 2) for (c in 0 until 3) rect(30 + c * 26, 60 + r * 24, 14, 12) }
                "alerts" -> { circle(64, 50, 34); rect(30, 50, 68, 40); tri(14, 98, 114, 98, 64, 78); rect(14, 90, 100, 10); circle(64, 110, 11) }
                "crisis" -> { tri(64, 8, 4, 116, 124, 116); erase(); tri(64, 30, 24, 104, 104, 104); ink(); rect(57, 46, 14, 34); circle(64, 93, 8) }
                "unrest" -> { rect(20, 10, 10, 110); tri(30, 14, 112, 34, 30, 64); rect(30, 14, 50, 50) }
                "press" -> { frame(12, 16, 104, 96, 8); rect(26, 30, 76, 16); rect(26, 56, 34, 6); rect(26, 70, 34, 6); rect(26, 84, 34, 6); rect(68, 56, 34, 34) }
                "replay" -> { ring(64, 64, 54, 9); tri(48, 34, 48, 94, 96, 64) }
                "book" -> { tri(8, 30, 60, 40, 8, 104); tri(8, 104, 60, 40, 60, 114); tri(120, 30, 68, 40, 120, 104); tri(120, 104, 68, 40, 68, 114); erase(); rect(60, 30, 8, 90); ink() }
                "legislation" -> {
                    rect(59, 14, 10, 92); rect(30, 108, 68, 10); line(18f, 30f, 110f, 30f, 8f); circle(64, 16, 9)
                    line(22f, 34f, 10f, 74f, 4f); line(22f, 34f, 34f, 74f, 4f); line(106f, 34f, 94f, 74f, 4f); line(106f, 34f, 118f, 74f, 4f)
                    circle(22, 74, 16); circle(106, 74, 16); erase(); rect(0, 52, 128, 22); ink(); rect(59, 14, 10, 92)
                }
                "laws" -> { rect(28, 18, 72, 92); circle(28, 24, 12); circle(100, 104, 12); erase(); for (k in 0 until 4) rect(40, 36 + k * 16, 48, 5); ink() }
                "actors" -> { circle(44, 40, 18); circle(88, 46, 15); circle(44, 104, 34); circle(88, 106, 28); erase(); rect(0, 104, 128, 24); ink(); rect(10, 104, 108, 6) }
                "elections" -> { rect(20, 56, 88, 60); erase(); rect(48, 56, 32, 8); ink(); rect(44, 14, 40, 46); erase(); rect(52, 22, 24, 4); rect(52, 32, 24, 4); ink(); rect(20, 56, 88, 8) }
                "economy" -> { ring(70, 64, 46, 13); erase(); tri(70, 64, 128, 26, 128, 102); ink(); rect(10, 50, 60, 10); rect(10, 70, 60, 10) }
                "trade" -> { tri(8, 84, 120, 84, 104, 112); rect(24, 84, 80, 28); erase(); tri(8, 84, 24, 84, 24, 112); ink(); rect(26, 52, 26, 28); rect(56, 52, 26, 28); rect(41, 22, 26, 28); rect(86, 60, 20, 20) }
                "eu" -> for (k in 0 until 12) { val a = k * 2 * PI / 12; star((64 + cos(a) * 46).toFloat(), (64 + sin(a) * 46).toFloat(), 12f) }
                "defense" -> { rect(20, 14, 88, 56); tri(20, 70, 108, 70, 64, 120); erase(); rect(30, 24, 68, 44); tri(30, 68, 98, 68, 64, 106); ink(); star(64f, 60f, 22f) }
                "intel" -> { circle(64, 64, 48); erase(); rect(0, 0, 128, 34); rect(0, 94, 128, 34); ink(); tri(4, 64, 24, 44, 24, 84); tri(124, 64, 104, 44, 104, 84); erase(); circle(64, 64, 22); ink(); circle(64, 64, 13) }
                "academy" -> { tri(4, 50, 64, 20, 124, 50); tri(4, 50, 124, 50, 64, 80); rect(34, 62, 60, 34); circle(64, 96, 30); erase(); rect(0, 100, 128, 28); ink(); line(110f, 52f, 110f, 96f, 5f); circle(110, 100, 7) }
                "settings" -> { for (k in 0 until 8) { val a = k * PI / 4; line((64 + cos(a) * 30).toFloat(), (64 + sin(a) * 30).toFloat(), (64 + cos(a) * 56).toFloat(), (64 + sin(a) * 56).toFloat(), 18f) }; circle(64, 64, 40); erase(); circle(64, 64, 17); ink() }
                "help" -> { ring(64, 64, 56, 8); ring(64, 50, 22, 10); erase(); rect(30, 50, 34, 30); ink(); rect(57, 66, 12, 18); circle(64, 96, 7) }
            }
        }
    }
}
