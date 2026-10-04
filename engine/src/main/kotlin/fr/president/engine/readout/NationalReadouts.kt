package fr.president.engine.readout

import fr.president.engine.data.TaxPayer
import fr.president.engine.economy.BudgetCalculator
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/** Indicateurs nationaux : on cache la complexité sans la supprimer. */
class NationalReadouts(private val ctx: SimulationContext) {
    private val scales = ctx.db.readouts
    private val e get() = ctx.state.playerCountry.economy

    fun all(): List<Indicator> = listOf(approval(), growth(), unemployment(), inflation(), debt(), energy(), parliament(), military(), demography())

    fun demography(): Indicator {
        val d = ctx.state.demography
        val pop = ctx.state.playerCountry.population
        val net = d.birthsLastYear - d.deathsLastYear + d.netMigrationLastYear
        return Indicator("Population", Formatting.population(pop), Tone.NEUTRAL,
            if (net >= 0) "La population augmente de ${Formatting.integer(net)} habitants par an." else "La population diminue.",
            listOf(
                "Naissances (rythme annuel)" to Formatting.integer(d.birthsLastYear),
                "Décès (rythme annuel)" to Formatting.integer(d.deathsLastYear),
                "Solde migratoire (rythme annuel)" to Formatting.integer(d.netMigrationLastYear),
                "Politique migratoire" to Formatting.percent(d.immigrationFactor) + " de la tendance",
            ))
    }

    fun debt(): Indicator {
        val rising = e.deficitBillions > 0
        val months = e.deficitRatioHistory.all().reversed().takeWhile { it > 0 }.size
        val explanation = if (rising) {
            if (months > 0) "Dépenses supérieures aux recettes depuis $months mois." else "Les dépenses dépassent les recettes."
        } else "Les recettes couvrent les dépenses : la dette se réduit."
        val ratio = scales.describe("debtRatio", e.debtRatio)
        return Indicator(
            "Dette publique", if (rising) "EN HAUSSE" else "EN BAISSE", if (rising) ratio.tone else Tone.GOOD, explanation,
            listOf(
                "Dette" to Formatting.billions(e.debtBillions),
                "Dette / PIB" to Formatting.percent(e.debtRatio),
                "Niveau" to ratio.label,
                "Déficit annuel" to Formatting.billions(e.deficitBillions) + " (" + Formatting.percent(e.deficitRatio) + " du PIB)",
                "Recettes annuelles" to Formatting.billions(e.revenueBillions),
                "Dépenses (hors intérêts)" to Formatting.billions(e.spendingBillions),
                "Charge des intérêts" to Formatting.billions(e.interestBillions),
                "Taux moyen de la dette" to Formatting.percent(e.averageDebtRate),
                "Taux de marché" to Formatting.percent(e.marketRate),
            ),
            value = Formatting.wholePercent(e.debtRatio) + " PIB",
            trend = if (rising) 1 else -1,
            higherIsBetter = false,
        )
    }

    /** Solde budgétaire annuel, en part du PIB (négatif = déficit). */
    fun deficit(): Indicator {
        val balance = -e.deficitRatio
        val tone = when {
            balance >= -DEFICIT_TARGET -> Tone.GOOD
            balance >= -DEFICIT_WARNING -> Tone.WARNING
            else -> Tone.BAD
        }
        return Indicator("Budget", if (balance >= 0) "EXCÉDENT" else "DÉFICIT", tone,
            "Objectif européen : déficit sous 3 % du PIB.",
            listOf("Recettes annuelles" to Formatting.billions(e.revenueBillions), "Dépenses (hors intérêts)" to Formatting.billions(e.spendingBillions),
                "Charge des intérêts" to Formatting.billions(e.interestBillions), "Solde" to Formatting.billions(-e.deficitBillions)),
            value = Formatting.signedPercent(balance),
            trend = trendOf(e.deficitRatioHistory.ago(1), e.deficitRatioHistory.ago(0), TREND_EPSILON))
    }

    fun growth(): Indicator {
        val s = scales.describe("growth", e.realGrowth)
        val trend = trendText(e.growthHistory.ago(0), e.growthHistory.ago(3), "s'accélère", "ralentit", "est stable")
        return Indicator("Croissance", s.label, s.tone, "L'activité $trend.", listOf(
            "Croissance annualisée" to Formatting.signedPercent(e.realGrowth),
            "Croissance potentielle" to Formatting.percent(e.potentialGrowth),
            "PIB nominal" to Formatting.billions(e.gdpBillions),
            "Confiance des ménages" to scales.describe("confidence", e.consumerConfidence).label,
            "Confiance des entreprises" to scales.describe("confidence", e.businessConfidence).label,
        ), value = Formatting.signedPercent(e.realGrowth), trend = trendOf(e.growthHistory.ago(0), e.growthHistory.ago(1), TREND_EPSILON))
    }

    fun unemployment(): Indicator {
        val s = scales.describe("unemployment", e.unemployment)
        val trend = trendText(e.unemploymentHistory.ago(0), e.unemploymentHistory.ago(3), "augmente", "recule", "est stable")
        return Indicator("Chômage", s.label, s.tone, "Le chômage $trend depuis 3 mois.", listOf(
            "Taux de chômage" to Formatting.percent(e.unemployment),
            "Taux structurel estimé" to Formatting.percent(e.naturalUnemployment),
            "Salaire net moyen" to Formatting.integer(e.averageNetMonthlyWage) + " €/mois",
        ), value = Formatting.percent(e.unemployment), trend = trendOf(e.unemploymentHistory.ago(0), e.unemploymentHistory.ago(1), TREND_EPSILON),
            higherIsBetter = false)
    }

