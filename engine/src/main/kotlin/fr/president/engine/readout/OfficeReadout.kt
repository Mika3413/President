package fr.president.engine.readout

import fr.president.engine.consequences.ConsequenceService
import fr.president.engine.data.GameJson
import fr.president.engine.data.PlaceNames
import fr.president.engine.government.MinistryEffectiveness
import fr.president.engine.government.PolicyStatus
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.Simulator
import fr.president.engine.util.Formatting
import fr.president.engine.world.WorldState
import kotlin.math.abs

/**
 * Le bureau du président : ce que l'administration fait remonter chaque jour. La note du matin,
 * les rapports des ministres (indicateurs chiffrés, seuils d'alerte, avis), les remontées des
 * préfets, les notes des services (renseignement, Bercy) et les prévisions « si rien ne change ».
 * Le président ne voit pas les rouages : il voit ce qu'on lui rapporte, plus ou moins juste selon
 * la compétence de ses ministres.
 */
class OfficeReadout(private val ctx: SimulationContext) {
    private val consequences = ConsequenceService(ctx)
    private val resolver get() = ctx.variables

    data class Item(val icon: String, val title: String, val text: String, val tone: Tone, val target: AdvisorReadout.Target? = null, val targetId: String? = null)
    data class Indicator(val label: String, val value: String, val tone: Tone, val note: String = "")
    data class Report(val ministryId: String, val title: String, val minister: String, val indicators: List<Indicator>, val advice: String, val tone: Tone)
    data class Hotspot(val code: String, val name: String, val reasons: List<String>, val score: Double)
    data class Forecast(val months: Int, val lines: List<Indicator>, val newConsequences: List<String>, val reliability: String)

    // ---- Note du matin ------------------------------------------------------------------------

