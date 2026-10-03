package fr.president.engine.events

/** Cible concrète d'un événement (département, ville, infrastructure, ministre, pays). */
data class ScopeRef(
    val type: EventScope,
    val id: String?,
    val senderId: String? = null,
)
