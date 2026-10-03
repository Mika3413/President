package fr.president.engine.diplomacy

import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class Sanction(val by: String, val target: String, val since: WorldTime)