    /** Les points du jour, du plus urgent au moins urgent (cinq au plus). */
    fun briefing(): List<Item> {
        val items = mutableListOf<Pair<Int, Item>>()
        consequences.active().take(2).forEach { (r, a) ->
            items += (60 + (a.severity * 10).toInt()) to Item(r.icon, r.label, "${ConsequenceReadout(ctx).row(r, a).value}. ${r.fix.replaceFirstChar { it.uppercase() }}",
                if (a.severity >= 1) Tone.BAD else Tone.WARNING, AdvisorReadout.Target.CONSEQUENCES)
        }
        consequences.nearThreshold().firstOrNull()?.let { (r, gap) ->
            items += (if (gap < IMMINENT) 55 else 25) to Item("◷", (if (gap < IMMINENT) "Bientôt : " else "À surveiller : ") + r.label, ConsequenceReadout(ctx).row(r, null).let { "${it.value} — ${it.threshold}." },
                Tone.WARNING, AdvisorReadout.Target.CONSEQUENCES)
        }
        ctx.state.unrest.movements.maxByOrNull { it.crowd }?.let { m ->
            val cause = fr.president.engine.politics.UnrestService(ctx).cause(m.cause)
            items += (50 + m.phase.ordinal * 10) to Item(m.phase.icon, "${m.phase.label} : ${cause?.label?.lowercase() ?: m.cause}",
                "${fr.president.engine.politics.UnrestService(ctx).people(m.crowd)} personnes mobilisées.", if (m.phase.ordinal >= 2) Tone.BAD else Tone.WARNING)
        }
        ctx.state.policy.proposals.filter { it.status == PolicyStatus.PENDING_VOTE && ctx.now.daysUntil(it.voteAt) < VOTE_SOON }.minByOrNull { it.voteAt }?.let { p ->
            val chance = fr.president.engine.legislation.LegislationService(ctx).passChance(fr.president.engine.legislation.LegislationService(ctx).preview(p.changes).difficulty)
            items += 45 to Item("⚖", "Vote le ${Formatting.date(p.voteAt)} : ${p.title.ifEmpty { "texte du gouvernement" }}",
                "Chances d'adoption estimées à ${Math.round(chance * 100)} %.", if (chance < 0.5) Tone.WARNING else Tone.NEUTRAL, AdvisorReadout.Target.GOVERNMENT)
        }
        val approval = ctx.state.stats.series["approval"]
        val change = approval?.let { h -> h.last()?.let { now -> h.ago(4.coerceAtMost(h.size - 1))?.let { now - it } } }
        if (change != null && abs(change) >= 0.02) {
            items += 40 to Item(if (change > 0) "▲" else "▼", "Sondages : ${if (change > 0) "embellie" else "chute"} de ${Math.round(abs(change) * 100)} points en un mois",
                "Popularité à ${Formatting.wholePercent(ctx.state.opinion.nationalApproval)}.", if (change > 0) Tone.GOOD else Tone.BAD)
        }
        hotspots().firstOrNull()?.takeIf { it.score > HOT }?.let { h ->
            items += 35 to Item("⚑", "Préfet : alerte ${h.name}", h.reasons.joinToString(" ; ").replaceFirstChar { it.uppercase() } + ".", Tone.WARNING, AdvisorReadout.Target.DEPARTMENT, h.code)
        }
        ctx.state.inbox.messages.count { it.awaitingAnswer }.takeIf { it > 0 }?.let { n ->
            items += 48 to Item("✉", if (n == 1) "Un courrier attend votre réponse" else "$n courriers attendent votre réponse",
                "Sans réponse avant l'échéance, vos services appliqueront l'option par défaut.", Tone.WARNING, AdvisorReadout.Target.INBOX)
        }
        ctx.state.agenda.entries.filter { it.end > ctx.now && ctx.now.daysUntil(it.start) < 2 }.minByOrNull { it.start }?.let { a ->
            items += 20 to Item("◷", "Agenda : ${a.label}", (if (a.start <= ctx.now) "En cours" else "Le ${Formatting.date(a.start)}") + if (a.place.isNotEmpty()) " · ${a.place}." else ".", Tone.NEUTRAL)
        }
        val days = ctx.now.daysUntil(ctx.state.elections.nextElection)
        if (days < ELECTION_SOON) items += 30 to Item("✔", "Présidentielle dans ${days.toInt()} jours", "Chaque décision compte double désormais.", Tone.WARNING, AdvisorReadout.Target.ELECTIONS)
        if (fr.president.engine.military.Geopolitics(ctx).isAtWar(ctx.state.player.countryId)) {
            items += 70 to Item("⚔", "La France est en guerre", "Lassitude de l'opinion : ${Formatting.wholePercent(ctx.state.military.warWeariness)}.", Tone.BAD)
        }
        // Les guerres dans le monde : nos alliés, nos approvisionnements, notre sécurité sont en jeu.
        fr.president.engine.military.Geopolitics(ctx).activeWars().filter { ctx.state.player.countryId !in it.participants }
            .maxByOrNull { it.participants.size }?.let { w ->
                fun the(c: String) = fr.president.engine.data.CountryNames(ctx.db.country(c).definition).the
                val allies = w.participants.count { p -> ctx.db.alliances.any { it.id in setOf("EU", "NATO") && p in it.members && ctx.state.player.countryId in it.members } }
                items += 62 to Item("⚔", "Guerre : ${the(w.attackers.first())} contre ${the(w.defenders.first())}",
                    "${w.participants.size} pays engagés" + (if (allies > 0) ", dont $allies de nos alliés" else "") +
                        ". Énergie, marchés et opinion vont en subir les effets ; nos alliés attendent un geste.", Tone.BAD, AdvisorReadout.Target.COUNTRY, w.defenders.first())
            }
        val gov = ctx.state.government
        ctx.playerData.government!!.ministries.filter { if (it.isPrimeMinister) gov.primeMinisterId == null else it.id !in gov.ministers }.let { vacant ->
            if (vacant.isNotEmpty()) items += 47 to Item("⌂", if (vacant.size == 1) "Poste vacant : ${vacant[0].title}" else "${vacant.size} postes du gouvernement vacants",
                (if (vacant.size == 1) "L'intérim est assuré par le cabinet" else "Intérim : ${vacant.joinToString(", ") { it.shortTitle }}") +
                    ". Ses dossiers avancent moins bien : nommez un successeur.", Tone.WARNING, AdvisorReadout.Target.GOVERNMENT)
        }
        fr.president.engine.presidency.MomentService(ctx).let { m ->
            if (m.debateOpen()) items += 80 to Item("⚖", "Débat d'entre-deux-tours à préparer", "Vingt millions de téléspectateurs : le vainqueur du débat gagne des points au second tour.", Tone.WARNING, AdvisorReadout.Target.MOMENTS)
            m.summitsOpen().firstOrNull()?.let { (_, s) -> items += 44 to Item("✪", s.label, "Les dirigeants vous attendent : vos prises de parole changeront nos relations.", Tone.NEUTRAL, AdvisorReadout.Target.MOMENTS) }
        }
        if (fr.president.engine.military.NuclearService(ctx).pendingDecision()) {
            items += 100 to Item("☢", "Décision nucléaire en attente", "Une arme nucléaire a frappé nos forces. Riposte massive, riposte limitée ou retenue : vous seul décidez.", Tone.BAD, AdvisorReadout.Target.DEFENSE)
        }
        if (items.isEmpty()) items += 0 to Item("★", "Rien d'urgent ce matin", "Le pays est calme : c'est le moment de lancer une réforme ou de préparer l'avenir.", Tone.GOOD)
        return items.sortedByDescending { it.first }.take(MAX_ITEMS).map { it.second }
    }

