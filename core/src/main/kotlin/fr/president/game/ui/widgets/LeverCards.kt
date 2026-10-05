package fr.president.game.ui.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import fr.president.engine.legislation.Lever
import fr.president.engine.legislation.Preview
import fr.president.engine.session.GameSession
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import kotlin.math.abs

/**
 * Carte d'un levier, la même partout : valeur en vigueur, valeur visée réglable (curseur par
 * crans ou choix), et aperçu de ce que le changement produirait (argent, gagnants et perdants,
 * services, risques). Le mode dit où va le changement : projet de budget, projet de loi, décret.
 */
object LeverCards {
    enum class Mode { BUDGET, LAW, DECREE }

    class Context(
        val ui: Ui,
        val session: GameSession,
        val expanded: MutableSet<String>,
        /** Valeurs visées pour les décrets (pas encore signés). */
        val decreeDrafts: MutableMap<String, Double>,
        val onMessage: (String?) -> Unit,
        val refresh: () -> Unit,
    )

    fun build(c: Context, lever: Lever, mode: Mode): Table {
        val s = c.session
        val ui = c.ui
        val leg = s.legislation
        val current = s.levers.current(lever.id)
        val target = when (mode) {
            Mode.BUDGET -> leg.budgetTarget(lever.id)
            Mode.LAW -> leg.lawChanges().firstOrNull { it.lever == lever.id }?.to ?: current
            Mode.DECREE -> c.decreeDrafts[lever.id] ?: current
        }
        val changed = abs(target - current) > 1e-9
        val pending = leg.pendingFor(lever.id)
        val open = lever.id in c.expanded
        val card = Table().apply { setBackground(ui.skin.fill(if (changed) Theme.panelAlt.cpy().lerp(Theme.accentDark, 0.18f) else Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val head = Table()
        head.add(ui.label(lever.label + if (lever.constitutional) " · Constitution" else "", "bold", wrap = true)).growX().minWidth(0f)
        head.add(ui.label(s.levers.format(lever, current), "bold", if (changed) Theme.textMuted else Theme.text)).right().padLeft(6f)
        head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right().padLeft(6f)
        head.onClick { if (!c.expanded.remove(lever.id)) c.expanded += lever.id; c.refresh() }
        card.add(head).growX().row()
        pending?.let { card.add(ui.label("◷ Au vote : ${it.title.ifEmpty { s.policy.label(it) }}", "small", Theme.accent, wrap = true)).growX().row() }
        if (open && lever.description.isNotEmpty()) card.add(ui.label(lever.description, "muted", wrap = true)).growX().padTop(2f).row()

        val blocker = when (mode) {
            Mode.BUDGET -> leg.budgetBlocker(lever.id)
            Mode.LAW -> if (pending != null) "Déjà soumis au vote." else null
            Mode.DECREE -> null
        }
        if (blocker == null || mode == Mode.BUDGET && pending?.id == leg.openPlf?.id) {
            fun set(v: Double) {
                val value = v.coerceIn(lever.min, lever.max)
                val msg = when (mode) {
                    Mode.BUDGET -> leg.setBudget(lever.id, value, lever.measure)
                    Mode.LAW -> if (abs(value - current) < 1e-9) { leg.removeFromLaw(lever.id); null } else leg.addToLaw(lever.id, value, lever.measure)
                    Mode.DECREE -> { if (abs(value - current) < 1e-9) c.decreeDrafts.remove(lever.id) else c.decreeDrafts[lever.id] = value; null }
                }
                c.onMessage(msg)
                c.refresh()
            }
            if (lever.numeric) {
                val row = Table().apply { defaults().padRight(3f) }
                val big = lever.step * BIG_STEP
                row.add(ui.button("−−", "flat") { set(target - big) }.also { it.isDisabled = target <= lever.min })
                row.add(ui.button("−") { set(target - lever.step) }.also { it.isDisabled = target <= lever.min })
                row.add(ui.label(s.levers.format(lever, target), if (changed) "value" else "bold", if (changed) Theme.highlight else Theme.text)).minWidth(VALUE_WIDTH).center()
                row.add(ui.button("+") { set(target + lever.step) }.also { it.isDisabled = target >= lever.max })
                row.add(ui.button("++", "flat") { set(target + big) }.also { it.isDisabled = target >= lever.max })
                if (changed) row.add(ui.button("↻", "flat") { set(current) })
                card.add(row).left().padTop(3f).row()
                card.add(ui.label("De ${s.levers.format(lever, lever.min)} à ${s.levers.format(lever, lever.max)}", "small", Theme.textMuted)).left().row()
            } else {
                // Un choix par ligne, en pleine largeur : lisible même avec des libellés longs.
                lever.options.forEachIndexed { i, label ->
                    if (lever.irreversible && i < current) return@forEachIndexed
                    val text = (if (i == Math.round(current).toInt()) "● " else "◯ ") + label + if (i == Math.round(current).toInt()) " (en vigueur)" else ""
                    card.add(ui.button(text, "toggle") { set(i.toDouble()) }.also { it.isChecked = Math.round(target).toInt() == i }.wide()).growX().padTop(2f).row()
                }
            }
        } else card.add(ui.label("↻ $blocker", "small", Theme.textMuted, wrap = true)).growX().row()

        if (changed) {
            val p = leg.previewOf(fr.president.engine.legislation.LeverChange(lever.id, current, target, measure = lever.measure))
            preview(c, card, p, open, mode)
            if (mode == Mode.DECREE) {
                val b = leg.decreeBlocker(lever.id, target)
                card.add(ui.colorButton("✎ Signer le décret", Theme.accentDark) {
                    c.onMessage(leg.decree(lever.id, target).fold({ c.decreeDrafts.remove(lever.id); it }, { it.message ?: "Impossible." })); c.refresh()
                }.also { it.isDisabled = b != null }).left().padTop(3f).row()
                b?.let { card.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().row() }
            }
        }
        return card
    }

    /** Aperçu lisible d'un changement (ou d'un ensemble) : argent, gagnants, perdants, services, risques. */
    fun preview(c: Context, into: Table, p: Preview, full: Boolean, mode: Mode? = null) {
        val ui = c.ui
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panel)); pad(4f, 6f, 4f, 6f); defaults().left() }
        money(ui, box, p)
        val chips = groupChips(c, p, if (full) 12 else 5)
        if (chips.children.size > 0) box.add(chips).growX().row()
        val services = p.quality.filter { abs(it.value) > 0.0005 }.entries.sortedByDescending { abs(it.value) }
        if (services.isNotEmpty()) box.add(ui.label("Services : " + services.joinToString(", ") { (q, v) -> "${QUALITY[q] ?: q} ${if (v > 0) "▲" else "▼"}" }, "small",
            if (services.sumOf { it.value } >= 0) Theme.good else Theme.bad, wrap = true)).growX().row()
        val sectors = p.sectors.filter { abs(it.value) > 0.001 }.entries.sortedByDescending { abs(it.value) }.take(3)
        if (sectors.isNotEmpty()) box.add(ui.label("Secteurs : " + sectors.joinToString(", ") { (k, v) ->
            "${c.session.context.playerData.sectors?.sectors?.firstOrNull { it.id == k }?.label?.lowercase() ?: k} ${if (v > 0) "▲" else "▼"}" }, "small", Theme.textMuted, wrap = true)).growX().row()
        (if (full) p.lines else p.lines.take(2)).forEach { box.add(ui.label("· $it", "small", wrap = true)).growX().row() }
        if (p.liberty <= -1) box.add(ui.label("Libertés publiques : ${Math.round(p.liberty)} points", "small", Theme.warning)).left().row()
        p.warnings.take(if (full) 6 else 2).forEach { box.add(ui.label(it, "small", Theme.bad, wrap = true)).growX().padTop(1f).row() }
        if (p.events.isNotEmpty()) box.add(ui.label("Risque de mobilisation : grèves, manifestations ou blocages", "small", Theme.warning, wrap = true)).growX().row()
        if (p.censure > 0.01) box.add(ui.label("⚖ Risque de censure : ${Math.round(p.censure * 100)} % — ${p.censureReason}", "small", if (p.censure > 0.3) Theme.bad else Theme.warning, wrap = true)).growX().row()
        if (mode == Mode.LAW || mode == null) {
            val chance = c.session.legislation.passChance(p.difficulty)
            if (mode == Mode.LAW) box.add(ui.label("Chances au Parlement (seul) : ${Math.round(chance * 100)} %", "small", chanceColor(chance))).left().row()
        }
        into.add(box).growX().padTop(3f).row()
    }

