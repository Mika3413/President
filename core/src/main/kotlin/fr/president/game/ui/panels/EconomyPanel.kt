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

    private enum class Tab(val label: String) { OVERVIEW("Situation"), TAXES("Impôts"), SPENDING("Dépenses"), SERVICES("Services"), MARKET("Entreprises") }

    override fun build(into: Table) {
        // Les onglets passent à la ligne plutôt que d'élargir le panneau.
        val tabs = com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup().apply { wrap(); left(); rowLeft(); space(4f); wrapSpace(4f) }
        Tab.entries.forEach { t ->
            tabs.addActor(ui.button(t.label, "toggle") { tab = t; nav.refresh() }.also { it.isChecked = t == tab })
        }
        into.add(tabs).growX().left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        when (tab) {
            Tab.OVERVIEW -> session.national.all().forEach { into.add(IndicatorView(ui, it, expanded)).padBottom(GAP).row() }
            Tab.TAXES -> taxes(into)
            Tab.SPENDING -> spending(into)
            Tab.SERVICES -> session.national.services().forEach { into.add(IndicatorView(ui, it, expanded)).padBottom(GAP).row() }
            Tab.MARKET -> market(into)
        }
        pending(into)
    }

    /** Bourse, secteurs et grandes entreprises, avec les leviers du président. */
    private fun market(into: Table) {
        val m = session.market
        if (!m.available) return
        val head = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        val top = Table()
        top.add(ui.label(m.indexName, "bold")).left().expandX()
        top.add(ui.label(Math.round(m.index).toString(), "value")).right()
        head.add(top).growX().row()
        m.monthChange?.let { c ->
            head.add(ui.label("Sur 30 jours : ${Formatting.signedPercent(c)}", "small", if (c >= 0) Theme.good else Theme.bad)).row()
        }
        if (m.history.size > 2) head.add(fr.president.game.ui.widgets.LineChart(ui.skin.white, m.history, true)).growX().height(CHART).padTop(3f).row()
        into.add(head).growX().padBottom(GAP).row()

        into.add(ui.label("Secteurs", "bold")).padTop(2f).row()
        into.add(ui.label("Activité par rapport au début du mandat. Elle suit le moral, l'énergie, les taux, la croissance mondiale, la sécurité et vos mesures de crise.", "muted", wrap = true)).growX().padBottom(4f).row()
        m.sectors().forEach { s ->
            val row = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(4f, 8f, 4f, 8f) }
            row.add(ui.label(s.def.icon, "bold", Theme.accent)).width(ICON).left()
            val col = Table().apply { left() }
            col.add(ui.label(s.def.label, "default")).left().row()
            col.add(ui.label("${Formatting.wholePercent(s.def.gdpShare)} du PIB · ${Formatting.integer(s.jobs)} emplois", "muted")).left()
            row.add(col).left().growX().minWidth(0f)
            val change = s.activity - 1
            val trend = if (s.shock > SHOCK) " ▲" else if (s.shock < -SHOCK) " ▼" else ""
            row.add(ui.label(Formatting.signedPercent(change) + trend, "bold", if (change >= 0) Theme.good else Theme.bad)).right()
            into.add(row).growX().padBottom(2f).row()
        }

        into.add(ui.label("Grandes entreprises", "bold")).padTop(GAP).row()
        m.companies().forEach { c ->
            val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
            val line = Table()
            line.add(ui.label(c.def.name, "bold")).left().growX().minWidth(0f)
            line.add(ui.label(Formatting.signedPercent(c.change), "bold", if (c.change >= 0) Theme.good else Theme.bad)).right()
            card.add(line).growX().row()
            card.add(ui.label("${c.sector} · ${Formatting.billions(c.capBillions)} en Bourse · ${Formatting.integer(c.employees.toLong())} salariés en France", "muted", wrap = true)).growX().row()
            val buttons = Table().apply { defaults().padRight(4f).padTop(3f) }
            buttons.add(ui.button("Soutenir (${Formatting.billions(c.supportCost)})", "flat") {
                message = m.support(c.def.id).fold({ it }, { it.message ?: "Impossible." }); nav.refresh()
            }.also { it.isDisabled = c.supportBlocker != null })
            buttons.add(ui.button("Convoquer le PDG", "flat") {
                message = m.summon(c.def.id).fold({ it }, { it.message ?: "Impossible." }); nav.refresh()
            }.also { it.isDisabled = c.summonBlocker != null })
            card.add(buttons).left().row()
            (c.supportBlocker ?: c.summonBlocker)?.let { card.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().row() }
            into.add(card).growX().padBottom(3f).row()
        }
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
        into.add(ui.label("Les budgets suivent l'inflation. Pour améliorer un service public, augmentez ses moyens réels ; pour réduire le déficit, il faut des économies réelles.", "muted", wrap = true)).padBottom(GAP).row()
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
            if (p.status == PolicyStatus.PENDING_VOTE) {
                into.add(fr.president.game.ui.widgets.ProposalCard.build(ui, session, p) { message = it; nav.refresh() }).growX().padBottom(4f).row()
                continue
            }
            val status = when (p.status) {
                PolicyStatus.PENDING_VOTE -> "Vote le ${p.voteAt.toDateTime().toLocalDate()}"
                PolicyStatus.ADOPTED -> "Adoptée"
                PolicyStatus.REJECTED -> "Rejetée"
                PolicyStatus.FORCED -> "Adoptée sans vote"
                PolicyStatus.PENDING_CENSURE -> "Responsabilité engagée : motion de censure en cours"
            }
            val color = when (p.status) {
                PolicyStatus.REJECTED -> Theme.bad
                PolicyStatus.ADOPTED, PolicyStatus.FORCED -> Theme.good
                else -> Theme.textMuted
            }
            into.add(ui.label(session.policy.label(p), "small", wrap = true)).row()
            into.add(ui.label(status, "muted", color)).row()
            if (p.status == PolicyStatus.REJECTED && p.supportAtVote != null) {
                into.add(ui.button("Passer en force (coût politique, risque de censure)", "flat") { session.policy.forcePass(p.id); nav.refresh() }).left().row()
            }
        }
    }

    private companion object {
        const val STEP = 0.05
        const val MIN_FACTOR = 0.5
        const val MAX_FACTOR = 1.6
        const val EPS = 1e-6
        const val MAX_PENDING_SHOWN = 6
        const val CHART = 70f
        const val ICON = 22f
        const val SHOCK = 0.005
    }
}
