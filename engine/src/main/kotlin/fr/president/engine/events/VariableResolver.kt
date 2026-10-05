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
            "military" -> military(parts.getOrNull(1))
            // « measure.lockdown » : 1 si la mesure est en vigueur (n'importe où).
            "measure" -> if (ctx.state.measures.active.any { it.id == parts.getOrNull(1) }) 1.0 else 0.0
            "derived" -> derived(parts.getOrNull(1))
            "lever" -> fr.president.engine.legislation.LeverService(ctx).let { l -> key.removePrefix("lever.").takeIf { l.lever(it) != null }?.let { l.current(it) } }
            "spending" -> ctx.state.playerCountry.economy.budget?.spending?.get(parts.getOrNull(1))?.policyFactor
            "funding" -> parts.getOrNull(1)?.let { BudgetCalculator.realFundingRatio(ctx.state.playerCountry.economy, it) }
            "demography" -> if (parts.getOrNull(1) == "immigration") ctx.state.demography.immigrationFactor else null
            "laws" -> fr.president.engine.government.LawService(ctx).indices().let { i ->
                when (parts.getOrNull(1)) { "liberty" -> i.liberty; "press" -> i.press; "rule" -> i.rule; else -> null }
            }
            "unrest" -> when (parts.getOrNull(1)) {
                "phase" -> ctx.state.unrest.movements.maxOfOrNull { it.phase.ordinal + 1.0 } ?: 0.0
                "crowd" -> ctx.state.unrest.movements.sumOf { it.crowd }
                "armyLoyalty" -> ctx.state.unrest.armyLoyalty.takeIf { it >= 0 } ?: (ctx.db.unrest?.armyLoyalty ?: 0.8)
                else -> null
            }
            "society" -> ctx.state.society.let { so ->
                when (parts.getOrNull(1)) {
                    "rentIndex" -> so.rentIndex
                    // Loyers corrigés de l'inflation : ce qui pèse vraiment sur le budget des ménages.
                    "realRent" -> so.rentIndex / ctx.state.playerCountry.economy.priceLevel
                    "housePrices" -> so.housePriceIndex / ctx.state.playerCountry.economy.priceLevel
                    "fertility" -> so.fertility
                    "lifeExpectancy" -> so.lifeExpectancy
                    "savingsRate" -> so.savingsRate
                    "informal" -> so.informal
                    else -> null
                }
            }
            "intel" -> if (parts.getOrNull(1) == "capacity") ctx.state.intel.capacity.takeIf { it >= 0 } else null
            "season" -> if (parts.getOrNull(1) == "month") ctx.now.month.toDouble() else null
            "scope" -> scoped(parts.getOrNull(1), scope)
            "president" -> ctx.state.characters[ctx.state.player.presidentId]?.let { p ->
                when (parts.getOrNull(1)) {
                    "scandals" -> p.scandals.toDouble()
                    null -> null
                    else -> p.trait(parts[1])
                }
            }
            else -> null
        }
    }

    /** Grandeurs calculées qui servent aux seuils de conséquences et aux conditions des décisions. */
    private fun derived(field: String?): Double? = when (field) {
        // Nation en danger : insurrection en cours ou territoire national occupé.
        "nationInDanger" -> {
            val geo = fr.president.engine.military.Geopolitics(ctx)
            val insurrection = ctx.state.unrest.movements.any { it.phase == fr.president.engine.politics.MovementPhase.INSURRECTION }
            val invaded = ctx.state.military.occupied.any { (zone, _) -> geo.ownerOf(zone) == ctx.state.player.countryId }
            if (insurrection || invaded) 1.0 else 0.0
        }
        "invaded" -> {
            val geo = fr.president.engine.military.Geopolitics(ctx)
            if (ctx.state.military.occupied.any { (zone, _) -> geo.ownerOf(zone) == ctx.state.player.countryId }) 1.0 else 0.0
        }
        "article16" -> if (fr.president.engine.legislation.LegislationService(ctx).article16Active()) 1.0 else 0.0
        // RSA rapporté au SMIC net : au-delà de ~85 %, reprendre un emploi ne rapporte presque plus rien.
        "rsaToSmic" -> {
            val l = fr.president.engine.legislation.LeverService(ctx)
            val rsa = l.lever("param:rsa_amount")?.let { l.current(it.id) } ?: return null
            val boost = l.lever("param:smic_boost")?.let { l.current(it.id) } ?: 0.0
            rsa / (fr.president.engine.consequences.ConsequenceService.SMIC_NET * (1 + boost / 100))
        }
        // Baisse moyenne des crédits des services (1 = budget de départ, 0,7 = −30 %).
        "servicesFunding" -> ctx.state.playerCountry.economy.budget?.spending?.values?.filter { it.domain != null && it.domain != "pensions" }
            ?.takeIf { it.isNotEmpty() }?.let { items -> items.sumOf { it.policyFactor } / items.size }
        "policeFunding" -> ctx.state.playerCountry.economy.budget?.spending?.get("police")?.policyFactor
        else -> null
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

    private fun military(field: String?): Double? {
        val geo = fr.president.engine.military.Geopolitics(ctx)
        return when (field) {
            "readiness" -> ctx.state.military.overallReadiness
            "warWeariness" -> ctx.state.military.warWeariness
            "atWar" -> if (geo.isAtWar(ctx.state.player.countryId)) 1.0 else 0.0
            "ammunitionStock" -> ctx.state.military.stocks.ammunition
            // Guerre active impliquant un pays européen (réfugiés, inquiétude, prix).
            "nearbyWar" -> if (geo.activeWars().any { w -> w.participants.any { it in NEARBY } }) 1.0 else 0.0
            // Guerre en cours dans laquelle la France n'est pas engagée (sujet de résolutions à l'ONU).
            "foreignWar" -> if (geo.mainWarWithout(ctx.state.player.countryId) != null) 1.0 else 0.0
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
                "fisheryNeighbor" -> if (ctx.db.country(scope.id).definition.strategic.fisheryNeighbor) 1.0 else 0.0
                // « hazard_earthquake » : 1 si le pays est exposé à ce risque naturel.
                else -> if (field.startsWith("hazard_")) {
                    if (field.removePrefix("hazard_") in ctx.db.country(scope.id).definition.strategic.hazards) 1.0 else 0.0
                } else null
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
            "mediterranean" -> if (d.region in MEDITERRANEAN_REGIONS) 1.0 else 0.0
            "overseas" -> if (ctx.playerData.territory?.departments?.firstOrNull { it.code == d.code }?.overseas == true) 1.0 else 0.0
            "healthAccess" -> d.healthAccess
            "crime" -> d.crime
            "industryShare" -> d.industryShare
            "agricultureShare" -> d.agricultureShare
            "pollution" -> d.pollution
            "coastal" -> if (d.code in COASTAL || d.code.length == 3) 1.0 else 0.0
            "mountain" -> if (d.code in MOUNTAIN) 1.0 else 0.0
            "border" -> if (d.code in BORDER) 1.0 else 0.0
            "wine" -> if (d.code in WINE) 1.0 else 0.0
            "tourist" -> if (d.code in TOURIST_DEPTS) 1.0 else 0.0
            "regionalLanguage" -> if (d.code in REGIONAL_LANGUAGE) 1.0 else 0.0
            "nuclear" -> infraIn(d.code, "NUCLEAR_PLANT")
            "port" -> infraIn(d.code, "PORT")
            "airport" -> infraIn(d.code, "AIRPORT")
            else -> null
        }
    }

    private fun infraIn(code: String, type: String) = if (ctx.catalog.items.values.any { it.department == code && it.type == type }) 1.0 else 0.0

    private companion object {
        val COASTAL = setOf("06", "11", "13", "14", "17", "2A", "2B", "22", "29", "30", "33", "34", "35", "40", "44", "50", "56", "59", "62", "64", "66", "76", "80", "83", "85")
        val MOUNTAIN = setOf("01", "04", "05", "06", "09", "12", "15", "19", "25", "26", "38", "39", "43", "48", "63", "64", "65", "66", "68", "73", "74", "88", "90", "2A", "2B")
        val BORDER = setOf("01", "02", "04", "05", "06", "08", "09", "25", "31", "39", "54", "55", "57", "59", "64", "65", "66", "67", "68", "73", "74", "90", "973")
        val WINE = setOf("11", "13", "16", "17", "21", "24", "30", "33", "34", "37", "41", "44", "47", "49", "51", "67", "68", "69", "71", "84")
        val TOURIST_DEPTS = setOf("06", "13", "17", "29", "2A", "2B", "30", "33", "34", "56", "64", "66", "73", "74", "75", "83", "84", "85")
        val REGIONAL_LANGUAGE = setOf("22", "29", "35", "56", "64", "66", "67", "68", "2A", "2B")
        val NEARBY = setOf("DEU", "ESP", "ITA", "GBR", "BEL", "NLD", "CHE", "PRT", "AUT", "POL", "SWE", "NOR", "GRC", "ROU", "UKR", "RUS", "BLR", "TUR", "DZA", "MAR", "TUN")
        const val MILLION = 1_000_000.0
        val MEDITERRANEAN_REGIONS = setOf("93", "94", "76")
        const val MW_PER_GW = 1000.0
    }
}