    // ---- Rapports des ministres ---------------------------------------------------------------

    private val ministryVariables = linkedMapOf(
        "pm" to listOf("opinion.national", "government.parliamentSupport", "society.realRent", "society.fertility"),
        "economy" to listOf("economy.growth", "economy.unemployment", "economy.inflation", "economy.deficitRatio", "economy.debtRatio", "tax.households", "tax.businesses", "society.informal", "society.savingsRate"),
        "interior" to listOf("quality.security", "spending.police", "unrest.phase", "intel.capacity", "demography.immigration"),
        "armed_forces" to listOf("quality.defense", "spending.defense", "unrest.armyLoyalty", "military.warWeariness"),
        "health" to listOf("quality.health", "spending.health", "society.lifeExpectancy"),
        "education" to listOf("quality.education", "spending.education"),
        "labour" to listOf("economy.unemployment", "derived.rsaToSmic", "quality.social", "spending.solidarity", "society.realRent", "lever.param:pension_age"),
        "justice" to listOf("quality.justice", "spending.justice", "laws.liberty", "laws.press"),
        "ecology" to listOf("quality.environment", "spending.ecology", "energy.priceIndex"),
        "transport" to listOf("quality.transport", "spending.transport"),
        "agriculture" to listOf("quality.agriculture", "spending.agriculture"),
    )

    fun reports(): List<Report> {
        val gov = ctx.playerData.government ?: return emptyList()
        val eff = MinistryEffectiveness(ctx)
        return ministryVariables.mapNotNull { (id, vars) ->
            val def = gov.ministries.firstOrNull { it.id == id } ?: return@mapNotNull null
            val minister = eff.ministerOf(id)
            val indicators = vars.mapNotNull { indicator(it) }
            val worst = vars.mapNotNull { v -> worstRule(v) }.maxByOrNull { it.second }
            val tone = indicators.maxOfOrNull { it.tone.ordinal }?.let { Tone.entries[it] } ?: Tone.NEUTRAL
            val advice = when {
                worst != null && worst.second >= 1 -> "« La situation est grave : ${worst.first.label}. Il faut ${worst.first.fix} »"
                worst != null -> "« Je dois vous alerter : ${worst.first.label}. ${worst.first.why} »"
                tone == Tone.WARNING -> "« Plusieurs voyants passent à l'orange, il faudra agir avant qu'ils ne virent au rouge. »"
                else -> "« Rien d'alarmant dans mon domaine pour l'instant. »"
            }
            Report(id, def.title, minister?.fullName ?: "Intérim", indicators, advice, tone)
        }
    }

