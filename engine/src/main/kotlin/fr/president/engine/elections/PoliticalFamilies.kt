package fr.president.engine.elections

import fr.president.engine.data.PoliticalFamilyDef
import kotlin.math.hypot

/** Rattachement d'une personnalité à la famille politique la plus proche (axes économique et sociétal). */
object PoliticalFamilies {
    fun closest(families: List<PoliticalFamilyDef>, economic: Double, social: Double): PoliticalFamilyDef =
        families.minBy { hypot(it.economicPosition - economic, it.socialPosition - social) }

    fun distance(a: PoliticalFamilyDef, b: PoliticalFamilyDef): Double =
        hypot(a.economicPosition - b.economicPosition, a.socialPosition - b.socialPosition)
}
