package fr.president.engine.government

import fr.president.engine.data.LegislativeDef
import fr.president.engine.data.PoliticalFamilyDef
import fr.president.engine.data.ReferendumDef
import fr.president.engine.elections.Candidate
import fr.president.engine.elections.ElectionSimulator
import fr.president.engine.elections.PoliticalFamilies
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.Character
import fr.president.engine.politics.CharacterRole
import fr.president.engine.setup.PoliticalSetup
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting
import fr.president.engine.util.clamp01
import kotlin.math.pow

enum class MajorityStatus(val label: String) {
    ABSOLUTE("Majorité absolue"),
    RELATIVE("Majorité relative"),
    COHABITATION("Cohabitation"),
}

/**
 * Assemblée nationale simplifiée : composition par famille politique, soutien au gouvernement
 * déduit des sièges et de la proximité idéologique, législatives (à échéance ou après dissolution),
 * motions de censure et référendums. Procédures résumées, conséquences réalistes.
 */
class ParliamentService(private val ctx: SimulationContext) {
    private val elections = ctx.playerData.elections
    val legislative: LegislativeDef? = elections?.legislative
    val referendum: ReferendumDef? = elections?.referendum
    private val state get() = ctx.state.parliament

    val families: List<PoliticalFamilyDef> get() = elections?.families.orEmpty()
    val isActive: Boolean get() = legislative != null && state.seats.isNotEmpty()

    /** Constitue l'Assemblée d'une nouvelle partie (ou d'une sauvegarde antérieure à cette fonctionnalité). */
    fun ensure() {
        val def = legislative ?: return
        if (state.seats.isNotEmpty()) return
        val (votes, turnout) = simulateVotes(coattail = true, noise = 0.0)
        val seats = allocate(votes, def)
        state.seats.putAll(seats)
        state.lastLegislative = ctx.state.player.termStart
        state.legislativeResults += LegislativeResult(ctx.state.player.termStart, votes, seats, turnout, afterDissolution = false)
        val next = ctx.state.elections.nextElection.plusDays(def.daysAfterPresidential.toLong())
        state.nextLegislative = next
        ctx.scheduler.schedule(ScheduledAction.LegislativeElection(next))
        ctx.state.government.parliamentSupport = supportTarget()
    }

    // ---- Composition et soutien ----

    private fun president(): Character = ctx.state.characters.getValue(ctx.state.player.presidentId)
    private fun primeMinister(): Character? = ctx.state.government.primeMinisterId?.let { ctx.state.characters[it] }

    fun familyOf(c: Character): PoliticalFamilyDef = PoliticalFamilies.closest(families, c.economicLeaning, c.socialLeaning)
    fun presidentFamily(): PoliticalFamilyDef = familyOf(president())
    /** Famille du Premier ministre (celle du président si Matignon est vacant). */
    fun primeMinisterFamily(): PoliticalFamilyDef = primeMinister()?.let { familyOf(it) } ?: presidentFamily()

    /** Proximité entre deux familles (1 = identiques, 0 = au-delà de la portée d'alliance). */
    fun affinity(a: PoliticalFamilyDef, b: PoliticalFamilyDef): Double {
        val range = legislative?.allyRange ?: return if (a.id == b.id) 1.0 else 0.0
        return (1.0 - PoliticalFamilies.distance(a, b) / range).clamp01()
    }

    /**
     * Propension d'un groupe parlementaire à voter les textes du gouvernement : sa proximité avec
     * la famille du président ou celle du Premier ministre (un PM d'ouverture élargit la base).
     */
    fun familySupport(family: PoliticalFamilyDef): Double =
        maxOf(affinity(family, presidentFamily()), affinity(family, primeMinisterFamily()))

    fun totalSeats(): Int = state.seats.values.sum()

    /** Part des sièges acquise au gouvernement, pondérée par la proximité de chaque groupe. */
    fun seatSupport(): Double {
        val total = totalSeats().takeIf { it > 0 } ?: return 0.0
        return families.sumOf { (state.seats[it.id] ?: 0) * familySupport(it) } / total
    }

