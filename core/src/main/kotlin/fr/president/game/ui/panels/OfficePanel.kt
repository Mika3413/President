package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.readout.AdvisorReadout
import fr.president.engine.readout.OfficeReadout
import fr.president.engine.readout.Tone
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick

/**
 * Le bureau du président : ce que l'administration fait remonter. La note du jour, les rapports
 * des ministres (chiffres, seuils d'alerte, avis), les remontées des préfets, les notes des
 * services et les prévisions de Bercy « si rien ne change ».
 */
class OfficePanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "✪ Bureau du président"
    private val session get() = nav.session
    private var tab = Tab.NOTE
    private var openReport: String? = null
    private var forecast: OfficeReadout.Forecast? = null
    private var forecastAt: String? = null

    private enum class Tab(val label: String) { NOTE("Note du jour"), MINISTERS("Ministres"), PREFECTS("Préfets"), SERVICES("Services"), FORECAST("Prévisions") }

    override fun applyArgument(argument: String) {
        Tab.entries.firstOrNull { it.name.equals(argument, ignoreCase = true) }?.let { tab = it }
    }

    override fun build(into: Table) {
        val office = OfficeReadout(session.context)
        val tabs = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t -> tabs.addActor(ui.button(t.label, "toggle") { tab = t; nav.refresh() }.also { it.isChecked = t == tab }) }
        into.add(tabs).growX().left().padBottom(GAP).row()
        when (tab) {
            Tab.NOTE -> note(into, office)
            Tab.MINISTERS -> ministers(into, office)
            Tab.PREFECTS -> prefects(into, office)
            Tab.SERVICES -> services(into, office)
            Tab.FORECAST -> forecast(into, office)
        }
    }

    private fun color(t: Tone): Color = when (t) { Tone.GOOD -> Theme.good; Tone.WARNING -> Theme.warning; Tone.BAD -> Theme.bad; Tone.NEUTRAL -> Theme.text }

    private fun note(into: Table, office: OfficeReadout) {
        into.add(ui.label("Note du secrétaire général de l'Élysée, ${Formatting.date(session.context.now)}. Les points qui comptent aujourd'hui, du plus urgent au moins urgent.", "muted", wrap = true)).growX().padBottom(GAP).row()
        office.briefing().forEachIndexed { i, item -> into.add(itemCard(item, i + 1)).growX().padBottom(GAP).row() }
    }

    private fun itemCard(item: OfficeReadout.Item, rank: Int? = null): Table {
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label((rank?.let { "$it. " } ?: "") + "${item.icon} ${item.title}", "bold", color(item.tone), wrap = true)).growX().minWidth(0f)
        card.add(head).growX().row()
        card.add(ui.label(item.text, "small", wrap = true)).growX().padTop(2f).row()
        item.target?.let { target -> card.add(ui.button("Voir →", "flat") { go(target, item.targetId) }).left().row() }
        return card
    }

    private fun go(target: AdvisorReadout.Target, id: String?) {
        when (target) {
            AdvisorReadout.Target.CONSEQUENCES -> nav.open(PanelId.STATS, "consequences")
            AdvisorReadout.Target.DEPARTMENT -> id?.let { nav.focusOn(it) }
            AdvisorReadout.Target.GOVERNMENT -> nav.open(PanelId.LEGISLATION, "parliament")
            AdvisorReadout.Target.ELECTIONS -> nav.open(PanelId.ELECTIONS)
            AdvisorReadout.Target.ECONOMY -> nav.open(PanelId.ECONOMY)
            else -> nav.open(PanelId.DECISIONS)
        }
    }

    private fun ministers(into: Table, office: OfficeReadout) {
        into.add(ui.label("Chaque ministre fait remonter l'état de son domaine : ses chiffres, le seuil où il faudrait s'alarmer, et son avis. Touchez un ministère pour le détail.", "muted", wrap = true)).growX().padBottom(GAP).row()
        office.reports().forEach { r ->
            val open = openReport == r.ministryId
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            val head = Table()
            val name = Table().apply { defaults().left() }
            name.add(ui.label(r.title, "bold", wrap = true)).growX().row()
            name.add(ui.label(r.minister, "muted")).row()
            head.add(name).growX().minWidth(0f)
            head.add(ui.label(when (r.tone) { Tone.BAD -> "● alerte"; Tone.WARNING -> "● vigilance"; else -> "● calme" }, "small", color(r.tone))).right().padLeft(6f)
            head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
            head.onClick { openReport = if (open) null else r.ministryId; nav.refresh() }
            card.add(head).growX().row()
            card.add(ui.label(r.advice, "small", if (r.tone == Tone.BAD) Theme.bad else Theme.text, wrap = true)).growX().padTop(2f).row()
            if (open) r.indicators.forEach { ind ->
                val row = Table()
                row.add(ui.label(ind.label, "small", wrap = true)).growX().minWidth(0f).left()
                row.add(ui.label(ind.value, "small", color(ind.tone), wrap = true)).right().padLeft(6f).width(170f)
                card.add(row).growX().padTop(2f).row()
                if (ind.note.isNotEmpty()) card.add(ui.label(ind.note, "muted", wrap = true)).growX().row()
            }
            into.add(card).growX().padBottom(GAP).row()
        }
    }

    private fun prefects(into: Table, office: OfficeReadout) {
        into.add(ui.label("Les préfets signalent les départements où la situation se tend : délinquance, accès aux soins, chômage, colère. Touchez-en un pour le voir sur la carte.", "muted", wrap = true)).growX().padBottom(GAP).row()
        val spots = office.hotspots(10)
        if (spots.isEmpty()) into.add(ui.label("Aucune remontée inquiétante des préfectures.", "bold", Theme.good)).left().row()
        spots.forEach { h ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            card.add(ui.label("⚑ Préfecture ${h.name}", "bold", if (h.score > 1.2) Theme.bad else Theme.warning, wrap = true)).growX().row()
            card.add(ui.label(h.reasons.joinToString(" ; ").replaceFirstChar { it.uppercase() }, "small", wrap = true)).growX().row()
            card.onClick { nav.focusOn(h.code) }
            into.add(card).growX().padBottom(GAP).row()
        }
    }

    private fun services(into: Table, office: OfficeReadout) {
        into.add(ui.label("Notes du renseignement intérieur (DGSI) et de Bercy : ce qui menace sans encore faire la une.", "muted", wrap = true)).growX().padBottom(GAP).row()
        office.services().forEach { into.add(itemCard(it)).growX().padBottom(GAP).row() }
    }

    private fun forecast(into: Table, office: OfficeReadout) {
        into.add(ui.label("Bercy simule l'avenir si vous ne changez rien : budget, économie, opinion, et les crises qui en découleraient. Les imprévus (catastrophes, guerres, scandales) peuvent tout changer.", "muted", wrap = true)).growX().padBottom(GAP).row()
        val row = Table().apply { defaults().padRight(4f) }
        row.add(ui.button("Prévision à 6 mois") { forecast = office.forecast(6); forecastAt = Formatting.date(session.context.now); nav.refresh() })
        row.add(ui.button("Prévision à 1 an") { forecast = office.forecast(12); forecastAt = Formatting.date(session.context.now); nav.refresh() })
        into.add(row).left().padBottom(GAP).row()
        val f = forecast ?: return
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        card.add(ui.label("Dans ${if (f.months >= 12) "un an" else "${f.months} mois"}, si rien ne change (note du $forecastAt)", "bold", wrap = true)).growX().row()
        f.lines.forEach { l ->
            val r = Table()
            r.add(ui.label(l.label, "small")).growX().left().minWidth(0f)
            r.add(ui.label(l.value, "small", color(l.tone))).right().padLeft(6f)
            card.add(r).growX().padTop(2f).row()
        }
        if (f.newConsequences.isNotEmpty()) {
            card.add(ui.label("Crises qui apparaîtraient :", "bold", Theme.bad)).left().padTop(4f).row()
            f.newConsequences.forEach { card.add(ui.label(it, "small", Theme.bad, wrap = true)).growX().row() }
        } else card.add(ui.label("Aucune nouvelle crise en vue.", "small", Theme.good)).left().padTop(4f).row()
        card.add(ui.label(f.reliability, "muted", wrap = true)).growX().padTop(4f).row()
        into.add(card).growX().row()
    }
}
