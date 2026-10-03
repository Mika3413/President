package fr.president.engine.readout

import kotlinx.serialization.Serializable

/** Tonalité d'un indicateur, traduite en couleur par l'interface. */
@Serializable
enum class Tone { GOOD, NEUTRAL, WARNING, BAD }

@Serializable
data class ScaleStep(val upTo: Double, val label: String, val tone: Tone)

/** Échelles qualitatives (data/ui/readouts.json) : valeur interne -> libellé lisible. */
@Serializable
data class TraitLabel(val high: String, val low: String)

@Serializable
data class ReadoutsFile(
    val scales: Map<String, List<ScaleStep>>,
    val traitLabels: Map<String, TraitLabel> = emptyMap(),
    val domainLabels: Map<String, String> = emptyMap(),
) {
    fun domainLabel(domain: String): String = domainLabels[domain] ?: domain

    fun describe(scale: String, value: Double): ScaleStep {
        val steps = scales[scale] ?: error("Échelle inconnue : $scale")
        return steps.firstOrNull { value <= it.upTo } ?: steps.last()
    }
}
