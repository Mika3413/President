package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.military.War
import fr.president.engine.military.WarStatus
import fr.president.engine.util.Formatting
import fr.president.game.map.MapSelection
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.onClick
import fr.president.game.ui.widgets.IndicatorView

/** Armées : forces, logistique et production, guerres en cours, renseignement. */
class ArmyPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    override val title = "Armées"
    private val session get() = nav.session
    private var tab = Tab.FORCES
    private var message: String? = null

    private enum class Tab(val label: String) { FORCES("Forces"), LOGISTICS("Logistique"), WARS("Conflits"), INTEL("Renseignement") }

    override fun build(into: Table) {
        val tabs = Table().apply { defaults().padRight(4f) }
        Tab.entries.forEach { t -> tabs.add(ui.button(t.label, "toggle") { tab = t; nav.refresh() }.also { it.isChecked = t == tab }) }
        into.add(tabs).left().padBottom(GAP).row()
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).growX().padBottom(GAP).row() }
        when (tab) {
            Tab.FORCES -> forces(into)
            Tab.LOGISTICS -> logistics(into)
            Tab.WARS -> wars(into)
            Tab.INTEL -> intel(into)
        }
    }

    private fun forces(into: Table) {
        into.add(IndicatorView(ui, session.national.military(), expanded)).growX().padBottom(GAP).row()
        val units = session.military.ownUnits().sortedBy { it.type }
        units.groupBy { session.db.unitType(it.type).domain }.forEach { (domain, list) ->
            into.add(ui.label(DOMAIN_LABELS[domain.name] ?: domain.name, "bold")).padTop(4f).row()
            list.forEach { u ->
                val row = Table().apply { defaults().left(); pad(4f); setBackground(ui.skin.fill(Theme.panelAlt)) }
                row.add(ui.label(u.name, "small", if (u.inCombat) Theme.bad else null, wrap = true)).growX().row()
                row.add(ui.label(session.militaryReadouts.unitSubtitle(u) + " · disponibilité ${Formatting.percent(u.readiness)}", "muted", wrap = true)).growX().row()
                row.onClick { nav.select(MapSelection.Unit(u.id)); nav.focusOn(u.id) }
                into.add(row).growX().padBottom(3f).row()
            }
        }
    }

    private fun logistics(into: Table) {
        session.militaryReadouts.logistics().forEach { into.add(IndicatorView(ui, it, expanded)).growX().padBottom(6f).row() }
        val p = session.db.militaryParameters
        val stocks = session.state.military.stocks
        val buy = Table().apply { defaults().padRight(4f).padBottom(4f) }
        buy.add(ui.button("Acheter des munitions (${Formatting.billions(p.purchaseCostBillions)})") { session.military.production.purchase(true); message = "Commande passée : livraison sous ${p.purchaseDays} jours."; nav.refresh() })
        buy.add(ui.button("Acheter du carburant") { session.military.production.purchase(false); message = "Commande passée : livraison sous ${p.purchaseDays} jours."; nav.refresh() })
        into.add(buy).left().row()
        val economy = Table().apply { defaults().padRight(4f).padBottom(4f) }
        economy.add(ui.button(if (stocks.warEconomy) "Quitter l'économie de guerre" else "Passer en économie de guerre", "toggle") {
            session.military.production.setWarEconomy(!stocks.warEconomy); nav.refresh()
        }.also { it.isChecked = stocks.warEconomy })
        if (stocks.reservists <= 0) {
            economy.add(ui.button("Décréter la mobilisation (${Formatting.billions(p.mobilizationCostBillions)})") {
                message = session.military.production.mobilize().fold({ "Mobilisation décrétée." }, { it.message }); nav.refresh()
            })
        } else economy.add(ui.button("Démobiliser les réserves") { session.military.production.demobilize(); message = "Réservistes démobilisés."; nav.refresh() })
        into.add(economy).left().row()
        into.add(ui.label("Commander des unités", "bold")).padTop(GAP).row()
        session.military.production.buildable().forEach { t ->
            val row = Table()
            row.add(ui.label("${t.label} — ${Formatting.billions(t.costBillions)}, ${t.buildDays} jours", "small", wrap = true)).growX().left()
            row.add(ui.button("Commander", "flat") {
                message = session.military.production.order(t.id).fold({ "${t.label} commandée." }, { it.message }); nav.refresh()
            }).right()
            into.add(row).growX().row()
        }
        val orders = session.state.military.production
        if (orders.isNotEmpty()) {
            into.add(ui.label("En production", "bold")).padTop(GAP).row()
            orders.forEach { o -> into.add(ui.label("${session.db.unitType(o.unitType).label} — livraison le ${fr.president.game.ui.Formats.date(o.readyAt)}", "small")).row() }
        }
    }

    private fun wars(into: Table) {
        val wars = session.state.military.wars.sortedBy { it.status }
        if (wars.isEmpty()) {
            into.add(ui.label("Aucun conflit en cours dans le monde.", "muted")).row()
            return
        }
        val player = session.state.player.countryId
        for (w in wars.filter { it.status != WarStatus.ENDED } + wars.filter { it.status == WarStatus.ENDED }.takeLast(MAX_ENDED)) {
            into.add(IndicatorView(ui, session.militaryReadouts.war(w), expanded)).growX().padBottom(4f).row()
            if (w.status == WarStatus.ENDED) continue
            val side = w.sideOf(player)
            val row = Table().apply { defaults().padRight(4f).padBottom(4f) }
            if (side != War.NONE) {
                val enemy = (if (side == War.ATTACKER) w.defenders else w.attackers).first()
                row.add(ui.button("Proposer un cessez-le-feu") { nav.prepareProposal(enemy, "CEASEFIRE", mapOf("days" to 90.0)) })
                row.add(ui.button("Proposer la paix") { nav.prepareProposal(enemy, "PEACE_TREATY", mapOf("keepOccupied" to 0.0)) })
                into.add(row).left().row()
                specialOps(into, enemy)
                continue
            } else {
                row.add(ui.button("Aider ${session.db.country(w.defenders.first()).definition.name}", "flat") {
                    nav.prepareProposal(w.defenders.first(), "MILITARY_AID", mapOf("amountBillions" to 2.0))
                })
                row.add(ui.button("Entrer en guerre à ses côtés", "flat") {
                    session.diplomacy.joinWar(w.id, defenderSide = true); message = "La France entre en guerre."; nav.refresh()
                })
            }
            into.add(row).left().row()
        }
    }

    /** Frappes et cyberattaque contre un ennemi : effets et limites affichés avant d'agir. */
    private fun specialOps(into: Table, enemy: String) {
        val ops = session.military.operations
        val box = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)); pad(6f, 8f, 6f, 8f); defaults().left() }
        box.add(ui.label("Opérations spéciales", "bold", Theme.catArmy)).row()
        val strike = ops.strikeBlocker(enemy)
        box.add(ui.label("✹ Frappes de missiles : affaiblissent la plus forte concentration ennemie à portée (0,2 Md€, munitions, risque de victimes civiles).", "small", wrap = true)).growX().row()
        if (strike == null) box.add(ui.colorButton("✹ Frapper", Theme.catArmy) { message = ops.strike(enemy).fold({ it }, { it.message }); nav.refresh() }).left().padBottom(4f).row()
        else box.add(ui.label("↻ $strike", "small", Theme.warning, wrap = true)).growX().padBottom(4f).row()
        val cyber = ops.cyberBlocker(enemy)
        box.add(ui.label("⚡ Cyberattaque : perturbe leur économie et leurs armées (0,05 Md€).", "small", wrap = true)).growX().row()
        if (cyber == null) box.add(ui.colorButton("⚡ Lancer la cyberattaque", Theme.catArmy) { message = ops.cyber(enemy).fold({ it }, { it.message }); nav.refresh() }).left().row()
        else box.add(ui.label("↻ $cyber", "small", Theme.warning, wrap = true)).growX().row()
        box.add(ui.label("Parachutage et débarquement : sélectionnez une brigade sur la carte.", "muted", wrap = true)).growX().padTop(2f).row()
        into.add(box).growX().padBottom(GAP).row()
    }

    private fun intel(into: Table) {
        val player = session.state.player.countryId
        val geo = session.military.geo
        into.add(ui.label("Estimations de nos services (qualité du renseignement : ${Formatting.percent(session.military.intelligence.quality(player))}).", "muted", wrap = true)).growX().row()
        val interesting = session.state.countries.keys.filter { it != player }
            .sortedByDescending { (if (geo.atWar(player, it)) 2 else 0) + if (geo.isAtWar(it)) 1 else 0 }
        for (c in interesting) {
            val (units, power) = session.military.intelligence.estimatedForces(player, c)
            if (units == 0) continue
            val name = session.db.country(c).definition.name
            val status = if (geo.atWar(player, c)) " — ENNEMI" else if (geo.isAtWar(c)) " — en guerre" else ""
            into.add(ui.label("$name$status", "bold", if (geo.atWar(player, c)) Theme.bad else null)).row()
            into.add(ui.label("Environ $units unités, puissance terrestre estimée ${power.toInt()}${if (geo.isNuclear(c)) " · puissance nucléaire" else ""}", "small", wrap = true)).growX().padBottom(4f).row()
        }
    }

    private companion object {
        const val MAX_ENDED = 5
        val DOMAIN_LABELS = mapOf("LAND" to "Armée de terre", "AIR" to "Armée de l'air et de l'espace", "SEA" to "Marine nationale", "STRATEGIC" to "Forces stratégiques")
    }
}
