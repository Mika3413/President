package fr.president.game.ui.hud

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import fr.president.engine.elections.ElectionResult
import fr.president.engine.session.GameSession
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.blockInput

/**
 * La soirée électorale en direct : compte à rebours jusqu'à 20 heures, résultats du premier tour
 * qui montent, puis la carte qui s'allume département par département au fil du dépouillement,
 * le compteur national qui converge, et enfin le verdict.
 */
class ElectionNight(private val ui: Ui, private val session: GameSession, private val colorMap: (((String) -> Color?)?) -> Unit) {
    val root: Table = Table().apply { setFillParent(true); top(); isVisible = false; blockInput() }
    private var time = 0f
    private var result: ElectionResult? = null
    private var onDone: () -> Unit = {}
    private var phase = -1
    private val card = Table()
    private val clock: Label = ui.label("", "headline", Theme.highlight)
    private val status: Label = ui.label("", "small", Theme.textMuted)
    private val tally = Table()
    private var order: List<String> = emptyList()
    private val revealColor = Color()

    fun show(r: ElectionResult, onDone: () -> Unit) {
        result = r
        this.onDone = onDone
        time = 0f
        phase = -1
        val shares = r.incumbentShareByDepartment
        order = shares.keys.shuffled(java.util.Random(r.time.seconds))
        root.clearChildren()
        card.clearChildren()
        card.setBackground(ui.skin.fill(Color(Theme.panel.r, Theme.panel.g, Theme.panel.b, 0.93f)))
        card.pad(10f).defaults().left().growX()
        root.add(card).growX().pad(6f).padTop(46f)
        root.isVisible = true
        nextPhase()
    }

    fun update(delta: Float) {
        if (!root.isVisible) return
        time += delta
        val r = result ?: return
        when (phase) {
            0 -> {
                // Compte à rebours.
                val left = (COUNTDOWN - time).coerceAtLeast(0f)
                clock.setText(if (left > 0) "19 h 59 : ${left.toInt() + 1}…" else "20 h 00")
                if (time >= COUNTDOWN) nextPhase()
            }
            1 -> if (time >= FIRST_ROUND_SECONDS) nextPhase()
            2 -> {
                // Dépouillement : les départements s'allument, le compteur converge.
                val progress = (time / COUNT_SECONDS).coerceIn(0f, 1f)
                val shown = (order.size * progress).toInt()
                val revealed = order.take(shown).toHashSet()
                colorMap { code -> if (code in revealed) r.incumbentShareByDepartment[code]?.let { heat(it) } else DIM }
                val final = r.secondRound?.shares?.get(session.state.player.presidentId) ?: 0.0
                val noise = (1 - progress) * NOISE * Math.sin(time * 7.0)
                updateTally(final + noise, progress)
                if (progress >= 1f && time >= COUNT_SECONDS + 0.8f) nextPhase()
            }
        }
    }

    /** Vert là où vous gagnez, rouge là où vous perdez : l'écart est accentué pour se lire d'un coup d'œil. */
    private fun heat(share: Double): Color = Theme.heat(((share - 0.5) * CONTRAST + 0.5).toFloat(), revealColor).cpy()

    private fun nextPhase() {
        phase++
        time = 0f
        val r = result ?: return
        card.clearChildren()
        val title = Table()
        title.add(ui.label("● SOIRÉE ÉLECTORALE", "bold", Theme.bad)).left().expandX()
        title.add(ui.label(fr.president.game.ui.Formats.date(r.time), "small", Theme.textMuted)).right()
        card.add(title).row()
        when (phase) {
            0 -> {
                card.add(clock).padTop(6f).row()
                card.add(ui.label("Les bureaux de vote ferment. Les premières estimations tombent à 20 heures.", "default", wrap = true)).row()
                fr.president.game.ui.Sfx.play(fr.president.game.ui.Sfx.Kind.MARCH)
            }
            1 -> {
                card.add(ui.label("Premier tour", "title")).padTop(4f).row()
                card.add(Bars(r.firstRound.shares)).height(BAR_ROWS * BAR_H).padTop(4f).row()
                fr.president.game.ui.Sfx.play(fr.president.game.ui.Sfx.Kind.DECISION)
                if (r.secondRound == null) phase = 2
            }
            2 -> {
                card.add(ui.label("Second tour : dépouillement en direct", "title")).padTop(4f).row()
                card.add(tally).padTop(4f).row()
                card.add(status).row()
            }
            else -> verdict(r)
        }
    }

