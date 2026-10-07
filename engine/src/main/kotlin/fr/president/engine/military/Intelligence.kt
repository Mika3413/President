package fr.president.engine.military

import fr.president.engine.government.MinistryEffectiveness
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.GameRandom
import fr.president.engine.util.Hashing

/**
 * Renseignement : un pays ne voit que les unités proches de ses forces ou de son territoire,
 * et en estime la force avec une erreur d'autant plus grande que ses services sont faibles.
 */
class Intelligence(private val ctx: SimulationContext) {
    private val geo = Geopolitics(ctx)

    fun observedZones(observer: String): Set<String> {
        val friends = geo.coBelligerents(observer) + observer
        val eyes = ctx.state.military.units.values.filter { it.countryId in friends && !it.destroyed }.map { it.zoneId } +
            friends.flatMap { geo.territoryOf(it) }
        val seen = ctx.db.zones.within(eyes.toSet(), ctx.db.militaryParameters.visibilityZones) { true }.keys
        // Les stations radar voient plus loin.
        val radars = ctx.state.military.works.filter { it.type == "radar" && it.level > 0 && it.countryId in friends }
        if (radars.isEmpty()) return seen
        val forts = FortificationService(ctx)
        return seen + radars.flatMap { r ->
            ctx.db.zones.within(listOf(r.zoneId), ctx.db.militaryParameters.visibilityZones + forts.value(r, "intel").toInt()) { true }.keys
        }
    }

    fun visibleUnits(observer: String): List<UnitState> {
        val observed = observedZones(observer)
        val friends = geo.coBelligerents(observer) + observer
        // Les unités au contact sont connues (presse, satellites) ; les alliés partagent leurs positions.
        return ctx.state.military.units.values.filter {
            !it.destroyed && (it.countryId in friends || it.zoneId in observed || it.inCombat || geo.allied(observer, it.countryId))
        }
    }

    fun quality(observer: String): Double {
        val base = ctx.db.country(observer).definition.strategic.intelligenceQuality
        if (observer != ctx.state.player.countryId) return base
        return (base + MINISTRY_WEIGHT * (MinistryEffectiveness(ctx).of(DEFENSE_MINISTRY) - NEUTRAL)).coerceIn(0.0, 1.0)
    }

    /** Force estimée (0..1) d'une unité étrangère, stable dans la journée. */
    fun estimatedStrength(observer: String, unit: UnitState): Double {
        if (unit.countryId == observer) return unit.strength
        val seed = Hashing.fnv1a64("${observer}|${unit.id}|${ctx.now.dayIndex}|${ctx.state.meta.seed}")
        val error = GameRandom(seed).nextGaussian() * (1 - quality(observer)) * MAX_ERROR * (1 - dossier(observer, unit.countryId))
        return (unit.strength + error).coerceIn(0.05, 1.0)
    }

    /** Estimation globale des forces terrestres d'un pays (unités, puissance). */
    fun estimatedForces(observer: String, country: String): Pair<Int, Double> {
        val units = ctx.state.military.units.values.filter { it.countryId == country && !it.destroyed }
        val seed = Hashing.fnv1a64("$observer|$country|${ctx.now.monthIndex}")
        val error = 1 + GameRandom(seed).nextGaussian() * (1 - quality(observer)) * MAX_ERROR * (1 - dossier(observer, country))
        return (units.size * error).toInt().coerceAtLeast(0) to geo.landPower(country) * error
    }

    /** Un dossier de la DGSE sur ce pays rend nos estimations fiables. */
    private fun dossier(observer: String, country: String): Double =
        if (observer == ctx.state.player.countryId) ctx.state.intel.dossiers[country] ?: 0.0 else 0.0

    private companion object {
        const val DEFENSE_MINISTRY = "armed_forces"
        const val MINISTRY_WEIGHT = 0.2
        const val NEUTRAL = 0.5
        const val MAX_ERROR = 0.35
    }
}
