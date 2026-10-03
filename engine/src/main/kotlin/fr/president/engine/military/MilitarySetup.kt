package fr.president.engine.military

import fr.president.engine.data.Domain
import fr.president.engine.data.UnitTypeDef
import fr.president.engine.simulation.SimulationContext

/**
 * Mise en place des forces : positionne les unités du joueur sur leurs bases et génère
 * les armées des pays IA à partir de leur profil. Idempotent : sert aussi à mettre à niveau
 * une sauvegarde créée avant l'introduction des zones de théâtre.
 */
class MilitarySetup(private val ctx: SimulationContext) {
    private val zones get() = ctx.db.zones

    fun ensure() {
        placePlayerUnits()
        if (ctx.state.military.units.values.none { it.countryId != ctx.state.player.countryId }) generateForeignForces()
    }

    private fun placePlayerUnits() {
        val player = ctx.state.player.countryId
        val defs = ctx.playerData.military?.units.orEmpty().associateBy { it.id }
        val bases = ctx.playerData.military?.bases.orEmpty().associateBy { it.id }
        for (u in ctx.state.military.units.values) {
            if (u.countryId.isBlank()) u.countryId = player
            if (u.countryId != player || u.zoneId.isNotBlank()) continue
            defs[u.id]?.let { u.name = it.name }
            val base = bases[u.baseId] ?: continue
            val sea = ctx.db.unitTypes[u.type]?.domain == Domain.SEA
            u.zoneId = zoneFor(player, base.lon, base.lat, sea)
            u.homeZoneId = u.zoneId
        }
    }

    fun zoneFor(country: String, lon: Double, lat: Double, coastal: Boolean): String =
        (zones.nearest(lon, lat) { !it.sea && it.owner == country && (!coastal || it.coastal) }
            ?: zones.nearest(lon, lat) { !it.sea && it.owner == country }
            ?: zones.nearest(lon, lat) { !it.sea })!!.id

    private fun generateForeignForces() {
        val p = ctx.db.militaryParameters
        for (country in ctx.state.countries.keys.filter { it != ctx.state.player.countryId }) {
            val strategic = ctx.db.country(country).definition.strategic
            val territory = zones.ownedBy(country)
            if (territory.isEmpty()) continue
            val capital = strategic.capital
            val capitalZone = capital?.let { zoneFor(country, it.lon, it.lat, false) } ?: territory.first().id
            val border = territory.filter { z -> zones.neighbors(z.id).any { !it.sea && it.owner != country } }.map { it.id }
            val interior = zones.within(listOf(capitalZone), INTERIOR_STEPS) { !it.sea && it.owner == country }.keys.toList()
            repeat(strategic.forces.land) { i ->
                val type = ctx.db.unitType(LAND_MIX[i % LAND_MIX.size])
                val zone = if (i % 2 == 0 && border.isNotEmpty()) ctx.rng.pick(border) else ctx.rng.pick(interior.ifEmpty { listOf(capitalZone) })
                createUnit(country, type, zone, p.aiUnitStartReadiness)
            }
            repeat(strategic.forces.air) { createUnit(country, ctx.db.unitType(AIR), capitalZone, p.aiUnitStartReadiness) }
            if (strategic.forces.sea > 0) {
                val port = capital?.let { zoneFor(country, it.lon, it.lat, true) }
                if (port != null && zones.zone(port).coastal) repeat(strategic.forces.sea) { createUnit(country, ctx.db.unitType(SEA), port, p.aiUnitStartReadiness) }
            }
        }
    }

    fun createUnit(country: String, type: UnitTypeDef, zone: String, readiness: Double): UnitState {
        val military = ctx.state.military
        military.unitCounter++
        val number = military.units.values.count { it.countryId == country && it.type == type.id } + 1
        val label = type.label.replaceFirstChar { it.lowercase() }
        val name = if (country == ctx.state.player.countryId) "${ordinal(number)} ${label} (nouvelle)"
        else "${ordinal(number)} ${label} — ${ctx.db.country(country).definition.name}"
        val unit = UnitState(
            id = "u-${country.lowercase()}-${military.unitCounter}",
            branch = type.domain.name, type = type.id, baseId = zone, personnel = type.personnel,
            equipment = mutableMapOf(), readiness = readiness, morale = MORALE, ammunition = STOCK, fuel = STOCK,
            name = name.replaceFirstChar { it.uppercase() }, countryId = country, zoneId = zone, homeZoneId = zone,
        )
        military.units[unit.id] = unit
        return unit
    }

    private fun ordinal(n: Int) = if (n == 1) "1re" else "${n}e"

    private companion object {
        val LAND_MIX = listOf("MECHANIZED_BRIGADE", "INFANTRY_BRIGADE", "ARMORED_BRIGADE", "INFANTRY_BRIGADE", "MECHANIZED_BRIGADE")
        const val AIR = "FIGHTER_WING"
        const val SEA = "SURFACE_GROUP"
        const val INTERIOR_STEPS = 3
        const val MORALE = 0.6
        const val STOCK = 0.75
    }
}