    fun supportTarget(): Double {
        val def = legislative ?: return ctx.state.government.parliamentSupport
        // Un exécutif à deux têtes de familles éloignées se divise : coût de cohésion.
        val cohesion = 1.0 - affinity(presidentFamily(), primeMinisterFamily())
        return (seatSupport() + def.approvalWeight * (ctx.state.opinion.nationalApproval - NEUTRAL) -
            def.cohesionWeight * cohesion).clamp01()
    }

    /** Sièges des groupes proches du président (soutien de principe supérieur à la moitié). */
    fun presidentialBlocSeats(): Int {
        val own = presidentFamily()
        return families.filter { affinity(it, own) >= BLOC_SUPPORT }.sumOf { state.seats[it.id] ?: 0 }
    }

    fun majorityStatus(): MajorityStatus {
        val total = totalSeats()
        val bloc = presidentialBlocSeats()
        val largest = state.seats.maxByOrNull { it.value }?.key
        return when {
            bloc * 2 > total -> MajorityStatus.ABSOLUTE
            largest == presidentFamily().id || bloc >= total * RELATIVE_SHARE -> MajorityStatus.RELATIVE
            else -> MajorityStatus.COHABITATION
        }
    }

    /** Famille la plus nombreuse de l'Assemblée, où chercher un Premier ministre de compromis. */
    fun largestFamily(): PoliticalFamilyDef {
        val largest = state.seats.maxByOrNull { it.value }?.key
        return families.firstOrNull { it.id == largest } ?: presidentFamily()
    }

    // ---- Législatives ----

    /** Simule le vote par famille ; renvoie les parts de voix et la participation. */
    private fun simulateVotes(coattail: Boolean, noise: Double): Pair<Map<String, Double>, Double> {
        val def = legislative!!
        val presidentFamily = presidentFamily()
        val p = president()
        val candidates = families.map { f ->
            val incumbent = f.id == presidentFamily.id
            Candidate(
                characterId = if (incumbent) p.id else f.id,
                familyId = f.id,
                incumbent = incumbent,
                economicPosition = f.economicPosition,
                socialPosition = f.socialPosition,
                momentum = if (incumbent) {
                    (if (coattail) def.coattailBonus else 0.0) - elections!!.incumbentBonus * (1.0 - def.incumbentBonusShare)
                } else 0.0,
            )
        }
        val result = ElectionSimulator(ctx).simulate(candidates, noise)
        val votes = candidates.associate { c -> c.familyId to (result.shares[c.characterId] ?: 0.0) }
        return votes to result.turnout
    }

    /** Scrutin majoritaire : les écarts de voix sont amplifiés en sièges (plus forts restes). */
    private fun allocate(votes: Map<String, Double>, def: LegislativeDef): Map<String, Int> {
        val weights = votes.mapValues { it.value.coerceAtLeast(0.0).pow(def.seatAmplification) }
        val sum = weights.values.sum().takeIf { it > 0 } ?: return votes.mapValues { 0 }
        val quotas = weights.mapValues { it.value / sum * def.seats }
        val seats = quotas.mapValues { it.value.toInt() }.toMutableMap()
        var remaining = def.seats - seats.values.sum()
        for (id in quotas.entries.sortedByDescending { it.value - it.value.toInt() }.map { it.key }) {
            if (remaining <= 0) break
            seats[id] = seats.getValue(id) + 1
            remaining--
        }
        return seats
    }

    fun dissolutionBlocker(): String? {
        val def = legislative ?: return "Indisponible pour ce pays"
        if (state.dissolutionPending) return "Des élections législatives sont déjà convoquées"
        state.lastLegislative?.let { last ->
            if (last.daysUntil(ctx.now) < def.minDaysBetweenDissolutions) {
                return "Pas de nouvelle dissolution dans l'année qui suit des élections législatives"
            }
        }
        if (ctx.now.daysUntil(ctx.state.elections.nextElection) < def.dissolutionCampaignDays * 2) {
            return "L'élection présidentielle est trop proche"
        }
        if (ctx.state.elections.pendingFirstRound != null) return "Élection présidentielle en cours"
        return null
    }

