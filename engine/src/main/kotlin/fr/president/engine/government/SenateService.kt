package fr.president.engine.government

import fr.president.engine.data.EuropeanElectionDef
import fr.president.engine.data.SenateDef
import fr.president.engine.elections.Candidate
import fr.president.engine.elections.ElectionSimulator
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting
import fr.president.engine.util.clamp01
import kotlin.math.pow

/**
 * Sénat (élu par les élus locaux, renouvelé par moitié) et élections européennes.
 * Le Sénat ne bloque pas : un rejet impose une navette qui retarde la mise en œuvre des réformes.
 */
class SenateService(private val ctx: SimulationContext) {
    private val elections = ctx.playerData.elections
    val senate: SenateDef? = elections?.senate
    val european: EuropeanElectionDef? = elections?.european
    private val state get() = ctx.state.parliament
    private val assembly = ParliamentService(ctx)

    fun ensure() {
        senate?.let { def ->
            if (state.senateSeats.isEmpty()) state.senateSeats.putAll(def.initialSeats)
            if (state.nextSenateRenewal == null) {
                val at = rollForward(WorldTime.parse(def.firstRenewal), def.renewalYears)
                state.nextSenateRenewal = at
                ctx.scheduler.schedule(ScheduledAction.SenateRenewal(at))
            }
        }
        european?.let { def ->
            if (state.nextEuropean == null) {
                val at = rollForward(WorldTime.parse(def.firstDate), def.termYears)
                state.nextEuropean = at
                ctx.scheduler.schedule(ScheduledAction.EuropeanElection(at))
            }
        }
    }

    private fun rollForward(first: WorldTime, years: Int): WorldTime {
        var at = first
        while (at < ctx.now) at = at.plusYears(years)
        return at
    }

    val isActive: Boolean get() = senate != null && state.senateSeats.isNotEmpty()

    fun totalSeats(): Int = state.senateSeats.values.sum()

    /** Soutien du Sénat au gouvernement : sièges pondérés par la proximité de chaque groupe. */
    fun support(): Double {
        val total = totalSeats().takeIf { it > 0 } ?: return 0.0
        return assembly.families.sumOf { (state.senateSeats[it.id] ?: 0) * assembly.familySupport(it) } / total
    }

    fun majorityLabel(): String {
        val own = assembly.presidentFamily()
        val bloc = assembly.families.filter { assembly.affinity(it, own) >= BLOC_SUPPORT }.sumOf { state.senateSeats[it.id] ?: 0 }
        return if (bloc * 2 > totalSeats()) "Sénat favorable" else "Sénat d'opposition"
    }

