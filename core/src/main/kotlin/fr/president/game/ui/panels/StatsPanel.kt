package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.engine.readout.StatsReadout
import fr.president.engine.util.Formatting
import fr.president.game.ui.Formats
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.widgets.LineChart
import kotlin.math.abs

/**
 * « Bilan » : la profondeur de la simulation rendue visible. Courbes du mandat avec leurs causes
 * (« Pourquoi ? »), opinion de chaque groupe social, classement des pays, journal du mandat.
 */
class StatsPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "▲ Bilan du mandat"
    private val session get() = nav.session
    private val stats get() = session.stats
    private var tab = Tab.CURVES
    /** Courbe dont les causes sont dépliées. */
    private var whyOpen: String? = null
    private var focus: String? = null
    private var countrySort = CountrySort.GDP
    private var journalKind: String? = null
    private var openGroup: String? = null

    private enum class Tab(val label: String) { CURVES("Courbes"), CONSEQUENCES("Conséquences"), GROUPS("Groupes"), COUNTRIES("Pays"), JOURNAL("Journal"), LEGACY("Héritage") }
    private enum class CountrySort(val label: String) { GDP("Économie"), RELATION("Relation"), ARMY("Armée") }

    /** Argument : une clé de courbe (« approval »...) ou un onglet (« groups », « countries », « journal »). */
    override fun applyArgument(argument: String) {
        when (argument) {
            "consequences" -> tab = Tab.CONSEQUENCES
            "groups" -> tab = Tab.GROUPS
            "countries" -> tab = Tab.COUNTRIES
            "journal" -> tab = Tab.JOURNAL
            "legacy" -> tab = Tab.LEGACY
            else -> { tab = Tab.CURVES; focus = argument; whyOpen = argument }
        }
    }

    override fun build(into: Table) {
        val bar = com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t -> bar.addActor(ui.button(t.label, "toggle") { tab = t; nav.refresh() }.also { it.isChecked = t == tab }) }
        into.add(bar).growX().left().padBottom(GAP).row()
        when (tab) {
            Tab.CURVES -> curves(into)
            Tab.CONSEQUENCES -> consequences(into)
            Tab.GROUPS -> groups(into)
            Tab.COUNTRIES -> countries(into)
            Tab.JOURNAL -> journal(into)
            Tab.LEGACY -> legacy(into)
        }
    }

    /** Conséquences en chaîne : ce qui frappe le pays, pourquoi, ce qui menace, ce qui se répare. */
    private fun consequences(into: Table) {
        val readout = fr.president.engine.readout.ConsequenceReadout(session.context)
        into.add(ui.label("Quand un chiffre dépasse un seuil (service public à bout, RSA trop proche du SMIC, impôts trop lourds, dette, libertés...), le pays réagit chaque mois : opinion, économie, carte, grèves, émeutes. Plus on s'éloigne du seuil, plus c'est grave. Quand la cause disparaît, les dégâts se réparent lentement.", "muted", wrap = true)).growX().padBottom(GAP).row()
        val active = readout.active()
        into.add(ui.label(if (active.isEmpty()) "Aucune conséquence en cours." else "En cours (${active.size})", "bold")).left().padBottom(2f).row()
        active.forEach { row ->
            val r = row.rule
            val color = when { row.severity >= 2 -> Theme.bad; row.severity >= 1 -> Theme.warning; else -> Theme.highlight }
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label("${r.icon} ${r.label}", "bold", wrap = true)).growX().minWidth(0f)
            head.add(ui.label(fr.president.engine.consequences.ConsequenceService.severityLabel(row.severity), "small", color)).right().padLeft(6f)
            card.add(head).growX().row()
            val bar = Table()
            bar.add(Table().apply { setBackground(ui.skin.fill(color)) })
                .width(Value.percentWidth((row.severity / r.maxSeverity).toFloat().coerceIn(MIN_BAR, 1f), bar)).height(BAR).left().expandX()
            card.add(bar).growX().height(BAR).padTop(2f).row()
            card.add(ui.label("${row.value} — ${row.threshold}", "small", color, wrap = true)).growX().padTop(2f).row()
            row.since?.let { card.add(ui.label("Depuis le ${Formatting.date(it)}", "muted")).left().row() }
            card.add(ui.label("Pourquoi : ${r.why}", "small", wrap = true)).growX().padTop(2f).row()
            if (r.fix.isNotEmpty()) card.add(ui.label("Pour en sortir : ${r.fix}", "small", Theme.good, wrap = true)).growX().padTop(2f).row()
            into.add(card).growX().padBottom(GAP).row()
        }
        val risks = readout.risks()
        if (risks.isNotEmpty()) {
            into.add(ui.label("Risques accrus", "bold")).left().padTop(GAP).padBottom(2f).row()
            risks.take(MAX_NEAR).forEach { risk ->
                val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 8f, 4f, 8f); defaults().left() }
                val col = Table().apply { defaults().left() }
                col.add(ui.label(risk.label, "small", wrap = true)).growX().row()
                col.add(ui.label("à cause de : " + risk.causes.joinToString(", ").lowercase(), "muted", wrap = true)).growX().row()
                row.add(col).growX().minWidth(0f)
                row.add(ui.label("×" + String.format(java.util.Locale.FRENCH, "%.1f", risk.factor), "bold", if (risk.factor >= 2) Theme.bad else Theme.warning)).right().padLeft(6f)
                into.add(row).growX().padBottom(2f).row()
            }
        }
        val near = readout.near()
        if (near.isNotEmpty()) {
            into.add(ui.label("Proches du seuil", "bold")).left().padTop(GAP).padBottom(2f).row()
            near.take(MAX_NEAR).forEach { (row, gap) ->
                val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 8f, 4f, 8f); defaults().left() }
                val head = Table()
                head.add(ui.label("${row.rule.icon} ${row.rule.label}", "small", Theme.warning, wrap = true)).growX().minWidth(0f)
                head.add(ui.label(if (gap < 0.15) "imminent" else "proche", "small", Theme.textMuted)).right().padLeft(6f)
                box.add(head).growX().row()
                box.add(ui.label("${row.value} — ${row.threshold}", "muted", wrap = true)).growX().row()
                into.add(box).growX().padBottom(2f).row()
            }
        }
        val healing = readout.healing()
        if (healing.isNotEmpty()) {
            into.add(ui.label("En voie de réparation", "bold")).left().padTop(GAP).padBottom(2f).row()
            healing.forEach { (r, months) ->
                into.add(ui.label("${r.icon} ${r.label} : la cause a disparu, encore environ $months mois pour effacer les dégâts.", "small", Theme.good, wrap = true)).growX().padBottom(2f).row()
            }
        }
    }

    /** Bilan historique provisoire : la note que l'Histoire vous donnerait aujourd'hui. */
    private fun legacy(into: Table) {
        val v = fr.president.engine.readout.LegacyReadout(session.context).verdict()
        val head = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        val top = Table()
        top.add(ui.label(v.title, "title", Theme.highlight)).left().expandX()
        top.add(ui.label("${Math.round(v.grade * 10) / 10.0} / 20", "value", if (v.grade >= 10) Theme.good else Theme.bad)).right()
        head.add(top).growX().row()
        head.add(ui.label(v.summary, "small", wrap = true)).growX().padTop(2f).row()
        head.add(ui.label("Note provisoire : elle évolue avec votre mandat et devient définitive à la fin de la partie.", "muted", wrap = true)).growX().padTop(4f).row()
        into.add(head).growX().padBottom(GAP).row()
        v.lines.forEach { l ->
            val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 8f, 4f, 8f) }
            val col = Table().apply { left() }
            col.add(ui.label(l.label, "bold")).left().row()
            col.add(ui.label(l.detail, "muted", wrap = true)).growX().left()
            row.add(col).growX().minWidth(0f)
            val pts = (if (l.points >= 0) "+" else "−") + String.format(java.util.Locale.FRENCH, "%.1f", kotlin.math.abs(l.points))
            row.add(ui.label(pts, "bold", if (l.points >= 0) Theme.good else Theme.bad)).right().padLeft(6f)
            into.add(row).growX().padBottom(2f).row()
        }
    }

    private fun curves(into: Table) {
        into.add(ui.label("Un point par semaine. ${Theme.goodName.replaceFirstChar { it.uppercase() }} : l'évolution du dernier mois est bonne ; ${Theme.badName} : elle est mauvaise. Touchez « Pourquoi ? » pour voir les causes.", "muted", wrap = true)).padBottom(GAP).row()
        val keys = listOfNotNull(focus).plus(stats.mainKeys.filter { it != focus })
        keys.forEach { key -> stats.series(key)?.let { curveCard(into, it) } }
    }

    private fun curveCard(into: Table, s: StatsReadout.Series) {
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(s.label, "bold")).left().expandX()
        s.current?.let { head.add(ui.label(s.format(it), "value")).right() }
        s.monthChange?.let { c ->
            if (abs(c) > CHANGE_EPSILON) {
                val good = (c > 0) == s.higherIsBetter
                head.add(ui.label(if (c > 0) " ▲" else " ▼", "small", if (good) Theme.good else Theme.bad)).right()
            }
        }
        card.add(head).growX().row()
        card.add(LineChart(ui.skin.white, s.values, s.higherIsBetter)).growX().height(CHART).padTop(3f).row()
        val span = Table()
        span.add(ui.label(if (s.values.size > 1) "il y a ${s.values.size - 1} semaine(s)" else "premier relevé", "muted")).left().expandX()
        span.add(ui.label("aujourd'hui", "muted")).right()
        card.add(span).growX().row()
        stats.why(s.key)?.let { why ->
            val open = whyOpen == s.key
            card.add(ui.button(if (open) "▲ Masquer les causes" else "▼ Pourquoi ?", "flat") { whyOpen = if (open) null else s.key; nav.refresh() }).left().row()
            if (open) causes(card, why)
        }
        into.add(card).growX().padBottom(GAP).row()
    }

    /** Causes en barres : longueur proportionnelle au poids, vert favorable, rouge défavorable. */
    private fun causes(into: Table, why: StatsReadout.Why) {
        into.add(ui.label(why.title, "bold")).padTop(2f).row()
        into.add(ui.label(why.summary, "muted", wrap = true)).growX().row()
        val max = why.causes.maxOfOrNull { abs(it.points) }?.coerceAtLeast(MIN_SCALE) ?: return
        why.causes.forEach { c ->
            val color = if (c.points >= 0) Theme.good else Theme.bad
            val row = Table()
            row.add(ui.label(c.label, "small", wrap = true)).left().growX().minWidth(0f)
            if (c.points == 0.0) { into.add(ui.label(c.label, "muted", wrap = true)).growX().padTop(2f).row(); return@forEach }
            row.add(ui.label((if (c.points >= 0) "+" else "−") + Formats.decimal(abs(c.points)) + " pt", "small", color)).right().padLeft(6f)
            into.add(row).growX().padTop(2f).row()
            val bar = Table()
            bar.add(Table().apply { setBackground(ui.skin.fill(color)) })
                .width(Value.percentWidth((abs(c.points) / max).toFloat().coerceIn(MIN_BAR, 1f), bar)).height(BAR).left().expandX()
            into.add(bar).growX().height(BAR).row()
        }
    }

    private fun groups(into: Table) {
        into.add(ui.label("Opinion de chaque groupe, du plus hostile au plus favorable. Chaque Français appartient à plusieurs groupes (âge, statut, revenus, lieu de vie). Touchez un groupe pour voir sa courbe.", "muted", wrap = true)).padBottom(GAP).row()
        stats.groups().forEach { g ->
            val color = Theme.heat(((g.approval - LOW) / (HIGH - LOW)).toFloat(), Color())
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            val head = Table()
            val name = Table().apply { defaults().left() }
            name.add(ui.label(g.label, "bold")).row()
            name.add(ui.label("${g.partition} · ${Formatting.wholePercent(g.populationShare)} de la population", "muted")).row()
            head.add(name).left().expandX()
            head.add(ui.label(Formatting.wholePercent(g.approval), "value", color)).right()
            g.change?.let { c ->
                if (abs(c) > CHANGE_EPSILON) head.add(ui.label(if (c > 0) " ▲" else " ▼", "small", if (c > 0) Theme.good else Theme.bad)).right()
            }
            card.add(head).growX().row()
            val bar = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
            bar.add(Table().apply { setBackground(ui.skin.fill(color)) })
                .width(Value.percentWidth(g.approval.toFloat().coerceIn(MIN_BAR, 1f), bar)).height(BAR).left().expandX()
            card.add(bar).growX().height(BAR).padTop(2f).row()
            val reasons = listOfNotNull(g.worry?.let { "Inquiet : ${it.lowercase()}" }, g.support?.let { "Satisfait : ${it.lowercase()}" })
            if (reasons.isNotEmpty()) card.add(ui.label(reasons.joinToString(" · "), "small", Theme.textMuted, wrap = true)).growX().padTop(2f).row()
            if (openGroup == g.id) stats.series("group.${g.id}")?.let { s ->
                card.add(LineChart(ui.skin.white, s.values, true)).growX().height(CHART).padTop(3f).row()
            }
            card.onClick { openGroup = if (openGroup == g.id) null else g.id; nav.refresh() }
            into.add(card).growX().padBottom(4f).row()
        }
        into.add(ui.colorButton("★ Décider : mesures sociales", Theme.highlightDark) { nav.open(PanelId.DECISIONS, "social") }).left().padTop(4f).row()
    }

    private fun countries(into: Table) {
        val sort = Table().apply { defaults().padRight(4f) }
        sort.add(ui.label("Trier par", "muted")).padRight(6f)
        CountrySort.entries.forEach { s -> sort.add(ui.button(s.label, "toggle") { countrySort = s; nav.refresh() }.also { it.isChecked = s == countrySort }) }
        into.add(sort).left().padBottom(GAP).row()
        val rows = stats.countries().let { list ->
            when (countrySort) {
                CountrySort.GDP -> list
                CountrySort.RELATION -> list.sortedByDescending { it.relation }
                CountrySort.ARMY -> list.sortedByDescending { it.militaryBudgetBillions }
            }
        }
        rows.forEachIndexed { i, c ->
            val color = Theme.relation(c.relation.toFloat(), Color())
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            val head = Table()
            head.add(ui.label("${i + 1}.", "muted")).width(RANK).left()
            head.add(ui.label(c.name + if (c.atWar) "  ⚔ en guerre" else "", "bold", if (c.atWar) Theme.bad else Theme.text)).left().expandX()
            head.add(ui.label("${Math.round(c.relation * PERCENT)} / 100", "small", color)).right()
            card.add(head).growX().row()
            val facts = listOfNotNull(
                "PIB ${Formatting.billions(c.gdpBillions)}",
                "croissance ${Formatting.signedPercent(c.growth)}",
                "défense ${Formatting.billions(c.militaryBudgetBillions)}/an",
                c.units.takeIf { it > 0 }?.let { "$it unité(s) en Europe" },
                c.alliances.takeIf { it.isNotEmpty() }?.joinToString(", "),
            )
            card.add(ui.label(facts.joinToString(" · "), "muted", wrap = true)).growX().row()
            val bar = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
            bar.add(Table().apply { setBackground(ui.skin.fill(color)) })
                .width(Value.percentWidth(c.relation.toFloat().coerceIn(MIN_BAR, 1f), bar)).height(BAR).left().expandX()
            card.add(bar).growX().height(BAR).padTop(2f).row()
            card.onClick { nav.focusOn(c.id) }
            into.add(card).growX().padBottom(4f).row()
        }
    }

    private fun journal(into: Table) {
        val kinds = stats.journalKinds()
        if (kinds.isNotEmpty()) {
            val chips = com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
            chips.addActor(ui.button("Tout", "toggle") { journalKind = null; nav.refresh() }.also { it.isChecked = journalKind == null })
            kinds.forEach { k -> chips.addActor(ui.button(k, "toggle") { journalKind = k; nav.refresh() }.also { it.isChecked = journalKind == k }) }
            into.add(chips).growX().left().padBottom(GAP).row()
        }
        val entries = stats.journal(journalKind)
        if (entries.isEmpty()) {
            into.add(ui.label("Rien encore : vos décisions, les lois votées, les élections et les crises s'inscriront ici.", "muted", wrap = true)).growX().row()
            return
        }
        entries.take(MAX_JOURNAL).forEach { e ->
            val row = Table()
            row.add(Table().apply { setBackground(ui.skin.fill(Theme.tone(e.tone))) }).width(STRIPE).growY().padRight(6f)
            val text = Table().apply { defaults().left() }
            text.add(ui.label("${Formats.date(e.time)} · ${e.kind}", "muted")).row()
            text.add(ui.label(e.text, "small", wrap = true)).growX().row()
            row.add(text).growX().minWidth(0f)
            into.add(row).growX().padBottom(4f).row()
        }
    }

    private companion object {
        const val CHART = 60f
        const val MAX_NEAR = 8
        const val BAR = 5f
        const val MIN_BAR = 0.02f
        const val MIN_SCALE = 0.5
        const val CHANGE_EPSILON = 0.002
        const val LOW = 0.3
        const val HIGH = 0.65
        const val PERCENT = 100
        const val RANK = 26f
        const val STRIPE = 3f
        const val MAX_JOURNAL = 150
    }
}
