package fr.president.engine.military

import fr.president.engine.data.theCountry
import fr.president.engine.data.ofCountry
import fr.president.engine.data.toCountry

import fr.president.engine.data.WorldCityDef
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService

/**
 * Les villes changent de mains avec les zones qui les entourent. Prendre une ville ennemie
 * galvanise l'opinion ; perdre une ville française est un choc. Les villes alliées tombées sont
 * annoncées dans l'actualité.
 */
class CitiesSystem : SimulationSystem {
    override val name = "cities"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val geo = Geopolitics(ctx)
        // Rien ne change sans guerre : on évite tout calcul en temps de paix.
        if (geo.activeWars().isEmpty() && ctx.state.military.occupied.isEmpty()) return
        val player = ctx.state.player.countryId
        val control = ctx.state.military.cityControl
        for (city in WorldCities(ctx).all()) {
            val zone = WorldCities(ctx).zoneOf(city) ?: continue
            val holder = geo.controllerOf(zone)
            val before = control[city.id] ?: city.country
            if (holder == before) continue
            control[city.id] = holder
            announce(ctx, geo, city, before, holder, player)
        }
    }

    private fun announce(ctx: SimulationContext, geo: Geopolitics, city: WorldCityDef, before: String, holder: String, player: String) {
        val name = { id: String -> ctx.db.countries[id]?.definition?.name ?: id }
        val what = if (city.capital) "la capitale ${city.name}" else city.name
        val journal = JournalService(ctx)
        when {
            holder == player -> {
                ctx.effects.trigger(fr.president.engine.effects.EffectSpec("opinion.national", if (city.capital) CAPITAL_GAIN else CITY_GAIN), null, emptyMap(), "city")
                ctx.notifications.post(NotificationCategory.MILITARY, Urgency.IMPORTANT, "Nos troupes prennent ${city.name}",
                    "${name(city.country)} perd $what.", city.id, journal = false)
                journal.add("Guerre", "Prise de ${city.name} (${name(city.country)})", Tone.GOOD)
            }
            before == player || city.country == player -> {
                ctx.effects.trigger(fr.president.engine.effects.EffectSpec("opinion.national", if (city.capital) -CAPITAL_GAIN else -CITY_GAIN), null, emptyMap(), "city")
                ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "${city.name} est tombée",
                    "Les forces ${ctx.db.ofCountry(holder)} contrôlent désormais $what.", city.id, journal = false)
                journal.add("Guerre", "Perte de ${city.name} au profit ${ctx.db.ofCountry(holder)}", Tone.BAD)
            }
            holder == city.country -> ctx.notifications.news(NotificationCategory.MILITARY, "${name(city.country)} reprend ${city.name}", city.id)
            else -> ctx.notifications.news(NotificationCategory.MILITARY, "${name(holder)} s'empare de ${city.name}", city.id)
        }
    }

    private companion object {
        const val CITY_GAIN = 0.002
        const val CAPITAL_GAIN = 0.008
    }
}

/** Villes étrangères (et françaises) avec leur zone de théâtre, calculée une fois. */
class WorldCities(private val ctx: SimulationContext) {
    fun all(): List<WorldCityDef> = cache.getOrPut(ctx.db) { build() }

    private fun build(): List<WorldCityDef> {
        val french = ctx.playerData.territory?.cities.orEmpty().filter { it.rank <= 2 }.map {
            WorldCityDef(it.id, it.name, ctx.state.player.countryId, it.lat, it.lon, it.urbanAreaPopulation / 1e6, it.id == ctx.playerData.definition.capitalCityId, it.rank)
        }
        // Les villes du pays joué viennent de son territoire, pas de la liste mondiale.
        return ctx.db.worldCities.filter { it.country != ctx.state.player.countryId || french.isEmpty() } + french
    }

    fun zoneOf(city: WorldCityDef): String? = zones.getOrPut(city.id) {
        ctx.db.zones.nearest(city.lon, city.lat) { !it.sea && it.owner == city.country }?.id
            ?: ctx.db.zones.nearest(city.lon, city.lat) { !it.sea }?.id ?: ""
    }.ifEmpty { null }

    fun byId(id: String): WorldCityDef? = all().firstOrNull { it.id == id }

    /** Pays qui tient la ville aujourd'hui. */
    fun holder(city: WorldCityDef): String = zoneOf(city)?.let { Geopolitics(ctx).controllerOf(it) } ?: city.country

    private companion object {
        val cache = java.util.WeakHashMap<Any, List<WorldCityDef>>()
        val zones = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
