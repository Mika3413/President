package fr.president.game.ui.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import fr.president.game.ui.Theme
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Courbe simple et lisible : fond, ligne médiane, tracé coloré (vert si l'évolution récente est
 * bonne, rouge sinon), point sur la dernière valeur. Les valeurs sont hebdomadaires.
 */
class LineChart(
    private val white: TextureRegion,
    private val values: List<Double>,
    private val higherIsBetter: Boolean,
    private val chartHeight: Float = DEFAULT_HEIGHT,
) : Widget() {
    private val color = Color()

    override fun getPrefHeight(): Float = chartHeight
    override fun getPrefWidth(): Float = DEFAULT_WIDTH

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.color = color.set(Theme.panelAlt).also { it.a *= parentAlpha }
        batch.draw(white, x, y, width, height)
        batch.color = color.set(Theme.panelBorder).also { it.a *= parentAlpha }
        batch.draw(white, x, y + height / 2, width, 1f)
        if (values.size < 2) {
            batch.color = Color.WHITE
            return
        }
        var min = values.min()
        var max = values.max()
        if (max - min < MIN_RANGE) {
            val mid = (max + min) / 2
            min = mid - MIN_RANGE / 2
            max = mid + MIN_RANGE / 2
        }
        val pad = PAD
        fun px(i: Int) = x + pad + (width - 2 * pad) * i / (values.size - 1)
        fun py(v: Double) = y + pad + ((v - min) / (max - min)).toFloat() * (height - 2 * pad)
        val rising = values.last() >= values[maxOf(0, values.size - 1 - RECENT)]
        val good = rising == higherIsBetter || values.last() == values[maxOf(0, values.size - 1 - RECENT)]
        batch.color = color.set(if (good) Theme.good else Theme.bad).also { it.a *= parentAlpha }
        for (i in 1 until values.size) {
            val x1 = px(i - 1); val y1 = py(values[i - 1])
            val x2 = px(i); val y2 = py(values[i])
            val dx = x2 - x1; val dy = y2 - y1
            val length = sqrt(dx * dx + dy * dy)
            val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            batch.draw(white, x1, y1 - LINE / 2, 0f, LINE / 2, length, LINE, 1f, 1f, angle)
        }
        val lx = px(values.size - 1); val ly = py(values.last())
        batch.draw(white, lx - DOT / 2, ly - DOT / 2, DOT, DOT)
        batch.color = Color.WHITE
    }

    private companion object {
        const val DEFAULT_HEIGHT = 54f
        const val DEFAULT_WIDTH = 200f
        const val MIN_RANGE = 0.004
        const val PAD = 5f
        const val LINE = 2f
        const val DOT = 6f
        const val RECENT = 4
    }
}