    private fun updateTally(incumbent: Double, progress: Float) {
        val r = result ?: return
        val shares = r.secondRound?.shares ?: return
        val you = session.state.player.presidentId
        val other = shares.keys.firstOrNull { it != you } ?: return
        tally.clearChildren()
        fun row(id: String, v: Double) {
            val c = session.state.characters[id]
            val line = Table()
            line.add(ui.label((if (id == you) "★ " else "") + (c?.fullName ?: id), if (id == you) "bold" else "default")).left().expandX()
            line.add(ui.label(Formatting.percent(v), "value", if (v >= 0.5) Theme.good else Theme.bad)).right()
            tally.add(line).growX().row()
        }
        row(you, incumbent)
        row(other, 1 - incumbent)
        status.setText("Bulletins dépouillés : ${(progress * 100).toInt()} %")
    }

    private fun verdict(r: ElectionResult) {
        val you = session.state.characters.getValue(session.state.player.presidentId)
        val share = r.secondRound?.shares?.get(you.id) ?: r.firstRound.shares[you.id] ?: 0.0
        val winner = session.state.characters[r.winnerId]
        val e = if (you.female) "e" else ""
        val (title, text, color) = when {
            r.incumbentWon -> Triple("Vous êtes réélu$e !", "Avec ${Formatting.percent(share)} des voix, le pays vous renouvelle sa confiance. La foule envahit les rues.", Theme.good)
            r.secondRound == null -> Triple("Éliminé$e dès le premier tour", "Avec ${Formatting.percent(share)} des voix, vous ne passez pas le premier tour. ${winner?.fullName ?: ""} l'emporte.", Theme.bad)
            else -> Triple("Battu$e par ${winner?.fullName ?: "votre adversaire"}", "Avec ${Formatting.percent(share)} des voix, votre présidence s'achève ce soir.", Theme.bad)
        }
        card.add(ui.label(title, "headline", color, wrap = true)).padTop(6f).row()
        card.add(ui.label(text, "default", wrap = true)).row()
        card.add(ui.colorButton("Continuer", Theme.accentDark) { root.isVisible = false; colorMap(null); onDone() }).left().padTop(8f).row()
        fr.president.game.ui.Sfx.play(if (r.incumbentWon) fr.president.game.ui.Sfx.Kind.FANFARE else fr.president.game.ui.Sfx.Kind.DEFEAT)
    }

    /** Barres du premier tour qui montent jusqu'au score de chacun. */
    private inner class Bars(shares: Map<String, Double>) : Widget() {
        private val rows = shares.entries.sortedByDescending { it.value }.take(BAR_ROWS)
        private val labels = rows.map { (id, _) -> ui.label(session.state.characters[id]?.fullName ?: id, "small") }
        private val c = Color()
        private var t = 0f

        override fun act(delta: Float) { super.act(delta); t += delta }
        override fun getPrefHeight() = BAR_ROWS * BAR_H

        override fun draw(batch: Batch, parentAlpha: Float) {
            val grow = (t / GROW_SECONDS).coerceIn(0f, 1f)
            val max = rows.maxOf { it.value }.coerceAtLeast(0.01)
            rows.forEachIndexed { i, (id, v) ->
                val yy = y + height - (i + 1) * BAR_H
                val you = id == session.state.player.presidentId
                batch.color = c.set(Theme.panelAlt)
                batch.draw(ui.skin.white, x + NAME_W, yy + 3f, width - NAME_W, BAR_H - 6f)
                batch.color = c.set(if (you) Theme.highlight else Theme.accent)
                batch.draw(ui.skin.white, x + NAME_W, yy + 3f, ((width - NAME_W - 60f) * (v / max) * grow).toFloat(), BAR_H - 6f)
                batch.color = Color.WHITE
                labels[i].setPosition(x, yy + 2f); labels[i].setSize(NAME_W - 4f, BAR_H - 4f); labels[i].draw(batch, parentAlpha)
                ui.skin.font("small").draw(batch, Formatting.percent(v * grow), x + width - 56f, yy + BAR_H - 6f)
            }
        }
    }

    private companion object {
        const val COUNTDOWN = 3f
        const val FIRST_ROUND_SECONDS = 4.5f
        const val COUNT_SECONDS = 8f
        const val NOISE = 0.03
        const val CONTRAST = 6.0
        const val BAR_ROWS = 5
        const val BAR_H = 26f
        const val NAME_W = 150f
        const val GROW_SECONDS = 2f
        val DIM = Color(0.25f, 0.27f, 0.3f, 1f)
    }
}
