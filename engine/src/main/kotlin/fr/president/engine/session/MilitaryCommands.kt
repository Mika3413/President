package fr.president.engine.session

import fr.president.engine.military.Geopolitics
import fr.president.engine.military.Intelligence
import fr.president.engine.military.OrderService
import fr.president.engine.military.ProductionService
import fr.president.engine.military.UnitOrder
import fr.president.engine.military.UnitState
import fr.president.engine.military.War
import fr.president.engine.simulation.SimulationContext

/** Commandement militaire du président : ordres, production, mobilisation, renseignement. */
class MilitaryCommands(private val ctx: SimulationContext) {
    private val player get() = ctx.state.player.countryId
    val geo get() = Geopolitics(ctx)
    val intelligence get() = Intelligence(ctx)
    val production get() = ProductionService(ctx)

    fun ownUnits(): List<UnitState> = ctx.state.military.units.values.filter { it.countryId == player && !it.destroyed }

    fun order(unitId: String, order: UnitOrder, zone: String? = null): OrderService.Outcome {
        val unit = ctx.state.military.units[unitId] ?: return OrderService.Outcome.Refused("Unité inconnue")
        if (unit.countryId != player) return OrderService.Outcome.Refused("Cette unité n'est pas sous vos ordres.")
        return OrderService(ctx).issue(unitId, order, zone)
    }

    fun visibleUnits(): List<UnitState> = intelligence.visibleUnits(player)

    fun wars(): List<War> = geo.ongoingWars()

    /** Zone de théâtre la plus proche d'un point (pour désigner une cible sur la carte). */
    fun zoneAt(lon: Double, lat: Double): String? = ctx.db.zones.nearest(lon, lat)?.id
}
