package fr.president.engine.events

import fr.president.engine.effects.EffectSpec
import kotlinx.serialization.Serializable

/**
 * Ampleur des événements : un même événement peut être limité ou exceptionnel (petit feu de
 * broussailles ou mégafeu, séisme léger ou dévastateur). L'ampleur multiplie les effets et les
 * coûts, change le titre et l'urgence. Les événements « fixes » (sommets, demandes d'élus...)
 * n'ont pas d'ampleur.
 */
@Serializable
data class IntensityLevel(val id: String, val label: String, val factor: Double, val weight: Double)

@Serializable
data class IntensityFile(
    val levels: List<IntensityLevel>,
    /** Événements sans ampleur variable. */
    val fixed: List<String> = emptyList(),
    /** Titres par niveau (un par niveau, dans l'ordre de « levels »). */
    val headlines: Map<String, List<String>> = emptyMap(),
    /** Conséquences supplémentaires à l'arrivée de l'événement (valeurs pour une ampleur « importante »). */
    val consequences: Map<String, List<EffectSpec>> = emptyMap(),
)

object EventIntensity {
    const val FACTOR = "intensity"
    const val LEVEL = "intensityLevel"

    /** Effet mis à l'échelle de l'ampleur (une suite d'histoire devient plus ou moins probable). */
    fun scale(spec: EffectSpec, factor: Double): EffectSpec = when {
        factor == 1.0 || spec.param != null -> spec
        spec.target.startsWith("chain.") -> spec.copy(amount = (spec.amount * factor).coerceIn(0.0, 1.0))
        spec.target.startsWith("operation.") || spec.target.endsWith(".dismiss") || spec.target.endsWith(".scandal") -> spec
        else -> spec.copy(amount = spec.amount * factor)
    }

    private val COST = Regex("""(\d+(?:[,.]\d+)?)\s*(M€|Md€)""")

    /** Réécrit les montants d'une aide (« Coût : 150 M€ ») selon l'ampleur. */
    fun scaleCosts(text: String, factor: Double): String {
        if (factor == 1.0) return text
        return COST.replace(text) { m ->
            val millions = m.groupValues[1].replace(',', '.').toDouble() * (if (m.groupValues[2] == "Md€") THOUSAND else 1.0) * factor
            when {
                millions >= THOUSAND -> String.format(java.util.Locale.FRENCH, "%.1f Md€", millions / THOUSAND)
                millions >= ROUND * 2 -> "${Math.round(millions / ROUND) * ROUND.toLong()} M€"
                else -> "${Math.max(1L, Math.round(millions))} M€"
            }
        }
    }

    private const val THOUSAND = 1000.0
    private const val ROUND = 10.0
}