    fun money(ui: Ui, box: Table, p: Preview) {
        val parts = mutableListOf<String>()
        if (abs(p.revenue) >= 0.005) parts += (if (p.revenue > 0) "Recettes +" else "Recettes −") + Formatting.billions(abs(p.revenue))
        if (abs(p.spending) >= 0.005) parts += (if (p.spending > 0) "Dépenses +" else "Dépenses −") + Formatting.billions(abs(p.spending))
        if (parts.isEmpty()) return
        val balance = p.balance
        box.add(ui.label(parts.joinToString(" · ") + " par an → déficit " + (if (balance >= 0) "−" else "+") + Formatting.billions(abs(balance)), "small",
            if (balance >= 0) Theme.good else Theme.warning, wrap = true)).growX().row()
    }

    fun groupChips(c: Context, p: Preview, max: Int): HorizontalGroup {
        val chips = HorizontalGroup().apply { wrap(); left(); rowLeft(); space(3f); wrapSpace(3f) }
        val labels = c.session.context.playerData.socialGroups?.groups?.associate { it.id to it.label }.orEmpty() + ("national" to "Tous les Français")
        p.groups.filter { abs(it.value) >= 0.001 }.entries.sortedByDescending { abs(it.value) }.take(max).forEach { (g, v) ->
            val color = if (v > 0) Theme.good else Theme.bad
            val chip = Table().apply { setBackground(c.ui.skin.fill(color.cpy().mul(1f, 1f, 1f, 0.18f))); pad(1f, 5f, 1f, 5f) }
            chip.add(c.ui.label("${if (v > 0) "▲" else "▼"} ${labels[g] ?: g}", "small", color))
            chips.addActor(chip)
        }
        return chips
    }

    fun chanceColor(chance: Double): Color = when { chance >= 0.7 -> Theme.good; chance >= 0.4 -> Theme.warning; else -> Theme.bad }

    /** Bouton dont le texte passe à la ligne. */
    fun TextButton.wide(): TextButton = apply { label.setWrap(true); label.setAlignment(Align.left) }

    val QUALITY = mapOf("health" to "santé", "education" to "éducation", "security" to "sécurité", "justice" to "justice", "transport" to "transports",
        "environment" to "environnement", "agriculture" to "agriculture", "defense" to "défense", "social" to "social", "pensions" to "retraites",
        "solidarity" to "solidarité", "economy" to "économie")

    private const val BIG_STEP = 5
    private const val VALUE_WIDTH = 96f
}
