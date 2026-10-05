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

    private enum class Tab(val label: String) { OVERVIEW("Situation"), TAXES("Impôts"), SPENDING("Dépenses"), SERVICES("Services"), FISCAL("Fiscalité fine"), MONEY("Monnaie et dette"), MARKET("Entreprises") }

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
            Tab.FISCAL -> fiscal(into)
            Tab.MONEY -> money(into)
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
            val stakeRow = Table().apply { defaults().padRight(4f).padTop(2f) }
            stakeRow.add(ui.label("État : ${Math.round(c.stake * 100)} %", "small", if (c.stake > 0) Theme.accent else Theme.textMuted))
            if (c.stake < 1.0) stakeRow.add(ui.button("Nationaliser", "flat") { message = m.nationalize(c.def.id).fold({ it }, { it.message ?: "Impossible." }); nav.refresh() })
            if (c.stake > 0.0) {
                stakeRow.add(ui.button("Céder 10 %", "flat") { message = m.privatize(c.def.id, 0.1).fold({ it }, { it.message ?: "Impossible." }); nav.refresh() })
                stakeRow.add(ui.button("Privatiser", "flat") { message = m.privatize(c.def.id, 1.0).fold({ it }, { it.message ?: "Impossible." }); nav.refresh() })
            }
            card.add(stakeRow).left().row()
            (c.supportBlocker ?: c.summonBlocker)?.let { card.add(ui.label("↻ $it", "small", Theme.warning, wrap = true)).growX().row() }
            into.add(card).growX().padBottom(3f).row()
        }
    }

    /** Dispositifs fiscaux détaillés : chaque changement passe dans une loi de finances. */
    private fun fiscal(into: Table) {
        val f = session.fiscal
        into.add(ui.label("Au-delà des grands taux : fortune, capital, successions, niches, TVA réduites, taxes comportementales. Chaque changement est voté dans une loi de finances.", "muted", wrap = true)).growX().padBottom(GAP).row()
        session.state.policy.proposals.filter { it.kind == fr.president.engine.government.PolicyKind.FISCAL && it.status == PolicyStatus.PENDING_VOTE }.forEach { p ->
            into.add(fr.president.game.ui.widgets.ProposalCard.build(ui, session, p) { message = it; nav.refresh() }).growX().padBottom(4f).row()
        }
        f.categories.forEach { cat ->
            into.add(ui.label("${cat.icon} ${cat.label}", "bold")).padTop(4f).row()
            f.taxes.filter { it.category == cat.id }.forEach { t ->
                val key = "fiscal.${t.id}"
                val open = key in expanded
                val card = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(5f, 8f, 5f, 8f); defaults().left() }
                val head = Table()
                head.add(ui.label(t.label, "bold", wrap = true)).growX().minWidth(0f)
                head.add(ui.label(if (open) "▲" else "▼", "small", Theme.textMuted)).right()
                head.onClickToggle(key)
                card.add(head).growX().row()
                card.add(ui.label("En vigueur : ${t.options[f.current(t.id)].label}", "small", Theme.good, wrap = true)).growX().row()
                if (open) {
                    card.add(ui.label(t.description, "muted", wrap = true)).growX().row()
                    t.options.forEachIndexed { i, o ->
                        if (i == f.current(t.id)) return@forEachIndexed
                        val delta = f.delta(t.id, i)
                        val box = Table().apply { setBackground(ui.skin.fill(Theme.panel)); pad(4f, 6f, 4f, 6f); defaults().left() }
                        box.add(ui.label(o.label, "small", wrap = true)).growX().row()
                        box.add(ui.label((if (delta >= 0) "Recettes +" else "Recettes −") + Formatting.billions(kotlin.math.abs(delta)) + " par an", "small", if (delta >= 0) Theme.good else Theme.warning)).row()
                        val effects = fr.president.engine.session.ActionPresenter(session.context).summarize(fr.president.engine.territory.LocalActionDef(t.id, o.label, "", "", "", immediate = o.effects))
                        if (effects.isNotEmpty()) box.add(fr.president.game.ui.widgets.ActionCards.chips(ui, effects)).growX().row()
                        val blocker = f.blocker(t.id, i)
                        box.add(ui.button("Proposer au Parlement" + (blocker?.let { " — $it" } ?: ""), "flat") {
                            message = f.propose(t.id, i).fold({ "Texte déposé : vote dans quelques semaines." }, { it.message ?: "Impossible." }); nav.refresh()
                        }.also { it.isDisabled = blocker != null }).left().row()
                        card.add(box).growX().padTop(3f).row()
                    }
                }
                into.add(card).growX().padBottom(3f).row()
            }
        }
    }

    private fun Table.onClickToggle(key: String) {
        addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) {
                if (!expanded.remove(key)) expanded += key
                nav.refresh()
            }
        })
    }

    /** BCE, euro, gestion de la dette, obligations vertes. */
    private fun money(into: Table) {
        val m = session.state.monetary
        val e = session.state.playerCountry.economy
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        fun line(label: String, value: String) { val r = Table(); r.add(ui.label(label, "small")).left().expandX(); r.add(ui.label(value, "bold")).right(); box.add(r).growX().row() }
        line("Taux directeur de la BCE", Formatting.percent(m.ecbRate))
        line("Taux de la Fed (États-Unis)", Formatting.percent(m.fedRate))
        line("Euro / dollar", String.format(java.util.Locale.FRENCH, "%.3f", m.eurUsd))
        line("Taux d'emprunt de l'État (10 ans)", Formatting.percent(e.marketRate))
        line("Taux moyen de la dette", Formatting.percent(e.averageDebtRate))
        line("Charge de la dette", Formatting.billions(e.interestBillions) + " par an")
        line("Dividendes de l'État actionnaire", Formatting.billions(fr.president.engine.economy.MonetarySystem.dividends(session.context)) + " par an")
        if (m.ecbHistory.size > 2) box.add(fr.president.game.ui.widgets.LineChart(ui.skin.white, m.ecbHistory, false)).growX().height(CHART).padTop(3f).row()
        box.add(ui.label("La BCE est indépendante : elle fixe ses taux d'après l'inflation et la croissance de la zone euro. Un euro fort pèse sur les exportateurs mais freine l'inflation.", "muted", wrap = true)).growX().padTop(3f).row()
        into.add(box).growX().padBottom(GAP).row()

        val pressure = session.monetary.pressureBlocker()
        val row = Table().apply { defaults().padRight(4f) }
        row.add(ui.button("Appeler la BCE à baisser ses taux", "flat") { message = session.monetary.pressureEcb(true).fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }.also { it.isDisabled = pressure != null })
        row.add(ui.button("… à les relever", "flat") { message = session.monetary.pressureEcb(false).fold({ it }, { it.message ?: "Impossible." }); nav.refresh() }.also { it.isDisabled = pressure != null })
        into.add(row).left().row()
        pressure?.let { into.add(ui.label("↻ $it", "small", Theme.warning)).left().row() }

        into.add(ui.label("Stratégie de la dette (Agence France Trésor)", "bold")).padTop(GAP).row()
        fr.president.engine.economy.DebtStrategy.entries.forEach { s ->
            val r = Table()
            r.add(ui.button(s.label, "toggle") { session.monetary.setStrategy(s); message = "Stratégie de dette : ${s.label.lowercase()}."; nav.refresh() }.also { it.isChecked = m.strategy == s }).width(STRATEGY_WIDTH).left()
            r.add(ui.label(s.description, "small", wrap = true)).growX().padLeft(6f)
            into.add(r).growX().padBottom(2f).row()
        }
        into.add(ui.button("Émettre 10 Md€ d'obligations vertes (${Formatting.billions(m.greenBondsBillions)} déjà émis)", "flat") {
            message = session.monetary.issueGreenBonds().fold({ it }, { it.message ?: "Impossible." }); nav.refresh()
        }).left().padTop(4f).row()
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
        const val STRATEGY_WIDTH = 110f
        const val ICON = 22f
        const val SHOCK = 0.005
    }
}