    /** La règle la plus grave (active, sinon proche) portant sur une variable, avec sa gravité (0 si seulement proche). */
    private fun worstRule(variable: String): Pair<fr.president.engine.consequences.ConsequenceRule, Double>? {
        val rules = ctx.db.consequences?.rules.orEmpty().filter { it.variable == variable }
        rules.mapNotNull { r -> ctx.state.consequences.current[r.id]?.takeIf { it.active }?.let { r to it.severity } }.maxByOrNull { it.second }?.let { return it }
        return consequences.nearThreshold().firstOrNull { it.first.variable == variable }?.let { it.first to 0.0 }
    }

    fun indicator(variable: String): Indicator? {
        val v = resolver.resolve(variable) ?: return null
        val rules = ctx.db.consequences?.rules.orEmpty().filter { it.variable == variable }
        val active = rules.mapNotNull { r -> ctx.state.consequences.current[r.id]?.takeIf { it.active }?.let { r to it } }.maxByOrNull { it.second.severity }
        val near = consequences.nearThreshold().firstOrNull { it.first.variable == variable }
        val tone = when {
            active != null && active.second.severity >= 1 -> Tone.BAD
            active != null || near != null -> Tone.WARNING
            rules.isEmpty() -> Tone.NEUTRAL
            else -> Tone.GOOD
        }
        val limit = (active?.first ?: near?.first ?: rules.firstOrNull())?.let { r ->
            "alerte ${if (r.above != null) "au-delà de" else "sous"} ${ConsequenceReadout.format(variable, r.above ?: r.below ?: 0.0)}"
        } ?: ""
        return Indicator(LABELS[variable] ?: variable, ConsequenceReadout.format(variable, v), tone, limit)
    }

    // ---- Remontées des préfets -----------------------------------------------------------------

    fun hotspots(limit: Int = MAX_HOTSPOTS): List<Hotspot> {
        val territory = ctx.playerData.territory ?: return emptyList()
        val depts = ctx.state.territory.departments.values.filter { it.code.length < 3 && it.population > MIN_POPULATION }
        if (depts.isEmpty()) return emptyList()
        val avgCrime = depts.sumOf { it.crime } / depts.size
        val avgHealth = depts.sumOf { it.healthAccess } / depts.size
        val national = ctx.state.playerCountry.economy.unemployment
        return depts.map { d ->
            val parts = mutableListOf<Pair<Double, String>>()
            (d.crime / avgCrime - 1).takeIf { it > 0.15 }?.let { parts += it to "délinquance ${String.format(java.util.Locale.FRENCH, "%.1f", d.crime / avgCrime)} fois la moyenne" }
            (1 - d.healthAccess / avgHealth).takeIf { it > 0.15 }?.let { parts += it to "accès aux soins difficile" }
            (d.unemployment - national).takeIf { it > 0.02 }?.let { parts += it * 10 to "chômage à ${Formatting.percent(d.unemployment)}" }
            (0.42 - d.approval).takeIf { it > 0 }?.let { parts += it * 3 to "popularité à ${Formatting.wholePercent(d.approval)}" }
            val def = territory.departments.firstOrNull { it.code == d.code }
            Hotspot(d.code, def?.let { PlaceNames.department(it.name, it.article).getValue("departmentIn") } ?: d.code,
                parts.sortedByDescending { it.first }.take(2).map { it.second }, parts.sumOf { it.first })
        }.filter { it.reasons.isNotEmpty() }.sortedByDescending { it.score }.take(limit)
    }

    // ---- Notes des services ----------------------------------------------------------------------

