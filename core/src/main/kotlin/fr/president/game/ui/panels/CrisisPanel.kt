package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.engine.readout.RiskReadout
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.hint
import fr.president.game.ui.onClick
import fr.president.game.ui.widgets.MeasureCards

/**
 * « Crises et risques » : ce qui menace le pays dans les 30 jours (feux, crues, épidémie,
 * attentat...), les mesures en vigueur, et toute la boîte à outils du président pour prévenir
 * ou affronter une crise : confinement, couvre-feu, plan ORSEC, Vigipirate, délestages...
 */
class CrisisPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "⚠ Crises et risques"
    private val session get() = nav.session
    private enum class Tab(val label: String) { RISKS("⚠ Risques"), ACTIVE("● En vigueur"), TOOLBOX("☰ Boîte à outils") }
    private var tab = Tab.RISKS
    private var family: String? = null
    /** Département visé par les mesures locales de la boîte à outils. */
    private var department: String? = null
    private var message: String? = null
    private val cards = MeasureCards(ui, nav.session, { nav.refresh() }) { result -> message = result; nav.refresh() }

    override fun applyArgument(argument: String) {
        message = null
        when {
            argument == "active" -> tab = Tab.ACTIVE
            argument.startsWith("dept:") -> { tab = Tab.TOOLBOX; department = argument.removePrefix("dept:") }
            else -> { tab = Tab.RISKS; expanded += "risk.$argument" }
        }
    }

    override fun build(into: Table) {
        val bar = Table().apply { defaults().padRight(4f) }
        val activeCount = session.state.measures.active.size
        Tab.values().forEach { t ->
            val label = if (t == Tab.ACTIVE && activeCount > 0) "${t.label} ($activeCount)" else t.label
            bar.add(ui.button(label, "toggle") { tab = t; message = null; nav.refresh() }.also { it.isChecked = t == tab; it.name = "crisis.${t.name.lowercase()}" })
        }
        into.add(bar).left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        when (tab) {
            Tab.RISKS -> risks(into)
            Tab.ACTIVE -> active(into)
            Tab.TOOLBOX -> toolbox(into)
        }
    }

    private fun risks(into: Table) {
        into.add(ui.label("Probabilité qu'au moins un drame de ce type survienne dans les 30 jours, d'après la saison, la météo, l'état du pays et vos mesures. Touchez un risque pour voir quoi faire.", "muted", wrap = true)).padBottom(GAP).row()
        session.risks.risks().forEach { r -> into.add(riskCard(r)).growX().padBottom(4f).row() }
    }

    private fun riskCard(r: RiskReadout.Risk): Table {
        val key = "risk.${r.def.id}"
        val open = key in expanded
        val color = Theme.tone(r.tone)
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(r.def.icon, "value", color)).width(ICON_WIDTH).left()
        head.add(ui.label(r.def.label, "bold", wrap = true)).left().growX().minWidth(0f)
        val right = Table()
        right.add(ui.label(r.level, "small", color)).right().row()
        right.add(ui.label("${Math.round(r.probability * PERCENT)} % / 30 j", "muted")).right()
        head.add(right).right().padLeft(6f)
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
        head.onClick { if (!expanded.remove(key)) expanded += key; nav.refresh() }
        head.hint(ui, r.def.description)
        card.add(head).growX().row()
        val gauge = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
        gauge.add(Table().apply { setBackground(ui.skin.fill(color)) })
            .width(Value.percentWidth(r.probability.toFloat().coerceIn(MIN_BAR, 1f), gauge)).height(BAR).left().expandX()
        card.add(gauge).growX().height(BAR).padTop(3f).row()
        val facts = buildList {
            r.hotspot?.let { add("Surtout : $it") }
            if (r.activeMeasures.isNotEmpty()) add("Protégé par : " + r.activeMeasures.joinToString(", ") { it.label.lowercase() })
            else if (r.probability >= RiskReadout.HIGH) add("Aucune prévention en place")
        }
        if (facts.isNotEmpty()) card.add(ui.label(facts.joinToString(" · "), "small", if (r.activeMeasures.isEmpty() && r.probability >= RiskReadout.HIGH) Theme.warning else Theme.textMuted, wrap = true)).growX().padTop(2f).row()
        if (open) {
            card.add(ui.label(r.def.description, "muted", wrap = true)).growX().padTop(2f).row()
            card.add(ui.label("Que faire ?", "bold", Theme.highlight)).padTop(4f).row()
            r.measures.forEach { m ->
                val dept = if (m.local) r.hotspotDepartment else null
                card.add(cards.card(session.measures.view(m, dept), dept, compact = true)).growX().padTop(3f).row()
            }
        }
        return card
    }

    private fun active(into: Table) {
        val list = session.state.measures.active.toList()
        if (list.isEmpty()) {
            into.add(ui.label("Aucune mesure en vigueur. Ouvrez la boîte à outils pour prévenir une crise ou y faire face.", "muted", wrap = true)).row()
            into.add(ui.colorButton("☰ Boîte à outils", Theme.warning) { tab = Tab.TOOLBOX; nav.refresh() }).left().padTop(GAP).row()
            return
        }
        val monthly = list.sumOf { m -> session.measures.definitions.firstOrNull { it.id == m.id }?.costPerMonthBillions ?: 0.0 }
        if (monthly > 0) into.add(ui.label("Coût total : ${fr.president.engine.util.Formatting.billions(monthly)} par mois", "small", Theme.warning)).padBottom(GAP).row()
        list.forEach { m ->
            val def = session.measures.definitions.firstOrNull { it.id == m.id } ?: return@forEach
            into.add(cards.card(session.measures.view(def, m.department), m.department)).growX().padBottom(4f).row()
        }
    }

    private fun toolbox(into: Table) {
        val families = session.measures.families
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        chips.addActor(ui.button("Tout", "toggle") { family = null; nav.refresh() }.also { it.isChecked = family == null })
        families.forEach { f ->
            chips.addActor(ui.button("${f.icon} ${f.label}", "toggle") { family = f.id; nav.refresh() }.also { it.isChecked = family == f.id; it.hint(ui, f.description) })
        }
        into.add(chips).growX().padBottom(GAP).row()
        departmentPicker(into)
        val views = session.measures.views(family, department)
        // Possibles d'abord, puis en vigueur, puis indisponibles.
        views.sortedBy { if (it.active != null) 1 else if (it.blocker == null) 0 else if (it.locked) 3 else 2 }.forEach { v ->
            into.add(cards.card(v, department)).growX().padBottom(4f).row()
        }
    }

    /** Mesures locales : le département visé, choisi parmi les plus exposés ou sur la carte. */
    private fun departmentPicker(into: Table) {
        val territory = session.context.playerData.territory ?: return
        val name = { code: String -> territory.departments.firstOrNull { it.code == code }?.name ?: code }
        val hot = session.risks.risks().mapNotNull { it.hotspotDepartment }.distinct().take(HOTSPOTS)
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 6f, 4f, 6f); defaults().left() }
        box.add(ui.label("Mesures locales : " + (department?.let { name(it) } ?: "aucun département choisi"), "small", if (department == null) Theme.warning else Theme.text, wrap = true)).growX().row()
        val row = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(3f) }
        (listOfNotNull(department) + hot).distinct().forEach { code ->
            row.addActor(ui.button(name(code), "toggle") { department = code; nav.refresh() }.also { it.isChecked = code == department })
        }
        row.addActor(ui.button("Autre : touchez la carte", "flat") { message = "Touchez un département, puis l'onglet « ⚠ Crise » de sa fiche."; nav.refresh() })
        box.add(row).growX().padTop(2f).row()
        into.add(box).growX().padBottom(GAP).row()
    }

    private companion object {
        const val ICON_WIDTH = 26f
        const val PERCENT = 100
        const val BAR = 5f
        const val MIN_BAR = 0.02f
        const val HOTSPOTS = 4
    }
}