    /**
     * Examen d'une réforme adoptée par l'Assemblée. Renvoie le retard de mise en œuvre (en jours) :
     * 0 si le Sénat l'adopte conforme, la durée de la navette sinon.
     */
    fun reviewReform(title: String): Int {
        val def = senate ?: return 0
        if (!isActive) return 0
        val vote = support() + ctx.rng.nextGaussian() * def.voteNoise
        if (vote >= def.passThreshold) {
            ctx.notifications.news(NotificationCategory.POLITICS, "Le Sénat adopte la réforme sans modification : $title")
            return 0
        }
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Le Sénat rejette la réforme",
            "$title : après une navette parlementaire, l'Assemblée a eu le dernier mot. La réforme est adoptée, mais sa mise en œuvre prend ${def.navetteDelayDays} jours de retard.")
        return def.navetteDelayDays
    }

    // ---- Renouvellement sénatorial ----

    /** Composition du collège des grands électeurs, déduite des exécutifs locaux. */
    fun electorate(): Map<String, Double> {
        val def = senate ?: return emptyMap()
        val weights = mutableMapOf<String, Double>()
        val t = ctx.state.territory
        val cityPop = t.cities.values.sumOf { it.population }.toDouble().coerceAtLeast(1.0)
        fun add(characterId: String?, w: Double) {
            val c = characterId?.let { ctx.state.characters[it] } ?: return
            weights.merge(assembly.familyOf(c).id, w, Double::plus)
        }
        t.cities.values.forEach { add(it.mayorId, def.mayorWeight * it.population / cityPop) }
        t.departments.values.forEach { add(it.presidentId, def.departmentWeight / t.departments.size) }
        t.regions.values.forEach { add(it.presidentId, def.regionWeight / t.regions.size.coerceAtLeast(1)) }
        val sum = weights.values.sum().takeIf { it > 0 } ?: return emptyMap()
        return weights.mapValues { it.value / sum }
    }

    fun renew(at: WorldTime) {
        val def = senate ?: return
        if (state.nextSenateRenewal != at || ctx.state.player.gameOver != null) return
        val composition = electorate()
        val own = assembly.presidentFamily()
        val blocBefore = blocSeats(own.id)
        val renewed = (def.seats * def.renewedShare).toInt()
        val kept = state.senateSeats.mapValues { (it.value * (1 - def.renewedShare)) }
        val newSeats = allocate(composition, renewed)
        val total = mutableMapOf<String, Double>()
        kept.forEach { (k, v) -> total.merge(k, v, Double::plus) }
        newSeats.forEach { (k, v) -> total.merge(k, v.toDouble(), Double::plus) }
        state.senateSeats.clear()
        state.senateSeats.putAll(roundTo(total, def.seats))
        val gained = blocSeats(own.id) - blocBefore
        state.senateResults += SenateResult(ctx.now, state.senateSeats.toMap(), gained)
        val next = at.plusYears(def.renewalYears)
        state.nextSenateRenewal = next
        ctx.scheduler.schedule(ScheduledAction.SenateRenewal(next))
        val trend = when {
            gained > 0 -> "votre camp gagne $gained sièges"
            gained < 0 -> "votre camp perd ${-gained} sièges"
            else -> "l'équilibre est inchangé"
        }
        ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.IMPORTANT, "Élections sénatoriales",
            "La moitié du Sénat a été renouvelée par les grands électeurs : $trend. ${majorityLabel()}.")
        ctx.log("parliament", "Sénatoriales : ${state.senateSeats}, gain du bloc $gained")
    }

    private fun blocSeats(ownId: String): Int {
        val own = assembly.families.first { it.id == ownId }
        return assembly.families.filter { assembly.affinity(it, own) >= BLOC_SUPPORT }.sumOf { state.senateSeats[it.id] ?: 0 }
    }

    private fun allocate(shares: Map<String, Double>, seats: Int): Map<String, Int> {
        val weights = shares.mapValues { it.value.pow(SENATE_AMPLIFICATION) }
        val sum = weights.values.sum().takeIf { it > 0 } ?: return emptyMap()
        return roundTo(weights.mapValues { it.value / sum * seats }, seats)
    }

    /** Arrondi aux plus forts restes pour obtenir exactement [seats] sièges. */
    private fun roundTo(values: Map<String, Double>, seats: Int): Map<String, Int> {
        val base = values.mapValues { it.value.toInt() }.toMutableMap()
        var remaining = seats - base.values.sum()
        for (id in values.entries.sortedByDescending { it.value - it.value.toInt() }.map { it.key }) {
            if (remaining <= 0) break
            base[id] = base.getValue(id) + 1
            remaining--
        }
        return base
    }

    // ---- Élections européennes ----

    fun runEuropean(at: WorldTime) {
        val def = european ?: return
        if (state.nextEuropean != at || ctx.state.player.gameOver != null) return
        val own = assembly.presidentFamily()
        val president = ctx.state.characters.getValue(ctx.state.player.presidentId)
        val bonusLoss = (elections?.incumbentBonus ?: 0.0) * (1.0 - EUROPEAN_INCUMBENT_SHARE)
        val candidates = assembly.families.map { f ->
            val incumbent = f.id == own.id
            Candidate(if (incumbent) president.id else f.id, f.id, incumbent, f.economicPosition, f.socialPosition,
                momentum = if (incumbent) -bonusLoss else 0.0)
        }
        val result = ElectionSimulator(ctx).simulate(candidates, elections!!.pollNoise)
        val votes = candidates.associate { it.familyId to (result.shares[it.characterId] ?: 0.0) }
        val eligible = votes.filter { it.value >= EUROPEAN_THRESHOLD }
        val seats = roundTo(eligible.mapValues { it.value / eligible.values.sum() * def.seats }, def.seats)
        val turnout = (result.turnout * def.turnoutFactor).clamp01()
        state.europeanResults += EuropeanResult(ctx.now, votes, seats, turnout)
        val next = at.plusYears(def.termYears)
        state.nextEuropean = next
        ctx.scheduler.schedule(ScheduledAction.EuropeanElection(next))
        val ownShare = votes[own.id] ?: 0.0
        val winner = assembly.families.maxBy { votes[it.id] ?: 0.0 }
        val verdict = when {
            ownShare >= def.goodScore -> {
                ctx.state.government.parliamentSupport = (ctx.state.government.parliamentSupport + def.supportSwing).clamp01()
                ctx.state.opinion.groups.values.forEach { it.shock += def.opinionSwing }
                "Un succès qui conforte la majorité."
            }
            ownShare < def.badScore -> {
                ctx.state.government.parliamentSupport = (ctx.state.government.parliamentSupport - def.supportSwing).clamp01()
                ctx.state.opinion.groups.values.forEach { it.shock -= def.opinionSwing }
                "Un revers sévère : la majorité s'interroge, certains réclament une dissolution."
            }
            else -> "Un résultat mitigé."
        }
        val ranking = assembly.families.sortedByDescending { votes[it.id] ?: 0.0 }.take(TOP_LISTS)
            .joinToString(", ") { "${it.name} ${Formatting.percent(votes[it.id] ?: 0.0)}" }
        ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.IMPORTANT, "Élections européennes : ${winner.name} en tête",
            "Participation ${Formatting.percent(turnout)}. $ranking. Votre liste obtient ${Formatting.percent(ownShare)}. $verdict")
        ctx.log("parliament", "Européennes : $votes")
    }

    private companion object {
        const val BLOC_SUPPORT = 0.5
        const val SENATE_AMPLIFICATION = 1.2
        const val EUROPEAN_THRESHOLD = 0.05
        /** Aux européennes, le parti présidentiel ne garde qu'une petite partie de la prime du sortant. */
        const val EUROPEAN_INCUMBENT_SHARE = 0.3
        const val TOP_LISTS = 4
    }
}
