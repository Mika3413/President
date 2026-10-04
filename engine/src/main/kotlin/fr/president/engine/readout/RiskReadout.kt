package fr.president.engine.readout

import fr.president.engine.crisis.MeasureDef
import fr.president.engine.crisis.RiskDef
import fr.president.engine.events.EventScope
import fr.president.engine.events.EventSystem
import fr.president.engine.events.ScopeRef
import fr.president.engine.events.SenderResolver
import fr.president.engine.simulation.SimulationContext
import kotlin.math.pow

/**
 * Les risques qui pèsent sur le pays dans les 30 prochains jours (feux, crues, épidémie,
 * attentat...), calculés avec les mêmes probabilités que la simulation, et les mesures qui
 * permettent de les réduire. Lecture seule : rien n'est tiré au hasard.
 */
class RiskReadout(private val ctx: SimulationContext) {

    data class Risk(
        val def: RiskDef,
        /** Probabilité qu'au moins un des événements survienne d'ici [HORIZON_DAYS] jours. */
        val probability: Double,
        val level: String,
        val tone: Tone,
        /** Lieu le plus exposé (« Var », « Marseille »...), si le risque est localisé. */
        val hotspot: String?,
        /** Code du département le plus exposé, pour y lancer une mesure locale. */
        val hotspotDepartment: String?,
        /** Mesures qui réduisent ce risque, et celles déjà en vigueur. */
        val measures: List<MeasureDef>,
        val activeMeasures: List<MeasureDef>,
    )

    private val events = EventSystem()

    private var cacheKey: Any? = null
    private var cache: List<Risk> = emptyList()

    /** Les risques, du plus probable au moins probable (recalculés quand l'heure ou les mesures changent). */
    fun risks(): List<Risk> {
        val file = ctx.playerData.measures ?: return emptyList()
        val key = Triple(ctx.now, ctx.state.measures.active.toList(), ctx.state.events.lastFired.size)
        if (key == cacheKey) return cache
        val activeIds = ctx.state.measures.active.map { it.id }.toSet()
        cache = file.risks.map { r -> risk(r, file.measures, activeIds) }.sortedByDescending { it.probability }
        cacheKey = key
        return cache
    }

    fun risk(id: String): Risk? = risks().firstOrNull { it.def.id == id }

    /** Risques élevés sans aucune mesure de réduction en vigueur : ce que le conseiller signale. */
    fun unattended(): List<Risk> = risks().filter { it.probability >= HIGH && it.activeMeasures.isEmpty() && it.measures.isNotEmpty() }

    private fun risk(r: RiskDef, measures: List<MeasureDef>, activeIds: Set<String>): Risk {
        var none = 1.0
        var best: ScopeRef? = null
        var bestP = 0.0
        for (id in r.events) {
            val def = ctx.db.events.firstOrNull { it.id == id } ?: continue
            val (daily, scope) = events.dailyRisk(ctx, def)
            none *= (1 - daily).pow(HORIZON_DAYS)
            if (daily > bestP && scope?.id != null) { bestP = daily; best = scope }
        }
        val p = 1 - none
        val (level, tone) = when {
            p >= VERY_HIGH -> "Très élevé" to Tone.BAD
            p >= HIGH -> "Élevé" to Tone.BAD
            p >= MODERATE -> "Modéré" to Tone.WARNING
            else -> "Faible" to Tone.GOOD
        }
        val reducing = measures.filter { m -> m.affects.any { it.event in r.events && (it.probability < 1 || it.intensity < 1) } }
        val senders = SenderResolver(ctx)
        val dept = senders.departmentOf(best)
        val hotspot = when (best?.type) {
            EventScope.CITY -> senders.cityName(best.id)
            EventScope.DEPARTMENT, EventScope.INFRASTRUCTURE -> dept?.let { senders.departmentName(it) }
            else -> null
        }?.ifBlank { null }
        return Risk(r, p, level, tone, hotspot, dept, reducing, reducing.filter { it.id in activeIds })
    }

    companion object {
        const val HORIZON_DAYS = 30.0
        const val MODERATE = 0.08
        const val HIGH = 0.25
        const val VERY_HIGH = 0.5
    }
}
