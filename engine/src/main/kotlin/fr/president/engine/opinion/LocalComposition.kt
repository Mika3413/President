package fr.president.engine.opinion

import fr.president.engine.data.SocialGroupsDefinition
import fr.president.engine.territory.DepartmentState

/**
 * Composition sociale d'un département : part locale de chaque groupe,
 * déduite de ses caractéristiques (urbanisation, âge, revenus).
 */
class LocalComposition(
    private val definition: SocialGroupsDefinition,
    departments: Collection<DepartmentState>,
) {
    private val nationalMeans: Map<String, Double>

    init {
        val total = departments.sumOf { it.population }.toDouble()
        nationalMeans = ATTRIBUTES.associateWith { attr ->
            departments.sumOf { attribute(it, attr) * it.population } / total
        }
    }

    /** Part de chaque groupe dans le département, normalisée par partition. */
    fun shares(dept: DepartmentState): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (partition in definition.partitions) {
            val groups = definition.groups.filter { it.partition == partition.id }
            val raw = groups.associate { g ->
                val multiplier = g.localAttribute?.let { attr ->
                    val mean = nationalMeans[attr] ?: 1.0
                    Math.pow(attribute(dept, attr) / mean, g.localElasticity)
                } ?: 1.0
                g.id to g.populationShare * multiplier
            }
            val sum = raw.values.sum()
            raw.forEach { (id, v) -> result[id] = v / sum }
        }
        return result
    }

    companion object {
        private val ATTRIBUTES = listOf("urbanShare", "ruralShare", "seniorShare", "nonSeniorShare", "incomeIndex")
        private const val MIN_ATTRIBUTE = 0.01

        fun attribute(d: DepartmentState, name: String): Double = when (name) {
            "urbanShare" -> d.urbanShare
            "ruralShare" -> 1.0 - d.urbanShare
            "seniorShare" -> d.seniorShare
            "nonSeniorShare" -> 1.0 - d.seniorShare
            "incomeIndex" -> d.incomeIndex
            else -> 1.0
        }.coerceAtLeast(MIN_ATTRIBUTE)
    }
}
