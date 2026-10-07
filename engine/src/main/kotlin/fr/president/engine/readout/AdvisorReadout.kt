package fr.president.engine.readout

import fr.president.engine.data.PlaceNames
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/**
 * Conseils du moment : les trois choses les plus utiles à faire maintenant, pour que le joueur
 * sache toujours quoi faire ensuite (décisions en attente, territoire en colère, budget...).
 */
class AdvisorReadout(private val ctx: SimulationContext) {
    private val risks = RiskReadout(ctx)

    enum class Target { INBOX, DEPARTMENT, COUNTRY, ECONOMY, ELECTIONS, GOVERNMENT, DECISIONS, CRISIS, CONSEQUENCES, OFFICE, DEFENSE, MOMENTS }

    data class Advice(val icon: String, val text: String, val target: Target, val targetId: String? = null, val tone: Tone, val priority: Int)

    fun advices(limit: Int = MAX_ADVICES): List<Advice> {
        val list = mutableListOf<Advice>()
        val pending = ctx.state.inbox.messages.count { it.awaitingAnswer }
        if (pending > 0) list += Advice("✉", if (pending == 1) "Une décision vous attend" else "$pending décisions vous attendent", Target.INBOX, null, Tone.WARNING, PRIORITY_INBOX + pending)

        val territory = ctx.playerData.territory
        // Outre-mer exclu : ses écarts sont structurels et masqueraient les vrais signaux.
        val depts = ctx.state.territory.departments.values.filter { it.code.length < OVERSEAS_CODE_LENGTH }
        val national = ctx.state.playerCountry.economy.unemployment
        if (territory != null && depts.isNotEmpty()) {
            val angry = depts.filter { it.population > MIN_POPULATION }.minBy { it.approval }
            if (angry.approval < ANGRY_APPROVAL) {
                val def = territory.departments.first { it.code == angry.code }
                val place = PlaceNames.department(def.name, def.article).getValue("departmentIn")
                list += Advice("♥", "Popularité de ${Formatting.wholePercent(angry.approval)} $place : allez-y, lancez un chantier", Target.DEPARTMENT, angry.code, Tone.BAD,
                    PRIORITY_LOCAL + ((ANGRY_APPROVAL - angry.approval) * SCALE).toInt())
            }
            val jobless = depts.filter { it.population > MIN_POPULATION }.maxBy { it.unemployment }
            if (jobless.unemployment > national + HIGH_UNEMPLOYMENT_GAP) {
                val def = territory.departments.first { it.code == jobless.code }
                val place = PlaceNames.department(def.name, def.article).getValue("departmentIn")
                list += Advice("⚒", "Chômage à ${Formatting.percent(jobless.unemployment)} $place : plan emploi ou usine", Target.DEPARTMENT, jobless.code, Tone.WARNING,
                    PRIORITY_LOCAL + ((jobless.unemployment - national - HIGH_UNEMPLOYMENT_GAP) * SCALE * 2).toInt())
            }
        }

        val e = ctx.state.playerCountry.economy
        if (e.deficitRatio > HIGH_DEFICIT) {
            list += Advice("€", "Déficit de ${Formatting.percent(e.deficitRatio)} du PIB : ajustez impôts et dépenses", Target.ECONOMY, null, Tone.BAD,
                PRIORITY_BUDGET + ((e.deficitRatio - HIGH_DEFICIT) * SCALE * 2).toInt())
        }

        val days = ctx.state.time.daysUntil(ctx.state.elections.nextElection)
        if (days < ELECTION_SOON_DAYS) {
            list += Advice("✔", "Élection dans ${days.toInt()} jours : soignez votre popularité", Target.ELECTIONS, null, Tone.WARNING, PRIORITY_ELECTION)
        }

        val player = ctx.state.player.countryId
        val relations = RelationCalculator(ctx)
        ctx.state.countries.keys.filter { it != player }
            .map { it to relations.score(it, player) }
            .filter { it.second < TENSE_RELATION }
            .minByOrNull { it.second }
            ?.let { (id, _) ->
                val names = fr.president.engine.data.CountryNames(ctx.db.country(id).definition)
                list += Advice("☎", "Relations tendues avec ${names.the} : appelez son dirigeant ou faites pression", Target.COUNTRY, id, Tone.WARNING, PRIORITY_DIPLOMACY)
            }

        unhappyGroup()?.let { list += it }

        // Risque élevé sans prévention : agir avant le drame.
        risks.unattended().firstOrNull()?.let { r ->
            val where = r.hotspot?.let { " (surtout $it)" } ?: ""
            list += Advice(r.def.icon.ifBlank { "⚠" }, "${r.def.label} : risque ${r.level.lowercase()}$where. Prévenez : ${r.measures.first().label.lowercase()}",
                Target.CRISIS, r.def.id, r.tone, PRIORITY_RISK + (r.probability * SCALE).toInt())
        }
        ctx.state.measures.active.firstOrNull { m -> m.endsAt == null && ctx.state.time.let { m.startedAt.daysUntil(it) } > LONG_MEASURE_DAYS }?.let { m ->
            val label = ctx.playerData.measures?.measures?.firstOrNull { it.id == m.id }?.label ?: m.id
            list += Advice("◷", "« $label » dure depuis longtemps : faut-il la lever ?", Target.CRISIS, null, Tone.WARNING, PRIORITY_PARLIAMENT)
        }

        // La conséquence en chaîne la plus grave : ce qui abîme le pays en ce moment.
        fr.president.engine.consequences.ConsequenceService(ctx).active().firstOrNull()?.let { (r, a) ->
            list += Advice(r.icon, "${r.label} : ${r.fix.ifEmpty { "voir pourquoi" }}", Target.CONSEQUENCES, r.id,
                if (a.severity >= 1) Tone.BAD else Tone.WARNING, PRIORITY_CONSEQUENCE + (a.severity * SCALE / 4).toInt())
        }
        if (ctx.state.government.parliamentSupport < WEAK_PARLIAMENT) {
            list += Advice("⌂", "Assemblée hésitante : vos lois risquent d'être rejetées", Target.GOVERNMENT, null, Tone.WARNING, PRIORITY_PARLIAMENT)
        }
        if (list.isEmpty()) {
            list += Advice("★", "Tout va bien : prenez une décision, touchez un département pour y agir ou un pays pour négocier", Target.DECISIONS, null, Tone.GOOD, 0)
        }
        return list.sortedByDescending { it.priority }.take(limit)
    }