    fun services(): List<Item> {
        val out = mutableListOf<Item>()
        val unrest = fr.president.engine.politics.UnrestService(ctx)
        ctx.state.unrest.movements.filter { it.radicalization > RADICAL }.forEach { m ->
            out += Item("◉", "DGSI : radicalisation", "Le mouvement « ${unrest.cause(m.cause)?.label?.lowercase() ?: m.cause} » se durcit (radicalité ${Math.round(m.radicalization * 100)} %). Des casseurs s'y mêlent.", Tone.BAD)
        }
        ctx.db.intel?.groups?.mapNotNull { g -> ctx.state.intel.groups[g.id]?.let { g to it } }
            ?.filter { (_, s) -> s.hostility * s.strength > THREAT }?.maxByOrNull { (_, s) -> s.hostility * s.strength }?.let { (g, s) ->
                out += Item("◉", "DGSI : menace ${g.label}", "Capacité ${Math.round(s.strength * 100)} %, hostilité ${Math.round(s.hostility * 100)} %. Renforcer le renseignement réduit le risque d'attentat.", Tone.WARNING)
            }
        ctx.state.intel.foreignSpying.maxByOrNull { it.value }?.takeIf { it.value > SPYING }?.let { (c, v) ->
            out += Item("◉", "DGSI : espionnage", "Activité intense des services de ${fr.president.engine.data.CountryNames(ctx.db.country(c).definition).the} (${Math.round(v * 100)} %).", Tone.WARNING)
        }
        val e = ctx.state.playerCountry.economy
        val base = ctx.state.society.baseRate
        if (base >= 0 && e.marketRate - base > RATE_ALERT) {
            out += Item("€", "Bercy : les taux montent", "La France emprunte à ${Formatting.percent(e.marketRate)} contre ${Formatting.percent(base)} au début du mandat : chaque point coûte des milliards d'intérêts.", Tone.WARNING)
        }
        if (e.debtRatio > 1.2 || e.deficitRatio > 0.05) {
            out += Item("€", "Bercy : note souveraine menacée", "Dette ${Formatting.percent(e.debtRatio)} du PIB, déficit ${Formatting.percent(e.deficitRatio)} : les agences de notation regardent.", if (e.deficitRatio > 0.06) Tone.BAD else Tone.WARNING)
        }
        if (ctx.state.society.fraudLossBillions > 1) {
            out += Item("€", "Bercy : fraude et travail au noir", "${Formatting.billions(ctx.state.society.fraudLossBillions)} de recettes perdues par an par rapport au début du mandat.", Tone.WARNING)
        }
        if (out.isEmpty()) out += Item("◉", "Rien à signaler", "Les services ne relèvent aucune menace particulière.", Tone.GOOD)
        return out
    }

    // ---- Prévisions -----------------------------------------------------------------------------

