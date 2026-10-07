package fr.president.game.ui.panels

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Value
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.hint
import fr.president.game.ui.widgets.ActionCards

/**
 * « Décider » : toutes les décisions que le président peut prendre seul, rangées par rubrique.
 * Chaque rubrique affiche combien de décisions sont possibles maintenant ; chaque décision montre
 * son coût, sa durée et ses effets avant d'être lancée.
 */
class DecisionPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "★ Décider"
    private val session get() = nav.session
    private var category: String? = null
    private var message: String? = null

    override fun applyArgument(argument: String) {
        category = argument
    }

    override fun build(into: Table) {
        val categories = session.nationalActions.categories
        if (categories.isEmpty()) return
        val current = category?.takeIf { id -> categories.any { it.id == id } } ?: categories.first().id
        category = current
        into.add(ui.label("Vos décisions directes, sans passer par le Parlement. Choisissez une rubrique.", "muted", wrap = true)).padBottom(GAP).row()

        val grid = Table().apply { defaults().growX().uniformX().pad(2f) }
        categories.forEachIndexed { i, c ->
            val selected = c.id == current
            val color = colorOf(c.id)
            val tile = Table().apply { setBackground(ui.skin.fill(if (selected) color else Theme.panelAlt)); pad(5f, 6f, 5f, 6f) }
            tile.add(ui.label(c.icon, "value", if (selected) Color.WHITE else color)).padRight(5f)
            tile.add(ui.label(c.label, "bold", if (selected) Color.WHITE else Theme.text)).left().expandX()
            val n = session.nationalActions.availableCount(c.id)
            if (n > 0) tile.add(ui.label(n.toString(), "small", if (selected) Color.WHITE else Theme.good)).right()
            tile.onClick { category = c.id; message = null; nav.refresh() }
            tile.hint(ui, c.description)
            tile.name = "cat.${c.id}"
            grid.add(tile)
            if (i % COLUMNS == COLUMNS - 1) grid.row()
        }
        into.add(grid).growX().padBottom(GAP).row()

        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        agenda(into)
        crisisLink(into)
        situational(into)
        running(into)

        val def = categories.first { it.id == current }
        into.add(ui.label("${def.icon} ${def.label}", "title", colorOf(current))).padTop(2f).row()
        if (def.description.isNotBlank()) into.add(ui.label(def.description, "muted", wrap = true)).padBottom(4f).row()
        // Possibles d'abord, puis en attente, puis verrouillées (la situation ne s'y prête pas).
        session.nationalActions.actions(current).sortedBy { if (it.locked) 2 else if (it.blocker != null) 1 else 0 }.forEach { a ->
            into.add(ActionCards.card(ui, a, colorOf(current), { nav.refresh() }) {
                message = session.nationalActions.perform(a.def.id).fold({ it }, { it.message ?: "Impossible." })
                nav.refresh()
            }).growX().padBottom(4f).row()
        }
    }

    /** Agenda de la semaine : jours de déplacement engagés, voyage en cours, prochains rendez-vous. */
    private fun agenda(into: Table) {
        val a = session.agenda.summary()
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label("◷ Agenda de la semaine", "bold")).left().expandX()
        val full = a.usedDays >= a.capacity - 0.01
        head.add(ui.label("${fmt(a.usedDays)} / ${fmt(a.capacity)} j", "small", if (full) Theme.warning else Theme.good)).right()
        box.add(head).growX().row()
        val gauge = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
        gauge.add(Table().apply { setBackground(ui.skin.fill(if (full) Theme.warning else Theme.accent)) })
            .width(Value.percentWidth((a.usedDays / a.capacity).toFloat().coerceIn(MIN_BAR, 1f), gauge)).height(BAR).left().expandX()
        box.add(gauge).growX().height(BAR).padTop(2f).row()
        val lines = buildList {
            a.current?.let { add("En cours : ${it.label}" + if (it.abroad) " (à l'étranger)" else "") }
            a.upcoming.take(2).forEach { add("À venir : ${it.label}") }
            if (isEmpty()) a.recent.take(2).forEach { add("Fait : ${it.label}") }
        }
        val text = lines.joinToString("\n").ifBlank { "Déplacements, sommets et visites prennent du temps : 5 jours par semaine au plus." }
        box.add(ui.label(text, "small", Theme.textMuted, wrap = true)).growX().padTop(2f).row()
        into.add(box).growX().padBottom(GAP).row()
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.FRENCH, "%.1f", v)

    /** Accès aux mesures de crise (confinement, couvre-feu, ORSEC...) et aux risques du moment. */
    private fun crisisLink(into: Table) {
        val high = session.risks.risks().count { it.probability >= fr.president.engine.readout.RiskReadout.HIGH }
        val active = session.state.measures.active.size
        val text = buildList {
            add(if (high > 0) "$high risque(s) élevé(s)" else "Aucun risque élevé")
            if (active > 0) add("$active mesure(s) en vigueur")
        }.joinToString(" · ")
        val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f) }
        row.add(ui.label("⚠", "value", if (high > 0) Theme.warning else Theme.good)).padRight(6f)
        val col = Table()
        col.add(ui.label("Crises et risques", "bold")).left().row()
        col.add(ui.label("$text. Confinement, couvre-feu, plan ORSEC, prévention des feux...", "small", Theme.textMuted, wrap = true)).left().growX()
        row.add(col).growX().minWidth(0f)
        row.add(ui.label("▶", "bold", Theme.warning)).right().padLeft(6f)
        row.onClick { nav.open(PanelId.CRISIS) }
        row.name = "decide.crisis"
        into.add(row).growX().padBottom(GAP).row()
        momentsLink(into)
    }

    /** Prendre la parole : allocution, interview, débat, sommets. */
    private fun momentsLink(into: Table) {
        val offers = session.moments.offers()
        val ready = offers.count { it.blocker == null }
        val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f) }
        row.add(ui.label("★", "value", if (ready > 0) Theme.highlight else Theme.textMuted)).padRight(6f)
        val col = Table()
        col.add(ui.label("Moments présidentiels", "bold")).left().row()
        col.add(ui.label(offers.joinToString(" · ") { it.title + if (it.blocker == null) "" else " (plus tard)" }, "small", Theme.textMuted, wrap = true)).left().growX()
        row.add(col).growX().minWidth(0f)
        row.add(ui.label("▶", "bold", Theme.highlight)).right().padLeft(6f)
        row.onClick { nav.open(PanelId.MOMENTS) }
        into.add(row).growX().padBottom(GAP).row()
    }

    /** Décisions de crise que la situation vient de débloquer : signalées en tête. */
    private fun situational(into: Table) {
        val list = session.nationalActions.situational()
        if (list.isEmpty()) return
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        box.add(ui.label("⚠ La situation ouvre de nouvelles options", "bold", Theme.warning)).row()
        list.forEach { a ->
            val row = Table()
            row.add(ui.label("${a.def.icon} ${a.def.label}", "small", wrap = true)).left().growX().minWidth(0f)
            row.add(ui.button("Voir", "flat") { category = a.def.category; nav.refresh() }).right()
            box.add(row).growX().row()
        }
        into.add(box).growX().padBottom(GAP).row()
    }

    /** Mesures en cours, avec une barre d'avancement. */
    private fun running(into: Table) {
        val projects = session.nationalActions.running()
        if (projects.isEmpty()) return
        into.add(ui.label("En cours", "bold")).row()
        projects.forEach { p ->
            val progress = p.progress(session.state.time).toFloat()
            val row = Table()
            row.add(ui.label(p.name, "small", wrap = true)).left().growX().minWidth(0f)
            row.add(ui.label("${Math.round(progress * PERCENT)} %", "small", Theme.textMuted)).right().padLeft(6f)
            into.add(row).growX().row()
            val bar = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)) }
            bar.add(Table().apply { setBackground(ui.skin.fill(Theme.accent)) })
                .width(Value.percentWidth(progress.coerceIn(MIN_BAR, 1f), bar)).height(BAR).left().expandX()
            into.add(bar).growX().height(BAR).padBottom(4f).row()
        }
        into.add(ui.separator()).growX().height(1f).padTop(2f).padBottom(GAP).row()
    }

    private companion object {
        const val COLUMNS = 2
        const val PERCENT = 100
        const val BAR = 5f
        const val MIN_BAR = 0.02f

        fun colorOf(category: String): Color = when (category) {
            "economy" -> Theme.catEconomy
            "social" -> Theme.catElections
            "security" -> Theme.catGovernment
            "ecology" -> Theme.catLocal
            "institutions" -> Theme.catInbox
            "communication" -> Theme.catHelp
            "international" -> Theme.catDiplomacy
            "defense" -> Theme.catArmy
            else -> Theme.accent
        }
    }
}
