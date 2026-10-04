package fr.president.engine.events

import kotlinx.serialization.Serializable

/**
 * Réponses supplémentaires communes à une famille d'événements : un président ne choisit pas
 * entre deux options. Devant un incendie, il peut aussi aller sur place, réquisitionner, demander
 * l'aide européenne, évacuer... Les options du paquet s'ajoutent à celles de l'événement, et ses
 * mesures de crise (confinement, plan ORSEC...) sont proposées en complément, cumulables.
 */
@Serializable
data class ResponsePack(
    val id: String,
    val label: String = "",
    val events: List<String>,
    val options: List<EventOptionDef> = emptyList(),
    val measures: List<String> = emptyList(),
)

@Serializable
data class ResponsesFile(val packs: List<ResponsePack>)

object ResponsePacks {
    /** Ajoute à chaque événement les options et mesures des paquets qui le citent. */
    fun merge(events: List<EventDefinition>, file: ResponsesFile?): List<EventDefinition> {
        if (file == null) return events
        return events.map { e ->
            val packs = file.packs.filter { e.id in it.events }
            if (packs.isEmpty()) return@map e
            val measures = packs.flatMap { it.measures }.distinct()
            val message = e.message?.let { m ->
                val known = m.options.map { it.id }.toMutableSet()
                val extra = packs.flatMap { it.options }.filter { known.add(it.id) }
                // Les options « reporter » et « plus d'infos » restent en dernier.
                val (late, main) = m.options.partition { it.requestDetails || it.reaskAfterDays != null }
                m.copy(options = main + extra + late)
            }
            e.copy(message = message, measures = measures)
        }
    }
}
