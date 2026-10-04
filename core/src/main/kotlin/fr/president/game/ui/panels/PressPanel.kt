package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.engine.util.Formatting
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.widgets.LineChart

/**
 * « Presse » : les unes du jour (chaque journal a sa ligne), les sondages des instituts
 * et les préoccupations des Français. Comme le journal quotidien de Supremacy.
 */
class PressPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "▤ Presse et sondages"
    private val session get() = nav.session

    override fun build(into: Table) {
        val pages = session.media.frontPages()
        if (pages.isEmpty()) {
            into.add(ui.label("Les premiers journaux paraissent demain matin.", "muted", wrap = true)).growX().row()
        } else {
            into.add(ui.label("Les unes du ${Formats.date(pages.first().second.time)}", "bold")).padBottom(4f).row()
            pages.forEach { (paper, page) ->
                val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); defaults().left() }
                val mast = Table().apply { setBackground(ui.skin.fill(Color.valueOf(paper.color))); pad(3f, 8f, 3f, 8f) }
                mast.add(ui.label(paper.name.uppercase(), "bold", Color.WHITE)).left().expandX()
                mast.add(ui.label(leaning(paper.leaning), "small", Color.WHITE)).right()
                card.add(mast).growX().row()
                val body = Table().apply { pad(6f, 8f, 8f, 8f); defaults().left() }
                body.add(Table().apply { setBackground(ui.skin.fill(Theme.tone(page.tone))) }).width(STRIPE).growY().padRight(8f)
                val text = Table().apply { defaults().left() }
                text.add(ui.label(page.headline, "large", wrap = true)).growX().row()
                if (page.subtitle.isNotBlank()) text.add(ui.label(page.subtitle, "muted", wrap = true)).growX().padTop(2f).row()
                body.add(text).growX().minWidth(0f)
                card.add(body).growX().row()
                into.add(card).growX().padBottom(GAP).row()
            }
        }

        into.add(ui.label("Ce qui inquiète les Français", "title")).padTop(GAP).row()
        val concerns = session.media.concerns()
        val worst = concerns.minOfOrNull { it.score }?.let { -it }?.coerceAtLeast(MIN_SCALE) ?: MIN_SCALE
        concerns.forEachIndexed { i, c ->
            val row = Table()
            row.add(ui.label("${i + 1}. ${c.label}", "small")).left().expandX()
            row.add(ui.label(if (c.score < 0) "inquiétude" else "satisfaction", "muted", if (c.score < 0) Theme.bad else Theme.good)).right()
            into.add(row).growX().padTop(2f).row()
            val bar = Table()
            bar.add(Table().apply { setBackground(ui.skin.fill(if (c.score < 0) Theme.bad else Theme.good)) })
                .width(Value.percentWidth((kotlin.math.abs(c.score) / worst).toFloat().coerceIn(MIN_BAR, 1f), bar)).height(BAR).left().expandX()
            into.add(bar).growX().height(BAR).row()
        }

        val polls = session.media.polls()
        into.add(ui.label("Sondages", "title")).padTop(GAP).row()
        if (polls.size >= 2) {
            into.add(LineChart(ui.skin.white, polls.reversed().map { it.second.approval }, true)).growX().height(CHART).padBottom(4f).row()
        }
        polls.take(MAX_POLLS).forEach { (institute, p) ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label(institute.name, "bold")).left().expandX()
            head.add(ui.label(Formats.date(p.time), "muted")).right()
            card.add(head).growX().row()
            val facts = listOfNotNull(
                "Opinions favorables : ${Formatting.wholePercent(p.approval)}",
                p.voteIntention?.let { "intentions de vote : ${Formatting.wholePercent(it)}" },
                p.topConcern.takeIf { it.isNotBlank() }?.let { "1re préoccupation : ${it.lowercase()}" },
            )
            card.add(ui.label(facts.joinToString(" · "), "small", wrap = true)).growX().row()
            into.add(card).growX().padBottom(4f).row()
        }
        if (polls.isEmpty()) into.add(ui.label("Premier sondage dans la semaine.", "muted")).row()
    }

    private fun leaning(l: Double) = when {
        l <= -LEAN -> "gauche"
        l >= LEAN -> "droite"
        l < 0 -> "centre gauche"
        l > 0 -> "centre droit"
        else -> "indépendant"
    }

    private companion object {
        const val STRIPE = 4f
        const val MIN_SCALE = 0.2
        const val MIN_BAR = 0.03f
        const val BAR = 5f
        const val CHART = 60f
        const val MAX_POLLS = 8
        const val LEAN = 0.5
    }
}
