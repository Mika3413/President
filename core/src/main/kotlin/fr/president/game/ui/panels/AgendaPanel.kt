package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.engine.session.AgendaEntry
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui

/**
 * Agenda du président : les jours engagés cette semaine, le déplacement en cours, ce qui est
 * prévu et ce qui vient d'être fait, et le temps que prend chaque type d'activité.
 */
class AgendaPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "◷ Agenda du président"
    private val session get() = nav.session

    override fun build(into: Table) {
        val agenda = session.agenda
        val a = agenda.summary()
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        val head = Table()
        head.add(ui.label("Cette semaine", "bold")).left().expandX()
        val full = a.usedDays >= a.capacity - 0.01
        head.add(ui.label("${fmt(a.usedDays)} / ${fmt(a.capacity)} jours", "value", if (full) Theme.warning else Theme.good)).right()
        box.add(head).growX().row()
        val gauge = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
        gauge.add(Table().apply { setBackground(ui.skin.fill(if (full) Theme.warning else Theme.accent)) })
            .width(Value.percentWidth((a.usedDays / a.capacity).toFloat().coerceIn(0.02f, 1f), gauge)).height(BAR).left().expandX()
        box.add(gauge).growX().height(BAR).padTop(4f).row()
        box.add(ui.label(if (full) "Agenda complet : les nouveaux déplacements attendront qu'un créneau se libère."
            else "Il vous reste ${fmt(a.capacity - a.usedDays)} jour(s) pour des déplacements, visites et entretiens.", "small", wrap = true)).growX().padTop(4f).row()
        agenda.abroad()?.let { box.add(ui.label("✈ En déplacement à l'étranger : ${it.label}. Pas de visite en France d'ici votre retour, le ${Formats.dateTime(it.end)}.", "small", Theme.warning, wrap = true)).growX().padTop(4f).row() }
        into.add(box).growX().padBottom(GAP).row()

        section(into, "En cours", listOfNotNull(a.current))
        section(into, "À venir", a.upcoming)
        section(into, "Cette semaine", a.recent)

        into.add(ui.label("Ce qui prend du temps", "bold")).padTop(GAP).row()
        val rules = agenda.rules
        val labels = session.nationalActions.definitions.associate { it.id to it.label }
        val lines = rules.actions.entries.sortedByDescending { it.value.days }.map { (id, c) ->
            "${labels[id] ?: id} : ${fmt(c.days)} j" + if (c.abroad) " (à l'étranger)" else ""
        } + listOf(
            "Sommets internationaux (UE, G7, OTAN, ONU, COP) : 2 j à l'étranger, automatiquement",
            "Visite présidentielle chez un élu : ${fmt(rules.localVisitDays)} j",
            "Entretien ou appel : ${fmt(rules.localTalkDays)} j",
            "Se rendre sur place lors d'une crise : 1 j",
        )
        lines.forEach { into.add(ui.label("• $it", "small", Theme.textMuted, wrap = true)).growX().row() }
    }

    private fun section(into: Table, title: String, entries: List<AgendaEntry>) {
        if (entries.isEmpty()) return
        into.add(ui.label(title, "bold")).padTop(4f).row()
        entries.forEach { e ->
            val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 8f, 4f, 8f) }
            row.add(ui.label((if (e.abroad) "✈ " else "◷ ") + e.label, "small", wrap = true)).growX().minWidth(0f)
            row.add(ui.label("${Formats.date(e.start)} · ${fmt(e.days)} j", "muted")).right().padLeft(6f)
            into.add(row).growX().padBottom(2f).row()
        }
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toInt().toString()
        else String.format(java.util.Locale.FRENCH, "%.2f", v).trimEnd('0').trimEnd(',')

    private companion object {
        const val BAR = 6f
    }
}
