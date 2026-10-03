package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.government.PolicyStatus
import fr.president.engine.util.Formatting
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.widgets.IndicatorView

/** Situation nationale, fiscalité et budget : le joueur oriente, le Parlement vote. */
class EconomyPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Économie et budget"
    private val session get() = nav.session
    private var tab = Tab.OVERVIEW
    private val draftRates = mutableMapOf<String, Double>()
    private val draftFactors = mutableMapOf<String, Double>()
    private var message: String? = null

    private enum class Tab(val label: String) { OVERVIEW("Situation"), TAXES("Impôts"), SPENDING("Dépenses"), SERVICES("Services") }

    override fun build(into: Table) {
        val tabs = Table().apply { defaults().padRight(4f) }
        Tab.entries.forEach { t ->
            tabs.add(ui.button(t.label, "toggle") { tab = t; nav.refresh() }.also { it.isChecked = t == tab })
        }
        into.add(tabs).left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        when (tab) {
            Tab.OVERVIEW -> session.national.all().forEach { into.add(IndicatorView(ui, it, expanded)).padBottom(GAP).row() }
            Tab.TAXES -> taxes(into)
            Tab.SPENDING -> spending(into)
            Tab.SERVICES -> session.national.services().forEach { into.add(IndicatorView(ui, it, expanded)).padBottom(GAP).row() }
        }
        pending(into)
    }

    private fun taxes(into: Table) {
        into.add(ui.label("Les changements sont soumis au Parlement (vote sous 14 jours). Leurs effets sur l'activité et l'opinion sont progressifs.", "muted", wrap = true)).padBottom(GAP).row()
        val budget = session.state.playerCountry.economy.budget!!
        val defs = session.db.country(session.state.player.countryId).economy.budget!!.revenues.filter { it.adjustable }
        for (def in defs) {
            val item = budget.revenues.getValue(def.id)
            val draft = draftRates[def.id] ?: session.policy.pendingValue(def.id) ?: item.rate
            val box = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(Theme.panelAlt)) }
            box.add(ui.label(def.label, "bold")).left().expandX()
            box.add(ui.label(Formatting.billions(item.amount) + "/an", "small")).right().row()
            box.add(ui.label(def.description, "muted", wrap = true)).colspan(2).growX().row()
            val row = Table().apply { defaults().padRight(4f) }
            row.add(ui.button("−") { draftRates[def.id] = (draft - def.step).coerceAtLeast(def.minRate); nav.refresh() })
            row.add(ui.label("${Formatting.amount(draft)} ${def.rateLabel}", if (draft != item.rate) "bold" else "default"))
            row.add(ui.button("+") { draftRates[def.id] = (draft + def.step).coerceAtMost(def.maxRate); nav.refresh() })
            if (draft != item.rate && session.policy.pendingValue(def.id) != draft) {
                row.add(ui.button("Proposer", "accent") {
                    session.policy.proposeTaxRate(def.id, draft)
                    draftRates.remove(def.id)
                    message = "Mesure déposée au Parlement : ${def.label}."
                    nav.refresh()
                })
            }
            box.add(row).colspan(2).left().padTop(4f).row()
            into.add(box).growX().padBottom(GAP).row()
        }
    }

    private fun spending(into: Table) {
        into.add(ui.label("Les budgets non indexés perdent du pouvoir d'achat avec l'inflation : la qualité des services s'en ressent.", "muted", wrap = true)).padBottom(GAP).row()
        val economy = session.state.playerCountry.economy
        val defs = session.db.country(session.state.player.countryId).economy.budget!!.spending
        for (def in defs) {
            val item = economy.budget!!.spending.getValue(def.id)
            val draft = draftFactors[def.id] ?: session.policy.pendingValue(def.id) ?: item.policyFactor
            val box = Table().apply { defaults().left(); pad(6f); setBackground(ui.skin.fill(Theme.panelAlt)) }
            box.add(ui.label(def.label, "bold")).left().expandX()
            box.add(ui.label(Formatting.billions(item.amount) + "/an", "small")).right().row()
            box.add(ui.label(def.description, "muted", wrap = true)).colspan(2).growX().row()
            if (def.adjustable) {
                val row = Table().apply { defaults().padRight(4f) }
                row.add(ui.button("−5 %") { draftFactors[def.id] = (draft - STEP).coerceAtLeast(MIN_FACTOR); nav.refresh() })
                row.add(ui.label("Budget : ${Formatting.signedPercent(draft - 1.0)}", if (draft != item.policyFactor) "bold" else "default"))
                row.add(ui.button("+5 %") { draftFactors[def.id] = (draft + STEP).coerceAtMost(MAX_FACTOR); nav.refresh() })
                if (kotlin.math.abs(draft - item.policyFactor) > EPS && session.policy.pendingValue(def.id) != draft) {
                    row.add(ui.button("Proposer", "accent") {
                        session.policy.proposeSpending(def.id, draft)
                        draftFactors.remove(def.id)
                        message = "Mesure déposée au Parlement : ${def.label}."
                        nav.refresh()
                    })
                }
                box.add(row).colspan(2).left().padTop(4f).row()
            }
            into.add(box).growX().padBottom(GAP).row()
        }
    }

    private fun pending(into: Table) {
        val proposals = session.state.policy.proposals.takeLast(MAX_PENDING_SHOWN).reversed()
        if (proposals.isEmpty()) return
        into.add(ui.label("Mesures récentes", "bold")).padTop(GAP).row()
        for (p in proposals) {
            val status = when (p.status) {
                PolicyStatus.PENDING_VOTE -> "Vote le ${p.voteAt.toDateTime().toLocalDate()}"
                PolicyStatus.ADOPTED -> "Adoptée"
                PolicyStatus.REJECTED -> "Rejetée"
                PolicyStatus.FORCED -> "Adoptée sans vote"
            }
            val color = when (p.status) {
                PolicyStatus.REJECTED -> Theme.bad
                PolicyStatus.ADOPTED, PolicyStatus.FORCED -> Theme.good
                else -> Theme.textMuted
            }
            into.add(ui.label(session.policy.label(p), "small", wrap = true)).row()
            into.add(ui.label(status, "muted", color)).row()
            if (p.status == PolicyStatus.REJECTED && p.supportAtVote != null) {
                into.add(ui.button("Passer en force (coût politique)", "flat") { session.policy.forcePass(p.id); nav.refresh() }).left().row()
            }
        }
    }

    private companion object {
        const val STEP = 0.05
        const val MIN_FACTOR = 0.5
        const val MAX_FACTOR = 1.6
        const val EPS = 1e-6
        const val MAX_PENDING_SHOWN = 6
    }
}
