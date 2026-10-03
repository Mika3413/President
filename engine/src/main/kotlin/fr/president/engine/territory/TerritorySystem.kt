package fr.president.engine.territory

import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.approach

/** Dynamiques locales mensuelles : chômage départemental, satisfaction des villes, élus locaux. */
class TerritorySystem : SimulationSystem {
    override val name = "territory"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val territory = ctx.state.territory
        val national = ctx.state.playerCountry.economy.unemployment
        for (dept in territory.departments.values) {
            dept.unemployment = (national + dept.unemploymentOffset).coerceAtLeast(MIN_UNEMPLOYMENT)
        }
        for (city in territory.cities.values) {
            val dept = territory.departments[city.department] ?: continue
            city.satisfaction = approach(city.satisfaction, dept.approval, CITY_ADJUSTMENT)
            // Les élus locaux sont sensibles à l'humeur de leurs administrés.
            city.mayorId?.let { ctx.state.characters[it] }?.let { mayor ->
                mayor.relationWithPlayer = approach(mayor.relationWithPlayer, (mayor.relationWithPlayer + city.satisfaction) / 2, LOCAL_DRIFT)
            }
        }
    }

    private companion object {
        const val MIN_UNEMPLOYMENT = 0.02
        const val CITY_ADJUSTMENT = 0.3
        const val LOCAL_DRIFT = 0.1
    }
}