    /** Ce que donnerait l'avenir si rien ne change : on fait tourner une copie du pays en avance. */
    fun forecast(months: Int): Forecast {
        val copy = GameJson.save.decodeFromString(WorldState.serializer(), GameJson.save.encodeToString(WorldState.serializer(), ctx.state))
        val future = SimulationContext(copy, ctx.db)
        Simulator(future).advanceTo(future.now.plusDays(months * DAYS_PER_MONTH))
        val now = ctx.state.playerCountry.economy
        val then = copy.playerCountry.economy
        fun line(label: String, a: Double, b: Double, higherIsBetter: Boolean, fmt: (Double) -> String): Indicator {
            val better = if (abs(b - a) < 1e-4) null else (b > a) == higherIsBetter
            return Indicator(label, "${fmt(a)} → ${fmt(b)}", when (better) { true -> Tone.GOOD; false -> Tone.BAD; null -> Tone.NEUTRAL })
        }
        val lines = listOf(
            line("Popularité", ctx.state.opinion.nationalApproval, copy.opinion.nationalApproval, true) { Formatting.wholePercent(it) },
            line("Chômage", now.unemployment, then.unemployment, false) { Formatting.percent(it) },
            line("Croissance", now.realGrowth, then.realGrowth, true) { Formatting.signedPercent(it) },
            line("Déficit", now.deficitRatio, then.deficitRatio, false) { "${Formatting.percent(it)} du PIB" },
            line("Dette", now.debtRatio, then.debtRatio, false) { "${Formatting.percent(it)} du PIB" },
            line("Inflation", now.inflation, then.inflation, false) { Formatting.percent(it) },
            line("Soutien à l'Assemblée", ctx.state.government.parliamentSupport, copy.government.parliamentSupport, true) { Formatting.wholePercent(it) },
        )
        val before = ctx.state.consequences.current.filterValues { it.active }.keys
        val after = copy.consequences.current.filterValues { it.active }.keys - before
        val competence = MinistryEffectiveness(ctx).of("economy")
        val reliability = when {
            competence >= 0.7 -> "Prévision solide : votre ministre de l'Économie est compétent. Les crises imprévues peuvent tout changer."
            competence >= 0.45 -> "Prévision moyenne : à prendre avec prudence."
            else -> "Prévision fragile : le ministère de l'Économie est mal tenu, ces chiffres peuvent être très faux."
        }
        return Forecast(months, lines, after.mapNotNull { id -> consequences.rule(id)?.let { "${it.icon} ${it.label}" } }, reliability)
    }

    companion object {
        private const val MAX_ITEMS = 5
        private const val MAX_HOTSPOTS = 5
        private const val IMMINENT = 0.15
        private const val VOTE_SOON = 10.0
        private const val HOT = 0.8
        private const val ELECTION_SOON = 180.0
        private const val MIN_POPULATION = 150_000L
        private const val RADICAL = 0.5
        private const val THREAT = 0.35
        private const val SPYING = 0.6
        private const val RATE_ALERT = 0.005
        private const val DAYS_PER_MONTH = 30.4

        val LABELS = mapOf(
            "opinion.national" to "Popularité", "government.parliamentSupport" to "Soutien à l'Assemblée",
            "society.realRent" to "Loyers", "society.fertility" to "Natalité", "society.housePrices" to "Prix des logements",
            "economy.growth" to "Croissance", "economy.unemployment" to "Chômage", "economy.inflation" to "Inflation",
            "economy.deficitRatio" to "Déficit", "economy.debtRatio" to "Dette", "tax.households" to "Impôts des ménages",
            "tax.businesses" to "Impôts des entreprises", "society.informal" to "Travail au noir", "society.savingsRate" to "Épargne des ménages",
            "quality.security" to "Sécurité", "spending.police" to "Crédits de la police", "unrest.phase" to "La rue",
            "intel.capacity" to "Renseignement", "demography.immigration" to "Immigration",
            "quality.defense" to "Armées", "spending.defense" to "Budget des armées", "unrest.armyLoyalty" to "Loyauté de l'armée",
            "military.warWeariness" to "Lassitude de la guerre", "quality.health" to "Hôpital et soins", "spending.health" to "Crédits de la santé",
            "society.lifeExpectancy" to "Espérance de vie", "quality.education" to "École", "spending.education" to "Crédits de l'éducation",
            "derived.rsaToSmic" to "RSA / SMIC", "quality.social" to "Pauvreté et solidarité", "spending.solidarity" to "Crédits de solidarité",
            "lever.param:pension_age" to "Âge de la retraite", "quality.justice" to "Justice", "spending.justice" to "Crédits de la justice",
            "laws.liberty" to "Libertés publiques", "laws.press" to "Liberté de la presse", "quality.environment" to "Environnement",
            "spending.ecology" to "Crédits de l'écologie", "energy.priceIndex" to "Prix de l'énergie", "quality.transport" to "Transports",
            "spending.transport" to "Crédits des transports", "quality.agriculture" to "Agriculture", "spending.agriculture" to "Aides agricoles",
        )
    }
}
