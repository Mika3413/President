package fr.president.engine.territory

import fr.president.engine.effects.ActiveEffect
import fr.president.engine.effects.EffectSpec
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
enum class ProjectStatus { IN_PROGRESS, COMPLETED, CANCELLED }

/** Grand projet en construction (transport, hôpital, rénovation d'infrastructure...). */
@Serializable
class ProjectState(
    val id: String,
    val name: String,
    val kind: String,
    /** Élément de carte où afficher le chantier. */
    val locationId: String,
    val startedAt: WorldTime,
    val completesAt: WorldTime,
    val costBillions: Double,
    /** Effets à appliquer à la fin, cibles déjà résolues. */
    val onCompletion: List<EffectSpec> = emptyList(),
    var status: ProjectStatus = ProjectStatus.IN_PROGRESS,
) {
    fun progress(now: WorldTime): Double {
        val total = (completesAt.seconds - startedAt.seconds).toDouble()
        if (total <= 0) return 1.0
        return ((now.seconds - startedAt.seconds) / total).coerceIn(0.0, 1.0)
    }
}

/** Utilitaire pour transformer des effets déjà résolus en effets actifs. */
fun EffectSpec.toActive(now: WorldTime, source: String, resolvedTarget: String = target, value: Double = amount) =
    ActiveEffect(
        target = resolvedTarget,
        total = value,
        startsAt = now.plusDays(delayDays),
        endsAt = now.plusDays(delayDays + days),
        source = source,
    )
