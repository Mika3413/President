package fr.president.engine.readout

import fr.president.engine.data.theCountry
import fr.president.engine.data.ofCountry
import fr.president.engine.data.toCountry

import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.Intelligence
import fr.president.engine.military.WorldCities
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/** Fiche d'une ville étrangère : qui la tient, ce que l'on sait de sa garnison, son importance. */
class WorldCityReadout(private val ctx: SimulationContext) {
    private val cities = WorldCities(ctx)

    data class CitySheet(val sheet: LocalReadouts.Sheet, val zoneId: String?, val country: String, val holder: String, val garrison: List<String>)

    /** Pays qui tient la ville (pour la couleur de son marqueur), ou null si inconnue. */
    fun holder(id: String): String? = cities.byId(id)?.let { cities.holder(it) }

    fun sheet(id: String): CitySheet? {
        val city = cities.byId(id) ?: return null
        val geo = Geopolitics(ctx)
        val player = ctx.state.player.countryId
        val zone = cities.zoneOf(city)
        val holder = cities.holder(city)
        val name = { c: String -> ctx.db.countries[c]?.definition?.name ?: c }
        val visible = Intelligence(ctx).visibleUnits(player).filter { it.zoneId == zone && !it.destroyed }
        val occupied = holder != city.country
        val control = Indicator(
            "Contrôle", if (occupied) "Occupée par ${ctx.db.theCountry(holder)}" else name(holder),
            when {
                holder == player -> Tone.GOOD
                geo.atWar(player, holder) -> Tone.BAD
                occupied -> Tone.WARNING
                else -> Tone.NEUTRAL
            },
            if (occupied) "La ville appartient ${ctx.db.toCountry(city.country)}." else "",
        )
        val relation = if (holder != player && holder in ctx.state.countries) {
            val score = RelationCalculator(ctx).score(holder, player)
            Indicator("Relation avec ${ctx.db.theCountry(holder)}", "${Math.round(score * PERCENT)} / 100", if (score > GOOD) Tone.GOOD else if (score < BAD) Tone.BAD else Tone.NEUTRAL)
        } else null
        val garrison = Indicator("Troupes repérées", if (visible.isEmpty()) "Aucune connue" else "${visible.size} unité(s)",
            if (visible.any { geo.atWar(player, it.countryId) }) Tone.WARNING else Tone.NEUTRAL,
            "Seules les unités vues par notre renseignement apparaissent.",
            visible.map { it.name to name(it.countryId) })
        val distance = ctx.playerData.definition.strategic.capital?.let { cap ->
            val d = haversine(cap.lat, cap.lon, city.lat, city.lon)
            Indicator("Distance de ${cap.name}", "${Formatting.integer(d)} km", Tone.NEUTRAL)
        }
        val subtitle = buildString {
            append(if (city.capital) "Capitale · " else "")
            append(name(city.country))
            if (city.populationMillions > 0) append(" · ${Formatting.amount(city.populationMillions)} M hab.")
        }
        val problems = buildList {
            if (geo.atWar(player, holder)) add("Ville ennemie : sélectionnez une unité puis touchez la ville pour l'attaquer.")
            if (city.capital && geo.atWar(player, city.country) && holder == player) add("Capitale prise : ${name(city.country)} est au bord de la capitulation.")
        }
        val sheet = LocalReadouts.Sheet(city.name, subtitle, listOfNotNull(control, relation, garrison, distance), problems = problems)
        return CitySheet(sheet, zone, city.country, holder, visible.map { it.id })
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = EARTH_KM
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    private companion object {
        const val PERCENT = 100
        const val GOOD = 0.6
        const val BAD = 0.35
        const val EARTH_KM = 6371.0
    }
}
