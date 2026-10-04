package fr.president.game.ui.panels

import com.badlogic.gdx.scenes.scene2d.ui.Table
import fr.president.engine.util.Formatting
import fr.president.game.map.MapSelection
import fr.president.game.ui.Theme
import fr.president.game.ui.Ui
import fr.president.game.ui.widgets.SheetView

/** Panneau contextuel ouvert depuis la carte : territoire, ville, équipement, base, pays. */
class SelectionPanel(ui: Ui, private val nav: Navigator, onClose: () -> Unit) : Panel(ui, onClose) {
    var selection: MapSelection? = null
        set(value) {
            if (value != field) { tab = Tab.SUMMARY; message = null }
            field = value
        }
    private var message: String? = null
    private var tab = Tab.SUMMARY

    /** Onglets des fiches de territoire : l'essentiel d'abord, l'action ensuite, le détail sur demande. */
    private enum class Tab(val label: String) { SUMMARY("Résumé"), ACT("▶ Agir"), DETAILS("Détails") }
    private val session get() = nav.session

    override val title: String
        get() = when (val s = selection) {
            is MapSelection.Department -> session.local.department(s.code).title
            is MapSelection.Region -> session.local.region(s.code).title
            is MapSelection.City -> session.local.city(s.id).title
            is MapSelection.Infrastructure -> session.local.infrastructure(s.id).title
            is MapSelection.Base -> session.local.base(s.id).title
            is MapSelection.Country -> session.db.countries[s.id]?.definition?.name ?: "Pays non simulé"
            is MapSelection.Unit -> session.state.military.units[s.id]?.name ?: "Unité"
            null -> ""
        }

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        when (val s = selection) {
            is MapSelection.Department -> buildDepartment(into, s.code)
            is MapSelection.Region -> {
                tabs(into, listOf(Tab.SUMMARY, Tab.DETAILS))
                into.add(SheetView(ui, session.local.region(s.code), expanded, compact = tab == Tab.SUMMARY)).row()
                electedTalk(into, session.state.territory.regions[s.code]?.presidentId)
            }
            is MapSelection.City -> {
                tabs(into, listOf(Tab.SUMMARY, Tab.DETAILS))
                into.add(SheetView(ui, session.local.city(s.id), expanded, compact = tab == Tab.SUMMARY)).row()
                electedTalk(into, session.state.territory.cities[s.id]?.mayorId)
                session.state.territory.cities[s.id]?.department?.let { code ->
                    into.add(ui.colorButton("▶ Agir dans le département", Theme.catLocal) { nav.select(MapSelection.Department(code)); tab = Tab.ACT; nav.refresh() }).left().padTop(GAP).row()
                }
            }
            is MapSelection.Infrastructure -> buildInfrastructure(into, s.id)
            is MapSelection.Base -> into.add(SheetView(ui, session.local.base(s.id), expanded)).row()
            is MapSelection.Country -> buildCountry(into, s.id)
            is MapSelection.Unit -> unitSheet.build(into, s.id)
            null -> Unit
        }
    }

    private fun buildDepartment(into: Table, code: String) {
        val available = localActions.availableCount(code)
        tabs(into, listOf(Tab.SUMMARY, Tab.ACT, Tab.DETAILS), mapOf(Tab.ACT to available))
        when (tab) {
            Tab.SUMMARY -> {
                into.add(SheetView(ui, session.local.department(code), expanded, compact = true)).row()
                if (available > 0) {
                    into.add(ui.colorButton("▶ Agir ici : $available action(s) possible(s)", Theme.catLocal) { tab = Tab.ACT; nav.refresh() }).growX().padTop(4f).row()
                }
                electedTalk(into, session.state.territory.departments[code]?.presidentId)
                val region = session.state.territory.departments.getValue(code).region
                into.add(ui.button("Voir la région", "flat") { nav.select(MapSelection.Region(region)) }).left().row()
            }
            Tab.ACT -> {
                into.add(ui.label("Lancez des chantiers et des plans locaux. Vert : ce qui s'améliore ; rouge : ce qui se dégrade.", "muted", wrap = true)).padBottom(4f).row()
                localActions.build(into, code)
            }
            Tab.DETAILS -> into.add(SheetView(ui, session.local.department(code), expanded)).row()
        }
    }

    /** Barre d'onglets ; un nombre en pastille signale ce qui est possible dans l'onglet. */
    private fun tabs(into: Table, list: List<Tab>, badges: Map<Tab, Int> = emptyMap()) {
        if (tab !in list) tab = list.first()
        val bar = Table().apply { defaults().padRight(4f) }
        list.forEach { t ->
            val n = badges[t] ?: 0
            val label = if (n > 0) "${t.label} ($n)" else t.label
            bar.add(ui.button(label, "toggle") { tab = t; nav.refresh() }.also { it.isChecked = t == tab; if (t == Tab.ACT) it.name = "tab.act" })
        }
        into.add(bar).left().padBottom(GAP).row()
    }

    private val localActions = fr.president.game.ui.widgets.LocalActionList(ui, nav.session, { nav.refresh() }) { result ->
        message = result
        nav.refresh()
    }

    private val talks = fr.president.game.ui.widgets.ConversationControls(ui, nav)

    private fun electedTalk(into: Table, characterId: String?) {
        val c = characterId?.let { session.state.characters[it] } ?: return
        talks.build(into, c.id, c.fullName)
    }

    private fun buildInfrastructure(into: Table, id: String) {
        into.add(SheetView(ui, session.local.infrastructure(id), expanded)).row()
        val infra = session.state.infrastructure.getValue(id)
        if (infra.closed) return
        into.add(ui.label("Décisions", "bold")).padTop(GAP).row()
        val maintenance = Table().apply { defaults().padRight(4f) }
        MAINTENANCE_LEVELS.forEach { (label, level) ->
            val b = ui.button(label, "toggle") {
                session.infrastructure.setMaintenance(id, level)
                message = "Niveau d'entretien : ${label.lowercase()}."
                nav.refresh()
            }
            b.isChecked = kotlin.math.abs(infra.maintenanceLevel - level) < LEVEL_EPSILON
            maintenance.add(b)
        }
        into.add(ui.label("Entretien", "muted")).row()
        into.add(maintenance).left().row()
        if (infra.renovationProjectId == null) {
            val cost = Formatting.billions(session.infrastructure.renovationCost(id))
            into.add(ui.button("Lancer une rénovation ($cost)") {
                message = session.infrastructure.renovate(id).fold({ "Rénovation lancée." }, { it.message })
                nav.refresh()
            }).left().padTop(4f).row()
        }
        val type = session.db.infrastructureTypes[session.context.catalog.item(id)?.type]
        if (type?.canClose == true) {
            into.add(ui.button("Fermer définitivement…") {
                message = "Confirmez la fermeture : les emplois locaux seront supprimés."
                pendingClose = id
                nav.refresh()
            }).left().padTop(4f).row()
            if (pendingClose == id) {
                into.add(ui.button("Confirmer la fermeture", "accent") {
                    message = session.infrastructure.close(id).fold({ "Installation fermée." }, { it.message })
                    pendingClose = null
                    nav.refresh()
                }).left().row()
            }
        }
    }

    private var pendingClose: String? = null
    val unitSheet = UnitSheet(ui, nav, expanded)

    private var confirmWar: String? = null

    private fun buildCountry(into: Table, id: String) {
        val data = session.db.countries[id]
        if (data == null || id == session.state.player.countryId) {
            into.add(ui.label(if (data == null) "Ce pays n'est pas encore simulé dans cette version." else "Votre pays.", "muted", wrap = true)).row()
            return
        }
        val player = session.state.player.countryId
        val relation = session.diplomacy.relation(id)
        val score = fr.president.engine.diplomacy.RelationCalculator(session.context).score(id, player)
        val leader = session.state.characters.getValue(session.state.countries.getValue(id).leaderId)
        val head = Table()
        head.add(ui.portraits.image(leader)).size(PORTRAIT).padRight(8f)
        val who = Table().apply { defaults().left() }
        who.add(ui.label(leader.fullName, "bold")).row()
        who.add(ui.label(data.definition.institutions.headOfGovernment(leader.female), "muted")).row()
        head.add(who).growX().left().top()
        into.add(head).growX().left().row()
        // Jauge de relation : rouge (hostile) → vert (allié).
        val gauge = Table()
        val color = Theme.relation(score.toFloat(), com.badlogic.gdx.graphics.Color())
        gauge.add(ui.label("Relation : ${relation.label}", "value", color)).left().expandX()
        gauge.add(ui.label(Math.round(score * PERCENT).toString() + " / 100", "small", color)).right()
        into.add(gauge).growX().padTop(GAP).row()
        val bar = Table().apply { setBackground(ui.skin.fill(Theme.panelAlt)) }
        bar.add(Table().apply { setBackground(ui.skin.fill(color)) }).width(com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth(score.toFloat().coerceIn(0.02f, 1f), bar)).height(GAUGE).left().expandX()
        into.add(bar).growX().height(GAUGE).padTop(2f).row()
        relation.factors.forEach { f ->
            into.add(ui.label((if (f.weight >= 0) "▲ " else "▼ ") + f.label, "small", if (f.weight >= 0) Theme.good else Theme.bad, wrap = true)).row()
        }
        val economy = session.state.countries.getValue(id).economy
        into.add(ui.label("Économie : croissance ${Formatting.signedPercent(economy.realGrowth)}, chômage ${Formatting.percent(economy.unemployment)}", "muted", wrap = true)).padTop(4f).row()
        val geo = session.military.geo
        if (geo.atWar(player, id)) into.add(ui.label("⚔ Nous sommes en guerre avec ce pays.", "bold", Theme.bad)).padTop(4f).row()

        into.add(ui.label("Coopérer", "title", Theme.catDiplomacy)).padTop(GAP).row()
        val friendly = Table().apply { defaults().growX().uniformX().pad(2f) }
        listOf(
            "€ Commerce" to ("TARIFF_REDUCTION" to mapOf("percent" to 3.0)),
            "✚ Aide" to ("FINANCIAL_AID" to mapOf("amountBillions" to 2.0)),
            "⚔ Défense" to ("DEFENSE_COOPERATION" to mapOf("intensity" to 1.0)),
            "⚑ Alliance" to ("DEFENSIVE_ALLIANCE" to mapOf("scope" to 1.0)),
            "⚒ Armes" to ("ARMS_SALE" to mapOf("amountBillions" to 3.0)),
            "⚡ Électricité" to ("ELECTRICITY_SUPPLY" to mapOf("volumeTWh" to 8.0, "pricePercent" to 100.0)),
        ).forEachIndexed { i, (label, clause) ->
            friendly.add(ui.colorButton(label, Theme.catDiplomacy) { nav.prepareProposal(id, clause.first, clause.second) })
            if (i % BUTTONS_PER_ROW == BUTTONS_PER_ROW - 1) friendly.row()
        }
        into.add(friendly).growX().row()
        into.add(ui.label("Chaque bouton prépare une proposition : vous ajustez les termes, puis l'envoyez.", "muted", wrap = true)).row()
        talks.build(into, leader.id, leader.fullName)

        into.add(ui.label("Faire pression", "title", Theme.catArmy)).padTop(GAP).row()
        val hostile = Table().apply { defaults().growX().uniformX().pad(2f) }
        val sanctioning = session.diplomacy.isSanctioning(id)
        hostile.add(ui.colorButton(if (sanctioning) "✔ Lever sanctions" else "✖ Sanctions", Theme.catAlerts) {
            if (sanctioning) session.diplomacy.liftSanctions(id) else session.diplomacy.sanction(id)
            message = if (sanctioning) "Sanctions levées." else "Sanctions imposées : leur économie et nos échanges en pâtiront."
            nav.refresh()
        })
        hostile.add(ui.colorButton("☎ Condamner", Theme.catAlerts) {
            session.diplomacy.condemn(id)
            message = "Condamnation publique prononcée."
            nav.refresh()
        }).row()
        if (!geo.atWar(player, id)) {
            hostile.add(ui.colorButton("⚠ Ultimatum…", Theme.catAlerts) { nav.open(PanelId.DIPLOMACY, id) })
            hostile.add(ui.colorButton("⚔ Guerre…", Theme.catArmy) { confirmWar = id; nav.refresh() }).row()
        } else {
            hostile.add(ui.colorButton("☮ Cessez-le-feu", Theme.catDiplomacy) { nav.prepareProposal(id, "CEASEFIRE", mapOf("days" to 90.0)) })
            hostile.add(ui.colorButton("⚔ Armées", Theme.catArmy) { nav.open(PanelId.ARMY) }).row()
        }
        into.add(hostile).growX().row()
        if (confirmWar == id && !geo.atWar(player, id)) {
            into.add(ui.label("Une guerre aura un coût humain, économique et politique considérable. Vos alliés pourraient ne pas suivre.", "small", Theme.warning, wrap = true)).growX().row()
            val confirm = Table().apply { defaults().padRight(4f) }
            confirm.add(ui.colorButton("Confirmer la guerre", Theme.catArmy) {
                session.diplomacy.declareWar(id); confirmWar = null; message = "La France est en guerre."; nav.refresh()
            })
            confirm.add(ui.button("Annuler") { confirmWar = null; nav.refresh() })
            into.add(confirm).left().row()
        }
        into.add(ui.button("Tout voir dans la Diplomatie", "flat") { nav.open(PanelId.DIPLOMACY, id) }).left().padTop(GAP).row()
    }

    private companion object {
        val MAINTENANCE_LEVELS = listOf("Réduit" to 0.7, "Normal" to 1.0, "Renforcé" to 1.4)
        const val LEVEL_EPSILON = 0.05
        const val PORTRAIT = 56f
        const val PERCENT = 100.0
        const val GAUGE = 8f
        const val BUTTONS_PER_ROW = 3
    }
}
