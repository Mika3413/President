package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import fr.president.engine.presidency.MomentRecord
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Moments présidentiels, joués pas à pas : l'allocution que l'on compose phrase par phrase,
 * l'interview du 20 heures, le débat d'entre-deux-tours, les sommets. À la fin : la réaction
 * de la presse et ce que cela change.
 */
class MomentsPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "★ Moments présidentiels"
    private val session get() = nav.session
    private val moments get() = session.moments
    private var result: MomentRecord? = null
    private var message: String? = null

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.warning, wrap = true)).growX().padBottom(GAP).row() }
        val step = moments.view()
        when {
            step != null -> step(into, step)
            result != null -> result(into, result!!)
            else -> offers(into)
        }
    }

    private fun offers(into: Table) {
        into.add(ui.label("Les grands rendez-vous d'un président avec le pays et le monde. Chaque mot compte : un discours cohérent et en phase avec l'actualité porte, une esquive se paie.", "muted", wrap = true)).growX().padBottom(GAP).row()
        moments.offers().forEach { o ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            card.add(ui.label("${o.kind.icon} ${o.title}", "bold", if (o.blocker == null) Theme.accent else Theme.textMuted, wrap = true)).growX().row()
            card.add(ui.label(o.description, "muted", wrap = true)).growX().row()
            if (o.blocker == null) card.add(ui.colorButton("▶ Commencer", Theme.highlightDark) {
                message = moments.start(o.kind, o.ref).exceptionOrNull()?.message
                nav.refresh()
            }).left().padTop(3f).row()
            else card.add(ui.label("↻ ${o.blocker}", "small", Theme.warning, wrap = true)).growX().row()
            into.add(card).growX().padBottom(4f).row()
        }
        val history = session.state.moments.history.asReversed().take(HISTORY)
        if (history.isNotEmpty()) {
            into.add(ui.label("Derniers moments", "bold")).left().padTop(GAP).row()
            history.forEach { h ->
                into.add(ui.label("${h.kind.icon} ${fr.president.game.ui.Formats.date(h.at)} — ${h.headline}", "small", Theme.tone(h.tone), wrap = true)).growX().row()
            }
        }
    }

    private fun step(into: Table, s: fr.president.engine.presidency.MomentService.StepView) {
        val head = Table()
        head.add(ui.label("${s.kind.icon} ${s.title}", "bold", Theme.accent, wrap = true)).growX().left().minWidth(0f)
        head.add(ui.label("${s.step + 1} / ${s.total}", "small", Theme.textMuted)).right()
        into.add(head).growX().row()
        // Barre de progression.
        val bar = Table()
        repeat(s.total) { i -> bar.add(Table().apply { setBackground(ui.skin.fill(if (i <= s.step) Theme.accent else Theme.panelAlt)) }).height(4f).growX().padRight(2f) }
        into.add(bar).growX().padBottom(GAP).row()
        if (s.feedback.isNotEmpty()) into.add(ui.label(s.feedback, "small", Theme.highlight, wrap = true)).growX().padBottom(4f).row()
        into.add(ui.label(s.heading, "title")).left().row()
        into.add(ui.label(s.prompt, "default", wrap = true)).growX().padBottom(GAP).row()
        s.choices.forEach { c ->
            val b = ui.button(c.text, "flat") {
                fr.president.game.ui.Sfx.play(fr.president.game.ui.Sfx.Kind.DECISION)
                moments.choose(c.index).fold({ r -> result = r; message = null }, { message = it.message })
                nav.refresh()
            }
            b.label.setWrap(true); b.label.setAlignment(Align.left)
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 6f, 4f, 6f); defaults().left() }
            card.add(b).growX().row()
            if (c.hint.isNotEmpty()) card.add(ui.label(c.hint, "muted", wrap = true)).growX().row()
            into.add(card).growX().padBottom(4f).row()
        }
        into.add(ui.button("Abandonner", "flat") { moments.cancel(); nav.refresh() }).left().padTop(GAP).row()
    }

    private fun result(into: Table, r: MomentRecord) {
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f, 10f, 8f, 10f); defaults().left() }
        box.add(ui.label("${r.kind.icon} ${r.title}", "bold", Theme.accent, wrap = true)).growX().row()
        box.add(ui.label(r.headline, "title", Theme.tone(r.tone), wrap = true)).growX().padTop(4f).row()
        box.add(ui.label(r.verdict, "default", wrap = true)).growX().padTop(4f).row()
        r.lines.forEach { box.add(ui.label("• $it", "small", wrap = true)).growX().row() }
        into.add(box).growX().padBottom(GAP).row()
        into.add(ui.colorButton("Terminer", Theme.accentDark) { result = null; nav.refresh() }).left().row()
    }

    private companion object {
        const val HISTORY = 8
    }
}
