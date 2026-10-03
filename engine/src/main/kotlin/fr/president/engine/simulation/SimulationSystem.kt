package fr.president.engine.simulation

/** Fréquence d'exécution d'un système, en temps du monde. */
enum class Cadence { HOURLY, DAILY, MONTHLY }

/** Un système de simulation indépendant : il lit et modifie l'état via le contexte. */
interface SimulationSystem {
    val name: String
    val cadence: Cadence
    fun run(ctx: SimulationContext)
}