    fun dissolve(): Result<Unit> = runCatching {
        dissolutionBlocker()?.let { error(it) }
        val def = legislative!!
        val at = ctx.now.plusDays(def.dissolutionCampaignDays.toLong())
        state.dissolutionPending = true
        state.nextLegislative = at
        ctx.scheduler.schedule(ScheduledAction.LegislativeElection(at))
        ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Dissolution de l'Assemblée nationale",
            "Vous dissolvez l'Assemblée. Les élections législatives auront lieu le ${Formatting.date(at)}. " +
                "Les électeurs jugeront votre bilan : une victoire renforce votre majorité, une défaite peut imposer une cohabitation.")
        ctx.log("parliament", "Dissolution, scrutin le $at")
    }

    fun runLegislative(at: fr.president.engine.time.WorldTime) {
        val def = legislative ?: return
        // Une dissolution remplace le scrutin prévu au calendrier : l'ancienne échéance est ignorée.
        if (state.nextLegislative != at || ctx.state.player.gameOver != null) return
        val lastPresidential = ctx.state.elections.results.lastOrNull()?.time ?: ctx.state.player.termStart
        val coattail = lastPresidential.daysUntil(ctx.now) <= def.coattailWindowDays
        val (votes, turnout) = simulateVotes(coattail, elections!!.pollNoise / 2)
        val seats = allocate(votes, def)
        val before = presidentialBlocSeats()
        state.seats.clear()
        state.seats.putAll(seats)
        val afterDissolution = state.dissolutionPending
        state.legislativeResults += LegislativeResult(ctx.now, votes, seats, turnout, afterDissolution)
        state.dissolutionPending = false
        state.lastLegislative = ctx.now
        val next = ctx.now.plusYears(def.termYears)
        state.nextLegislative = next
        ctx.scheduler.schedule(ScheduledAction.LegislativeElection(next))
        ctx.state.government.parliamentSupport = supportTarget()

        val status = majorityStatus()
        val bloc = presidentialBlocSeats()
        val ranking = families.sortedByDescending { seats[it.id] ?: 0 }
            .joinToString(", ") { "${it.name} ${seats[it.id] ?: 0}" }
        val headline = when (status) {
            MajorityStatus.ABSOLUTE -> if (bloc >= before) "Législatives : majorité absolue" else "Législatives : majorité absolue, mais réduite"
            MajorityStatus.RELATIVE -> "Législatives : majorité relative"
            MajorityStatus.COHABITATION -> "Législatives : cohabitation"
        }
        val advice = when (status) {
            MajorityStatus.ABSOLUTE -> "Vos textes devraient être adoptés sans difficulté majeure."
            MajorityStatus.RELATIVE -> "Chaque texte devra trouver des appuis au-delà de votre camp. Un Premier ministre d'ouverture peut aider."
            MajorityStatus.COHABITATION -> "L'Assemblée vous est hostile. Nommer un Premier ministre issu de la nouvelle majorité est le seul moyen de gouverner."
        }
        ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, headline,
            "Participation ${Formatting.percent(turnout)}. Sièges : $ranking. $advice")
        if (status == MajorityStatus.COHABITATION) refreshPrimeMinisterPool(largestFamily())
        ctx.log("parliament", "Législatives : $seats, bloc présidentiel $bloc (avant $before), statut $status")
    }

    /** Renouvelle les personnalités pressenties pour Matignon : moitié issue de [family], moitié du camp présidentiel. */
    fun refreshPrimeMinisterPool(family: PoliticalFamilyDef) {
        val pmMinistry = ctx.playerData.government?.ministries?.firstOrNull { it.isPrimeMinister } ?: return
        val pool = ctx.state.government.candidates.getOrPut(pmMinistry.id) { mutableListOf() }
        pool.forEach { id -> ctx.state.characters[id]?.let { if (it.role == CharacterRole.MINISTER_CANDIDATE) it.active = false } }
        pool.clear()
        val setup = PoliticalSetup(ctx)
        val own = presidentFamily()
        val size = ctx.playerData.government!!.candidatesPerMinistry
        val fromMajority = (size + 1) / 2
        repeat(size) { i ->
            val f = if (i < fromMajority) family else own
            pool += setup.person(ctx.state.player.countryId, CharacterRole.MINISTER_CANDIDATE, pmMinistry.id,
                f.economicPosition, POOL_SPREAD, f.socialPosition).id
        }
    }

    // ---- Motion de censure ----

    fun requestCensure(proposalId: String?) {
        val def = legislative ?: return
        if (state.censurePending) return
        state.censurePending = true
        ctx.scheduler.schedule(ScheduledAction.CensureVote(ctx.now.plusDays(def.censureDelayDays.toLong()), proposalId))
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "Motion de censure déposée",
            "L'opposition dépose une motion de censure contre le gouvernement. Vote dans ${def.censureDelayDays} jours. " +
                "Si elle est adoptée, le Premier ministre devra démissionner.")
    }

    fun censureVote() {
        val def = legislative ?: return
        if (!state.censurePending) return
        state.censurePending = false
        val support = ctx.state.government.parliamentSupport + ctx.rng.nextGaussian() * def.censureNoise
        val forced = ctx.state.policy.proposals.filter { it.status == PolicyStatus.PENDING_CENSURE }
        val policy = PolicyService(ctx)
        if (support < def.censureThreshold) {
            forced.forEach { it.status = PolicyStatus.REJECTED }
            governmentFalls()
        } else {
            state.censuresSurvived++
            forced.forEach { policy.enactForced(it) }
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "La motion de censure est rejetée",
                "Le gouvernement survit au vote." + if (forced.isNotEmpty()) " Les textes engagés sont adoptés." else "")
        }
        ctx.log("parliament", "Censure : soutien tiré %.3f (seuil %.3f)".format(support, def.censureThreshold))
    }

    private fun governmentFalls() {
        val def = legislative!!
        val gov = ctx.state.government
        val pm = primeMinister()
        pm?.let {
            it.role = CharacterRole.FORMER
            it.active = false
        }
        gov.primeMinisterId = null
        state.governmentsFallen++
        ctx.state.opinion.groups.values.forEach { it.shock -= def.governmentFallApprovalCost }
        refreshPrimeMinisterPool(largestFamily())
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "Le gouvernement est renversé",
            "La motion de censure est adoptée${pm?.let { " : ${it.fullName} présente sa démission" } ?: ""}. " +
                "Les textes engagés sont rejetés. Nommez un Premier ministre capable de réunir une majorité, ou dissolvez l'Assemblée.")
    }

    /** Appelé chaque mois : une opposition dominante peut tenter de renverser le gouvernement. */
    fun monthlyCheck() {
        val def = legislative ?: return
        if (!isActive || state.censurePending || state.dissolutionPending) return
        if (ctx.state.government.parliamentSupport < def.spontaneousCensureSupport &&
            ctx.rng.nextDouble() < def.spontaneousCensureChanceMonthly) {
            requestCensure(null)
        }
    }

    // ---- Référendum ----

    fun referendumBlocker(reformId: String): String? {
        val def = referendum ?: return "Indisponible pour ce pays"
        state.referendumReform?.let { return "Un référendum est déjà en campagne" }
        state.lastReferendum?.let { last ->
            if (last.daysUntil(ctx.now) < def.minDaysBetween) return "Un référendum a eu lieu trop récemment"
        }
        if (ctx.state.elections.pendingFirstRound != null || state.dissolutionPending) return "Une élection est en cours"
        return PolicyService(ctx).reformBlocker(reformId)
    }

    fun callReferendum(reformId: String): Result<Unit> = runCatching {
        referendumBlocker(reformId)?.let { error(it) }
        val def = referendum!!
        val reform = ctx.playerData.reforms!!.reforms.first { it.id == reformId }
        val at = ctx.now.plusDays(def.campaignDays.toLong())
        state.referendumReform = reformId
        state.referendumAt = at
        ctx.scheduler.schedule(ScheduledAction.ReferendumVote(at, reformId))
        ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Référendum convoqué",
            "Les Français se prononceront le ${Formatting.date(at)} sur : ${reform.title}. " +
                "Le vote portera autant sur votre personne que sur le texte.")
    }

    /** Estimation de la part de « oui » (sans bruit), utilisée pour les sondages. */
    fun referendumEstimate(reformId: String): Pair<Double, Double> {
        val reform = ctx.playerData.reforms?.reforms?.firstOrNull { it.id == reformId } ?: return NEUTRAL to 0.0
        val interest = reform.immediateEffects
            .filter { it.target.startsWith(GROUP_PREFIX) }
            .groupBy({ it.target.removePrefix(GROUP_PREFIX) }, { it.amount })
            .mapValues { it.value.sum() }
        return estimateWithInterest(interest)
    }

    /** Part de « oui » pour un texte dont on connaît l'intérêt pour chaque groupe (et participation). */
    fun estimateWithInterest(interest: Map<String, Double>): Pair<Double, Double> {
        val def = referendum ?: return NEUTRAL to 0.0
        val groups = ctx.playerData.socialGroups ?: return NEUTRAL to 0.0
        var yes = 0.0
        var voters = 0.0
        for (group in groups.groups) {
            val approval = ctx.state.opinion.groups[group.id]?.effective ?: group.baseApproval
            val p = (NEUTRAL + def.approvalWeight * (approval - NEUTRAL) + def.interestWeight * (interest[group.id] ?: 0.0))
                .coerceIn(MIN_YES, MAX_YES)
            val weight = group.populationShare * group.baseTurnout
            yes += weight * p
            voters += weight
        }
        val partitions = groups.partitions.size.coerceAtLeast(1)
        return (if (voters > 0) yes / voters else NEUTRAL) to voters / partitions
    }

    fun runReferendum(reformId: String) {
        val def = referendum ?: return
        if (state.referendumReform != reformId) return
        val (estimate, turnout) = referendumEstimate(reformId)
        val yes = (estimate + ctx.rng.nextGaussian() * def.noise).clamp01()
        state.referendumReform = null
        state.referendumAt = null
        state.lastReferendum = ctx.now
        state.referendumResults += ReferendumResult(ctx.now, reformId, yes, turnout)
        val reform = ctx.playerData.reforms!!.reforms.first { it.id == reformId }
        if (yes > NEUTRAL) {
            PolicyService(ctx).adoptByReferendum(reformId)
            ctx.state.government.parliamentSupport = (ctx.state.government.parliamentSupport + def.victorySupportBonus).clamp01()
            ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Le « oui » l'emporte",
                "${reform.title} : ${Formatting.percent(yes)} de oui. La réforme est adoptée et votre autorité sort renforcée.")
        } else {
            state.lockedReforms[reformId] = ctx.now.plusDays(def.defeatLockDays.toLong())
            ctx.state.opinion.groups.values.forEach { it.shock -= def.defeatApprovalCost }
            ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Le « non » l'emporte",
                "${reform.title} : seulement ${Formatting.percent(yes)} de oui. Ce désaveu affaiblit votre présidence ; " +
                    "le texte ne pourra pas être représenté avant longtemps.")
        }
        ctx.log("parliament", "Référendum $reformId : oui %.3f (estimation %.3f)".format(yes, estimate))
    }

    private companion object {
        const val NEUTRAL = 0.5
        const val BLOC_SUPPORT = 0.5
        const val RELATIVE_SHARE = 0.4
        const val MIN_YES = 0.05
        const val MAX_YES = 0.95
        const val GROUP_PREFIX = "opinion.group."
        const val POOL_SPREAD = 0.12
    }
}