    fun inflation(): Indicator {
        val s = scales.describe("inflation", e.inflation)
        return Indicator("Prix", s.label, s.tone, "Inflation annuelle de ${Formatting.percent(e.inflation)}.", listOf(
            "Inflation" to Formatting.percent(e.inflation),
            "Objectif" to Formatting.percent(e.inflationTarget),
            "Indice des prix de l'énergie" to Formatting.amount(e.energyPriceIndex * PERCENT),
            "Pouvoir d'achat (indice)" to Formatting.amount(e.wageIndex / e.priceLevel * PERCENT),
            "Pression fiscale ménages" to Formatting.percent(BudgetCalculator.taxBurden(e, TaxPayer.HOUSEHOLDS)) + " du PIB",
        ), value = Formatting.percent(e.inflation), trend = trendOf(e.inflationHistory.ago(0), e.inflationHistory.ago(1), TREND_EPSILON),
            higherIsBetter = false)
    }

    fun approval(): Indicator {
        val o = ctx.state.opinion
        val s = scales.describe("approval", o.nationalApproval)
        val def = ctx.playerData.socialGroups!!
        val worst = def.factors.minByOrNull { o.factorScores[it.id] ?: 0.0 }
        val best = def.factors.maxByOrNull { o.factorScores[it.id] ?: 0.0 }
        val explanation = buildString {
            if (worst != null && (o.factorScores[worst.id] ?: 0.0) < 0) append("Principal mécontentement : ${worst.label.lowercase()}. ")
            if (best != null && (o.factorScores[best.id] ?: 0.0) > 0) append("Point d'appui : ${best.label.lowercase()}.")
        }.trim()
        return Indicator("Opinion", s.label, s.tone, explanation,
            def.groups.map { g -> g.label to scales.describe("approval", o.groups[g.id]?.effective ?: g.baseApproval).label } +
                ("Approbation estimée" to Formatting.percent(o.nationalApproval)),
            value = Formatting.wholePercent(o.nationalApproval), trend = trendOf(o.nationalApproval, o.history.ago(1), APPROVAL_EPSILON))
    }

    fun energy(): Indicator {
        val en = ctx.state.energy
        val s = scales.describe("energyMargin", en.margin)
        return Indicator("Électricité", s.label, s.tone,
            if (en.netExportTWh >= 0) "Le pays produit plus qu'il ne consomme." else "Le pays doit importer de l'électricité.",
            listOf(
                "Production annuelle" to Formatting.integer(en.productionTWh) + " TWh",
                "Consommation annuelle" to Formatting.integer(en.demandTWh) + " TWh",
                "Exportations contractuelles" to Formatting.integer(en.committedExportTWh) + " TWh",
                "Indice des prix" to Formatting.amount(en.priceIndex * PERCENT),
            ), value = (if (en.netExportTWh >= 0) "+" else "") + Formatting.integer(en.netExportTWh) + " TWh")
    }

    fun parliament(): Indicator {
        val support = ctx.state.government.parliamentSupport
        val s = scales.describe("support", support)
        val threshold = ctx.playerData.government!!.parliament.passThreshold
        val assembly = fr.president.engine.government.ParliamentService(ctx)
        val details = mutableListOf("Soutien estimé" to Formatting.percent(support))
        if (assembly.isActive) {
            details += "Assemblée" to assembly.majorityStatus().label
            details += "Groupe présidentiel" to "${ctx.state.parliament.seats[assembly.presidentFamily().id] ?: 0} sièges sur ${assembly.totalSeats()}"
            ctx.state.parliament.nextLegislative?.let { details += "Prochaines législatives" to Formatting.date(it) }
            val senate = fr.president.engine.government.SenateService(ctx)
            if (senate.isActive) details += "Sénat" to senate.majorityLabel()
        }
        return Indicator("Parlement", s.label, s.tone,
            if (support >= threshold) "Vos textes ont de bonnes chances d'être adoptés." else "L'adoption de vos textes est incertaine.",
            details, value = Formatting.wholePercent(support))
    }

    fun military(): Indicator {
        val m = ctx.state.military
        val s = scales.describe("readiness", m.overallReadiness)
        val troubled = m.units.values.count { it.readiness < TROUBLED_READINESS }
        return Indicator("Préparation militaire", s.label, s.tone,
            if (troubled > 0) "$troubled unité(s) connaissent des problèmes de disponibilité." else "Les forces sont globalement disponibles.",
            listOf("Disponibilité moyenne" to Formatting.percent(m.overallReadiness), "Unités" to m.units.size.toString()),
            value = Formatting.wholePercent(m.overallReadiness))
    }

    fun services(): List<Indicator> = ctx.state.playerCountry.services.map { (domain, q) ->
        val s = scales.describe("quality", q)
        val funding = BudgetCalculator.realFundingRatio(e, domain)
        Indicator(ctx.db.readouts.domainLabel(domain), s.label, s.tone,
            funding?.let { if (it < 1.0 - FUNDING_ALERT) "Moyens réels en baisse." else if (it > 1.0 + FUNDING_ALERT) "Moyens réels en hausse." else "Moyens stables." } ?: "",
            listOfNotNull("Qualité" to Formatting.percent(q), funding?.let { "Financement réel" to Formatting.percent(it) }))
    }

    private fun trendText(now: Double?, before: Double?, up: String, down: String, flat: String): String {
        if (now == null || before == null) return flat
        return when {
            now - before > TREND_EPSILON -> up
            before - now > TREND_EPSILON -> down
            else -> flat
        }
    }

    private companion object {
        const val PERCENT = 100.0
        const val TREND_EPSILON = 0.001
        const val APPROVAL_EPSILON = 0.004
        const val DEFICIT_TARGET = 0.03
        const val DEFICIT_WARNING = 0.05
        const val TROUBLED_READINESS = 0.5
        const val FUNDING_ALERT = 0.02
    }
}
