package fr.president.engine.save

import fr.president.engine.world.WorldState
import kotlinx.serialization.Serializable

/** Enveloppe de sauvegarde versionnée. */
@Serializable
class SaveFile(
    val formatVersion: Int,
    /** Heure réelle UTC de l'écriture : sert au rattrapage au retour du joueur. */
    val savedAtRealUtcMillis: Long,
    val gameVersion: String,
    val state: WorldState,
) {
    companion object {
        /** À incrémenter à chaque changement incompatible, avec une migration associée. */
        const val CURRENT_FORMAT = 1
    }
}