    /**
     * Le groupe social qui vous soutient le moins, et la décision disponible qui lui plairait le plus :
     * le conseil mène directement à la bonne rubrique du panneau « Décider ».
     */
    private fun unhappyGroup(): Advice? {
        val defs = ctx.playerData.socialGroups?.groups ?: return null
        val (group, opinion) = defs.mapNotNull { d -> ctx.state.opinion.groups[d.id]?.let { d to it.effective } }
            .minByOrNull { it.second } ?: return null
        if (opinion > UNHAPPY_GROUP) return null
        val target = "opinion.group.${group.id}"
        val decisions = fr.president.engine.session.NationalActionCommands(ctx)
        val best = decisions.actions().filter { it.blocker == null }
            .map { v -> v to (v.def.immediate + v.def.onCompletion).filter { it.target == target }.sumOf { it.amount } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first ?: return null
        return Advice("♥", "${group.label} : ${Formatting.wholePercent(opinion)} d'opinions favorables. Piste : « ${best.def.label} »",
            Target.DECISIONS, best.def.category, Tone.WARNING, PRIORITY_GROUP + ((UNHAPPY_GROUP - opinion) * SCALE).toInt())
    }

    private companion object {
        const val MAX_ADVICES = 3
        const val MIN_POPULATION = 150_000L
        const val ANGRY_APPROVAL = 0.42
        const val HIGH_UNEMPLOYMENT_GAP = 0.025
        const val OVERSEAS_CODE_LENGTH = 3
        const val HIGH_DEFICIT = 0.05
        const val ELECTION_SOON_DAYS = 180.0
        const val TENSE_RELATION = 0.3
        const val WEAK_PARLIAMENT = 0.5
        const val SCALE = 100.0
        const val PRIORITY_INBOX = 100
        const val PRIORITY_ELECTION = 90
        const val PRIORITY_BUDGET = 40
        const val PRIORITY_LOCAL = 30
        const val PRIORITY_PARLIAMENT = 35
        const val PRIORITY_DIPLOMACY = 20
        const val PRIORITY_GROUP = 25
        const val PRIORITY_RISK = 30
        const val PRIORITY_CONSEQUENCE = 38
        const val LONG_MEASURE_DAYS = 60
        const val UNHAPPY_GROUP = 0.38
    }
}
