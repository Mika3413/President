package fr.president.game.ui.widgets

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.engine.government.PolicyProposal
import fr.president.engine.session.GameSession
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.hint

/**
 * Texte en attente de vote : chances d'adoption en jauge, et amendements à négocier
 * (chacun achète des voix contre une contrepartie clairement affichée).
 */
object ProposalCard {
    fun build(ui: Ui, session: GameSession, p: PolicyProposal, onChange: (String) -> Unit): Table {
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        card.add(ui.label(session.policy.label(p), "bold", wrap = true)).growX().row()
        val referendum = p.status == fr.president.engine.government.PolicyStatus.PENDING_REFERENDUM
        card.add(ui.label((if (referendum) "Référendum le " else "Vote le ") + Formats.date(p.voteAt), "muted")).row()
        // Les articles d'un texte (loi de finances, projet de loi).
        p.changes.take(MAX_CHANGES).forEach { c ->
            val l = session.levers.lever(c.lever) ?: c.measure?.let { session.levers.measureLever(it) } ?: return@forEach
            card.add(ui.label("• ${l.label} : ${session.levers.format(l, c.from)} → ${session.levers.format(l, c.to)}", "small", wrap = true)).growX().row()
        }
        if (p.changes.size > MAX_CHANGES) card.add(ui.label("… et ${p.changes.size - MAX_CHANGES} autre(s)", "small", Theme.textMuted)).left().row()
        if (p.kind == fr.president.engine.government.PolicyKind.BUDGET_BILL && p.changes.isEmpty()) card.add(ui.label("Budget de reconduction : rien ne change.", "small", Theme.textMuted)).left().row()
        if (referendum) {
            val yes = session.legislation.referendumEstimate(session.legislation.preview(p.changes))
            card.add(ui.label("Sondage : ${Math.round(yes * 100)} % de oui", "small", if (yes > 0.5) Theme.good else Theme.bad)).left().row()
            return card
        }
        val chance = session.amendments.chance(p.id)
        val color = when {
            chance >= LIKELY -> Theme.good
            chance >= UNSURE -> Theme.warning
            else -> Theme.bad
        }
        val head = Table()
        head.add(ui.label("Chances d'adoption", "small")).left().expandX()
        head.add(ui.label("${Math.round(chance * PERCENT)} %", "value", color)).right()
        card.add(head).growX().padTop(2f).row()
        val bar = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
        bar.add(Table().apply { setBackground(ui.skin.fill(color)) })
            .width(Value.percentWidth(chance.toFloat().coerceIn(MIN_BAR, 1f), bar)).height(BAR).left().expandX()
        card.add(bar).growX().height(BAR).row()
        if (p.amendments.isNotEmpty()) {
            val names = p.amendments.mapNotNull { id -> session.amendments.catalog.firstOrNull { it.id == id }?.label }
            card.add(ui.label("Négocié : " + names.joinToString(", "), "small", Theme.textMuted, wrap = true)).growX().padTop(2f).row()
        }
        val options = session.amendments.options(p.id).filter { it.available }
        if (options.isNotEmpty()) {
            card.add(ui.label("Négocier des voix", "small", Theme.highlight)).padTop(4f).row()
            options.forEach { o ->
                val a = o.amendment
                val row = Table()
                val text = Table().apply { defaults().left(); left() }
                text.add(ui.label("${a.label}  +${Math.round(a.supportGain * PERCENT)} pts", "small")).row()
                text.add(ui.label(a.costText, "muted")).row()
                row.add(text).left().growX().minWidth(0f)
                row.add(ui.button("Négocier", "default") { onChange(session.amendments.amend(p.id, a.id).fold({ it }, { it.message ?: "Impossible." })) }).right()
                row.hint(ui, a.description)
                card.add(row).growX().padTop(2f).row()
            }
        }
        return card
    }

    private const val PERCENT = 100
    private const val MAX_CHANGES = 6
    private const val LIKELY = 0.7
    private const val UNSURE = 0.4
    private const val MIN_BAR = 0.02f
    private const val BAR = 6f
}
