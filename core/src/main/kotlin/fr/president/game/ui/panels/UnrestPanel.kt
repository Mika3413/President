package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Value
import com.badlogic.gdx.utils.Align
import fr.president.engine.politics.Movement
import fr.president.engine.politics.MovementPhase
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.widgets.LineChart

/**
 * La rue et l'armée : les mouvements de contestation en cours (foule, radicalité, phase, villes),
 * les réponses du président jour après jour, et la loyauté de l'armée (complots, coups d'État).
 */
class UnrestPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "⚑ La rue et l'armée"
    private val session get() = nav.session
    private val unrest get() = session.unrest
    private var message: String? = null

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        if (!unrest.available) { into.add(ui.label("Données indisponibles.", "muted")).row(); return }
        val s = unrest.state
        into.add(ui.label("Une réforme contestée, la vie chère, une bavure : la rue peut se lever. Un mouvement grossit avec la colère, se radicalise sous la répression et passe par quatre phases. Une insurrection qui dure peut vous coûter la présidence.", "muted", wrap = true)).growX().padBottom(GAP).row()
        // Échelle des phases, toujours visible.
        val scale = Table()
        MovementPhase.entries.forEach { p ->
            val current = s.movements.any { it.phase == p }
            val cell = Table().apply { setBackground(ui.skin.fill(if (current) phaseColor(p) else Theme.panelAlt)); pad(3f, 4f, 3f, 4f) }
            cell.add(ui.label("${p.icon} ${p.label}", "small", if (current) Theme.text else Theme.textMuted, wrap = true).also { it.setAlignment(Align.center) }).growX()
            scale.add(cell).growX().uniformX().padRight(2f)
        }
        into.add(scale).growX().padBottom(GAP).row()
        if (s.movements.isEmpty()) into.add(ui.label("Aucun mouvement en cours. Le pays est calme.", "bold", Theme.good)).left().padBottom(GAP).row()
        s.movements.forEach { into.add(movement(it)).growX().padBottom(GAP).row() }
        army(into)
        if (s.past.isNotEmpty()) {
            into.add(ui.label("Mouvements passés", "bold")).padTop(GAP).row()
            s.past.takeLast(MAX_PAST).reversed().forEach { into.add(ui.label(it, "small", Theme.textMuted, wrap = true)).growX().row() }
        }
    }

    private fun run(r: Result<String>) { message = r.fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }

    private fun TextButton.wide() = apply { label.setWrap(true); label.setAlignment(Align.left) }

    private fun phaseColor(p: MovementPhase) = when (p) {
        MovementPhase.MARCHES -> Theme.accentDark
        MovementPhase.BLOCKADES -> Theme.warning
        MovementPhase.RIOTS -> Theme.bad
        MovementPhase.INSURRECTION -> Theme.bad
    }

    private fun movement(m: Movement): Table {
        val cause = unrest.cause(m.cause)!!
        val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(8f); defaults().left() }
        val head = Table()
        head.add(ui.label(cause.label, "value", wrap = true)).growX().minWidth(0f)
        head.add(ui.label("${m.phase.icon} ${m.phase.label}", "bold", phaseColor(m.phase))).right().padLeft(4f)
        card.add(head).growX().row()
        card.add(ui.label(cause.slogan, "small", Theme.textMuted, wrap = true)).growX().row()
        val days = m.startedAt.daysUntil(session.context.now).toInt()
        card.add(ui.label("${unrest.people(m.crowd)} manifestants · jour $days · record ${unrest.people(m.peak)}" + if (m.conceded) " · concessions faites" else "", "bold", wrap = true)).growX().padTop(3f).row()
        if (m.history.size > 2) card.add(LineChart(ui.skin.white, m.history, false)).growX().height(CHART).padTop(2f).row()
        gauge(card, "Radicalité", m.radicalization, if (m.radicalization >= 0.5) Theme.bad else if (m.radicalization >= 0.3) Theme.warning else Theme.good)
        gauge(card, "Colère des groupes mobilisés", unrest.anger(cause), Theme.warning)
        card.add(ui.label("Cortèges : " + cities(m.crowd), "small", wrap = true)).growX().padTop(2f).row()
        if (m.phase == MovementPhase.INSURRECTION) card.add(ui.label("⚠ Insurrection depuis ${m.insurrectionDays} jours : au-delà de deux semaines, si vous êtes impopulaire ou si l'armée vacille, vous pouvez tomber.", "small", Theme.bad, wrap = true)).growX().padTop(2f).row()
        card.add(ui.label("Vos réponses", "bold")).padTop(4f).row()
        unrest.responses(m).forEach { r ->
            val color = when (r.id) { "concede" -> Theme.accentDark; "crackdown", "army" -> Theme.bad; else -> null }
            val b = if (color != null) ui.colorButton(r.label, color) { run(unrest.respond(m.id, r.id)) } else ui.button(r.label, "flat") { run(unrest.respond(m.id, r.id)) }
            card.add(b.also { it.isDisabled = r.blocker != null }.wide()).growX().padTop(2f).row()
            card.add(ui.label(r.description + (r.blocker?.let { " ↻ $it" } ?: ""), "small", Theme.textMuted, wrap = true)).growX().row()
        }
        // Mesures d'ordre public (écran Crises) directement accessibles.
        listOf("gathering_ban" to "Interdire les rassemblements", "curfew" to "Couvre-feu").forEach { (id, label) ->
            val def = session.measures.definitions.firstOrNull { it.id == id } ?: return@forEach
            val active = session.state.measures.active.any { it.id == id }
            val blocker = if (active) "En vigueur." else session.measures.blocker(def)
            card.add(ui.button("$label (moins de monde, plus de radicalité)" + (blocker?.let { " — $it" } ?: ""), "flat") { run(session.measures.activate(id)) }
                .also { it.isDisabled = blocker != null }.wide()).growX().padTop(2f).row()
        }
        return card
    }

    private fun gauge(card: Table, label: String, v: Double, color: com.badlogic.gdx.graphics.Color) {
        val row = Table()
        row.add(ui.label(label, "small")).width(Value.percentWidth(0.45f, card)).left()
        val g = Table().apply { setBackground(ui.skin.fill(Theme.panel)) }
        g.add(Table().apply { setBackground(ui.skin.fill(color)) }).width(Value.percentWidth(v.toFloat().coerceIn(0.02f, 1f), g)).height(BAR).left().expandX()
        row.add(g).growX().height(BAR).padLeft(4f)
        row.add(ui.label("${Math.round(v * 100)}", "small", color)).width(28f).right().padLeft(4f)
        card.add(row).growX().padTop(2f).row()
    }

    /** Répartition indicative de la foule entre les grandes villes. */
    private fun cities(crowd: Double): String {
        val shown = CITIES.takeWhile { (_, share) -> crowd * share >= 3 }.ifEmpty { CITIES.take(1) }
        return shown.joinToString(", ") { (city, share) -> "$city ${unrest.people(crowd * share)}" }
    }

    private fun army(into: Table) {
        val s = unrest.state
        into.add(ui.label("L'armée", "bold")).padTop(GAP).row()
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        gauge(box, "Loyauté envers le président", s.armyLoyalty, if (s.armyLoyalty >= 0.6) Theme.good else if (s.armyLoyalty >= 0.45) Theme.warning else Theme.bad)
        box.add(ui.label("Elle baisse quand le budget de la défense est rogné, quand le territoire est envahi, en cas d'insurrection ou d'impopularité profonde. Sous 50 %, des officiers commencent à comploter.", "small", Theme.textMuted, wrap = true)).growX().padTop(2f).row()
        if (s.plotKnown) box.add(ui.label("⚠ La DGSI a identifié un complot (avancement ${Math.round(s.conspiracy * 100)} %).", "bold", Theme.bad, wrap = true)).growX().padTop(2f).row()
        if (s.coups > 0) box.add(ui.label("Tentatives de coup d'État déjouées : ${s.coups}", "small", Theme.warning)).row()
        listOf("pay" to "Revaloriser la solde et les primes (1,5 Md€)", "visit" to "Rendre visite aux troupes", "purge" to "Limoger les officiers comploteurs").forEach { (id, label) ->
            val blocker = unrest.armyBlocker(id)
            box.add(ui.button(label + (blocker?.let { " — $it" } ?: ""), "flat") { run(unrest.armyAction(id)) }.also { it.isDisabled = blocker != null }.wide()).growX().padTop(2f).row()
        }
        into.add(box).growX().row()
    }

    private companion object {
        const val BAR = 6f
        const val CHART = 50f
        const val MAX_PAST = 6
        val CITIES = listOf("Paris" to 0.3, "Marseille" to 0.08, "Lyon" to 0.07, "Toulouse" to 0.06, "Nantes" to 0.05, "Bordeaux" to 0.05, "Lille" to 0.04, "Rennes" to 0.04)
    }
}
