package fr.president.engine.dialogue

import kotlinx.serialization.Serializable

@Serializable
data class DialogueFile(val templates: List<DialogueTemplate>)

/**
 * Modèle de message composé de sections (INTRO, CONTEXTE, PROBLÈME, DEMANDE, CONCLUSION...).
 * Chaque section possède de nombreuses variantes filtrées par des étiquettes de contexte.
 */
@Serializable
data class DialogueTemplate(
    val id: String,
    val subject: List<DialogueVariant>,
    val sections: List<DialogueSection>,
)

@Serializable
data class DialogueSection(
    val id: String,
    /** Section facultative : omise si aucune variante ne correspond (ou tirage négatif). */
    val optional: Boolean = false,
    val chance: Double = 1.0,
    val variants: List<DialogueVariant>,
)

@Serializable
data class DialogueVariant(
    val text: String,
    /** Étiquettes toutes requises (ex. "history:refused", "trait:aggressive"). */
    val `when`: List<String> = emptyList(),
    /** Étiquettes interdites. */
    val unless: List<String> = emptyList(),
    val weight: Double = 1.0,
)

/** Synonymes insérés via la syntaxe [[mot]] pour multiplier les variantes. */
@Serializable
data class Lexicon(val words: Map<String, List<String>>)
