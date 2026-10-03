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
    private var message: String? = null
    private val session get() = nav.session

    override val title: String
        get() = when (val s = selection) {
            is MapSelection.Department -> session.local.department(s.code).title
            is MapSelection.Region -> session.local.region(s.code).title
            is MapSelection.City -> session.local.city(s.id).title
            is MapSelection.Infrastructure -> session.local.infrastructure(s.id).title
            is MapSelection.Base -> session.local.base(s.id).title
            is MapSelection.Country -> session.db.countries[s.id]?.definition?.name ?: "Pays non simulé"
            null -> ""
        }

    override fun build(into: Table) {
        message?.let { into.add(ui.label(it, "small", Theme.accent, wrap = true)).padBottom(GAP).row() }
        when (val s = selection) {
            is MapSelection.Department -> {
                into.add(SheetView(ui, session.local.department(s.code), expanded)).row()
                val region = session.state.territory.departments.getValue(s.code).region
                into.add(ui.button("Voir la région", "default") { nav.select(MapSelection.Region(region)) }).left().row()
            }
            is MapSelection.Region -> into.add(SheetView(ui, session.local.region(s.code), expanded)).row()
            is MapSelection.City -> into.add(SheetView(ui, session.local.city(s.id), expanded)).row()
            is MapSelection.Infrastructure -> buildInfrastructure(into, s.id)
            is MapSelection.Base -> into.add(SheetView(ui, session.local.base(s.id), expanded)).row()
            is MapSelection.Country -> buildCountry(into, s.id)
            null -> Unit
        }
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

    private fun buildCountry(into: Table, id: String) {
        val data = session.db.countries[id]
        if (data == null || id == session.state.player.countryId) {
            into.add(ui.label(if (data == null) "Ce pays n'est pas encore simulé dans cette version." else "Votre pays.", "muted", wrap = true)).row()
            return
        }
        val relation = session.diplomacy.relation(id)
        into.add(ui.label("Relations : ${relation.label}", "large")).row()
        relation.factors.forEach { f ->
            into.add(ui.label("• ${f.label}", "small", if (f.weight >= 0) Theme.good else Theme.bad, wrap = true)).row()
        }
        val economy = session.state.countries.getValue(id).economy
        into.add(ui.label("Économie : croissance ${Formatting.signedPercent(economy.realGrowth)}, chômage ${Formatting.percent(economy.unemployment)}", "muted", wrap = true)).padTop(GAP).row()
        into.add(ui.button("Ouvrir la diplomatie", "accent") { nav.open(PanelId.DIPLOMACY, id) }).left().padTop(GAP).row()
    }

    private companion object {
        val MAINTENANCE_LEVELS = listOf("Réduit" to 0.7, "Normal" to 1.0, "Renforcé" to 1.4)
        const val LEVEL_EPSILON = 0.05
    }
}
