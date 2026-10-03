package fr.president.game.map

import com.badlogic.gdx.graphics.Color
import fr.president.engine.world.WorldState
import fr.president.game.ui.Theme

/** Couleurs de remplissage des départements selon la couche thématique. */
class MapStyle(private val data: MapData) {
    private val regionPalette = listOf(
        "5b7a8c", "6f8a6a", "8c7a5b", "7a6a8c", "5f8c84", "8c6a6f", "6a7f8c",
        "7f8c5f", "8c805f", "5f6f8c", "806a8c", "6a8c74", "8c745f",
    ).map { Color.valueOf(it) }
    private val regionColors = HashMap<String, Color>()
    private val tmp = Color()

    fun departmentColor(code: String, regionCode: String, layer: ThematicLayer, state: WorldState): Color {
        val d = state.territory.departments[code] ?: return Theme.france
        return when (layer) {
            ThematicLayer.OPINION -> Theme.heat(norm(d.approval, APPROVAL_LOW, APPROVAL_HIGH), tmp)
            ThematicLayer.UNEMPLOYMENT -> Theme.heat(1f - norm(d.unemployment, UNEMPLOYMENT_LOW, UNEMPLOYMENT_HIGH), tmp)
            ThematicLayer.INCOME -> Theme.heat(norm(d.incomeIndex, INCOME_LOW, INCOME_HIGH), tmp)
            ThematicLayer.POPULATION -> {
                val density = data.density[code] ?: 0f
                val v = (Math.log10(density.coerceAtLeast(1f).toDouble()) / MAX_DENSITY_LOG).toFloat().coerceIn(0f, 1f)
                tmp.set(Theme.france).lerp(Theme.accent, v)
            }
            ThematicLayer.HEALTH -> Theme.heat(norm(d.healthAccess, HEALTH_LOW, HEALTH_HIGH), tmp)
            ThematicLayer.SECURITY -> Theme.heat(1f - norm(d.crime, CRIME_LOW, CRIME_HIGH), tmp)
            ThematicLayer.POLLUTION -> Theme.heat(1f - norm(d.pollution, POLLUTION_LOW, POLLUTION_HIGH), tmp)
            ThematicLayer.INDUSTRY -> tmp.set(Theme.france).lerp(INDUSTRY_COLOR, norm(d.industryShare, 0.03, 0.27))
            ThematicLayer.AGRICULTURE -> tmp.set(Theme.france).lerp(AGRICULTURE_COLOR, norm(d.agricultureShare, 0.0, 0.1))
            ThematicLayer.ADMIN -> regionColor(regionCode)
            else -> tmp.set(regionColor(regionCode)).lerp(Theme.france, MUTED)
        }
    }

    fun regionColor(code: String): Color = regionColors.getOrPut(code) {
        val index = data.regions.indexOfFirst { it.id == code }.coerceAtLeast(0)
        Color(regionPalette[index % regionPalette.size])
    }

    private fun norm(v: Double, low: Double, high: Double) = ((v - low) / (high - low)).toFloat().coerceIn(0f, 1f)

    private companion object {
        const val APPROVAL_LOW = 0.35
        const val APPROVAL_HIGH = 0.62
        const val UNEMPLOYMENT_LOW = 0.05
        const val UNEMPLOYMENT_HIGH = 0.12
        const val INCOME_LOW = 0.8
        const val INCOME_HIGH = 1.3
        const val MAX_DENSITY_LOG = 4.4
        const val MUTED = 0.6f
        const val HEALTH_LOW = 0.6
        const val HEALTH_HIGH = 1.4
        const val CRIME_LOW = 0.5
        const val CRIME_HIGH = 1.8
        const val POLLUTION_LOW = 0.5
        const val POLLUTION_HIGH = 1.7
        val INDUSTRY_COLOR: Color = Color.valueOf("b08968")
        val AGRICULTURE_COLOR: Color = Color.valueOf("8fbf5a")
    }
}
