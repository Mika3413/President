package fr.president.engine.events

import fr.president.engine.data.TaxPayer
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.economy.BudgetCalculator
import fr.president.engine.simulation.SimulationContext

/**
 * Traduit une clé textuelle des données (ex. "economy.unemployment", "scope.approval")
 * en valeur numérique de la simulation. C'est le pont entre données et moteur.
 */
class VariableResolver(private val ctx: SimulationContext) {

    fun resolve(key: String, scope: ScopeRef? = null): Double? {
        val parts = key.split('.')
        return when (parts[0]) {
            "economy" -> economy(parts.getOrNull(1))
            "energy" -> energy(parts.getOrNull(1))
            "opinion" -> opinion(parts)
            "quality" -> ctx.state.playerCountry.services[parts.getOrNull(1)]
            "government" -> if (parts.getOrNull(1) == "parliamentSupport") ctx.state.government.parliamentSupport else null
            "tax" -> when (parts.getOrNull(1)) {
                "households" -> BudgetCalculator.taxBurden(ctx.state.playerCountry.economy, TaxPayer.HOUSEHOLDS)
                "businesses" -> BudgetCalculator.taxBurden(ctx.state.playerCountry.economy, TaxPayer.BUSINESSES)
                else -> null
            }
            "military" -> if (parts.getOrNull(1) == "readiness") ctx.state.military.overallReadiness else null
            "season" -> if (parts.getOrNull(1) == "month") ctx.now.month.toDouble() else null
            "scope" -> scoped(parts.getOrNull(1), scope)
            else -> null
        }
    }

    private fun economy(field: String?): Double? {
        val e = ctx.state.playerCountry.economy
        return when (field) {
            "unemployment" -> e.unemployment
            "inflation" -> e.inflation
            "growth" -> e.realGrowth
            "consumerConfidence" -> e.consumerConfidence
            "businessConfidence" -> e.businessConfidence
            "debtRatio" -> e.debtRatio
            "deficitRatio" -> e.deficitRatio
            "energyPriceIndex" -> e.energyPriceIndex
            "purchasingPower" -> e.wageIndex / e.priceLevel
            else -> null
        }
    }

    private fun energy(field: String?): Double? = when (field) {
        "margin" -> ctx.state.energy.margin
        "priceIndex" -> ctx.state.energy.priceIndex
        else -> null
    }

    private fun opinion(parts: List<String>): Double? = when (parts.getOrNull(1)) {
        "national" -> ctx.state.opinion.nationalApproval
        "group" -> ctx.state.opinion.groups[parts.getOrNull(2)]?.effective
        else -> null
    }

    private fun scoped(field: String?, scope: ScopeRef?): Double? {
        if (scope?.id == null || field == null) return null
        val territory = ctx.state.territory
        return when (scope.type) {
            EventScope.DEPARTMENT -> department(territory.departments[scope.id]?.code, field)
            EventScope.CITY -> {
                val city = territory.cities[scope.id] ?: return null
                when (field) {
                    "populationMillions" -> city.population / MILLION
                    "satisfaction" -> city.satisfaction
                    else -> department(city.department, field)
                }
            }
            EventScope.INFRASTRUCTURE -> {
                val infra = ctx.state.infrastructure[scope.id] ?: return null
                val def = ctx.catalog.item(scope.id)
                when (field) {
                    "condition" -> infra.condition
                    "maintenance" -> infra.maintenanceLevel
                    "capacityGW" -> (def?.capacityMW ?: 0.0) / MW_PER_GW
                    else -> department(def?.department, field)
                }
            }
            EventScope.MINISTER -> {
                val c = ctx.state.characters[scope.id] ?: return null
                when (field) {
                    "integrity" -> c.trait(fr.president.engine.politics.Traits.INTEGRITY)
                    "loyalty" -> c.loyalty
                    "competence" -> c.competence
                    "popularity" -> c.popularity
                    else -> null
                }
            }
            EventScope.FOREIGN_COUNTRY -> when (field) {
                "relation" -> RelationCalculator(ctx).score(scope.id, ctx.state.player.countryId)
                "electricityBalance" -> ctx.state.countries[scope.id]?.electricityBalanceTWh
                else -> null
            }
            EventScope.NATIONAL -> null
        }
    }

    private fun department(code: String?, field: String): Double? {
        val d = ctx.state.territory.departments[code ?: return null] ?: return null
        return when (field) {
            "approval" -> d.approval
            "unemployment" -> d.unemployment
            "incomeIndex" -> d.incomeIndex
            "urbanShare" -> d.urbanShare
            "seniorShare" -> d.seniorShare
            "populationMillions" -> d.population / MILLION
            else -> null
        }
    }

    private companion object {
        const val MILLION = 1_000_000.0
        const val MW_PER_GW = 1000.0
    }
}
