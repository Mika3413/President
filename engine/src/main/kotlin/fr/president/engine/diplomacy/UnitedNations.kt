package fr.president.engine.diplomacy

import fr.president.engine.data.CountryNames
import fr.president.engine.effects.EffectSpec
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOption
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.military.Geopolitics
import fr.president.engine.military.WarService
import fr.president.engine.military.WarStatus
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.Traits
import fr.president.engine.readout.Tone
import fr.president.engine.session.AgendaCost
import fr.president.engine.session.AgendaService
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

@Serializable
data class UnProfile(val west: Double = 0.0, val russia: Double = 0.0, val china: Double = 0.0, val nonaligned: Double = 0.0) {
    fun dot(o: UnProfile) = west * o.west + russia * o.russia + china * o.china + nonaligned * o.nonaligned
}

@Serializable
data class UnMemberDef(val id: String, val name: String = "", val country: String? = null, val profile: UnProfile, val until: Int = 0)

@Serializable
data class UnTemplate(
    val id: String,
    val kind: String,
    val title: String,
    val description: String = "",
    val interest: String = "",
    val stances: UnProfile,
    val effects: List<EffectSpec> = emptyList(),
)

@Serializable
data class UnFile(
    val permanent: List<UnMemberDef>,
    val elected: List<UnMemberDef>,
    val candidates: List<UnMemberDef> = emptyList(),
    val warTemplates: List<UnTemplate> = emptyList(),
    val thematic: List<UnTemplate> = emptyList(),
    val voteDelayDays: Double = 15.0,
    val proposalIntervalDays: Double = 40.0,
)

@Serializable
enum class UnVote(val label: String) { YES("Pour"), NO("Contre"), ABSTAIN("Abstention"), VETO("Veto") }

@Serializable
data class UnSeat(val id: String, val until: Int)

@Serializable
class UnDraft(
    val id: String,
    val template: String,
    val title: String,
    val sponsor: String,
    val aggressor: String? = null,
    val victim: String? = null,
    val proposedAt: WorldTime,
    val voteAt: WorldTime,
    var france: UnVote = UnVote.ABSTAIN,
    val lobbied: MutableSet<String> = mutableSetOf(),
    var amended: Boolean = false,
)

@Serializable
data class UnResult(
    val title: String,
    val time: WorldTime,
    val adopted: Boolean,
    val sponsor: String,
    val votes: Map<String, UnVote>,
    val vetoBy: List<String> = emptyList(),
    val france: UnVote = UnVote.ABSTAIN,
)

@Serializable
class UnState(
    val seats: MutableList<UnSeat> = mutableListOf(),
    var nextCandidate: Int = 0,
    var lastYear: Int = 0,
    val drafts: MutableList<UnDraft> = mutableListOf(),
    val results: MutableList<UnResult> = mutableListOf(),
    var nextForeign: WorldTime? = null,
    var lastFrench: WorldTime? = null,
    val usedThemes: MutableSet<String> = mutableSetOf(),
    var vetoes: Int = 0,
    var frenchAdopted: Int = 0,
)

/**
 * Conseil de sécurité : de nouveaux projets de résolution arrivent régulièrement (guerres en
 * cours, crises du monde), chaque membre vote selon ses alignements et ses relations, un seul
 * veto d'un membre permanent suffit à tout bloquer. Chaque 1er janvier, cinq sièges changent.
 */
class UnSystem : SimulationSystem {
    override val name = "un"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val file = ctx.db.un ?: return
        val service = UnService(ctx)
        val s = service.state
        val year = ctx.now.toDateTime().year
        if (s.lastYear != year) { service.renew(file, year); s.lastYear = year }
        s.drafts.filter { it.voteAt.daysUntil(ctx.now) >= 0 }.forEach { service.vote(it) }
        if (s.drafts.none { it.sponsor != ctx.state.player.countryId } && s.nextForeign?.let { ctx.now.daysUntil(it) <= 0 } != false) {
            if (s.nextForeign != null) service.proposeForeign(file)
            s.nextForeign = ctx.now.plusDays(file.proposalIntervalDays * (0.6 + ctx.rng.nextDouble() * 0.8))
        }
    }
}

class UnService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.un
    private val player get() = ctx.state.player.countryId
    private val relations = RelationCalculator(ctx)
    private val geo = Geopolitics(ctx)

    val available: Boolean get() = file != null

    val state: UnState get() = ctx.state.un.also { s ->
        val f = file ?: return@also
        if (s.seats.isEmpty()) {
            s.seats += f.elected.map { UnSeat(it.id, it.until) }
            s.lastYear = ctx.now.toDateTime().year
        }
    }

    val permanent: List<String> get() = file?.permanent?.map { it.id }.orEmpty()
    fun members(): List<String> = permanent + state.seats.map { it.id }
    fun isPermanent(id: String) = id in permanent

    private fun def(id: String): UnMemberDef? = file?.let { f -> (f.permanent + f.elected + f.candidates).firstOrNull { it.id == id } }

    /** Pays simulé correspondant à un membre (ou null). */
    fun simulated(id: String): String? = def(id)?.country ?: id.takeIf { it in ctx.state.countries }

    fun name(id: String): String = simulated(id)?.let { ctx.db.countries[it]?.definition?.name } ?: def(id)?.name?.takeIf { it.isNotEmpty() } ?: id

    fun template(id: String) = file?.let { f -> (f.warTemplates + f.thematic).firstOrNull { it.id == id } }

    /** Chaque 1er janvier, les cinq élus en fin de mandat laissent la place à de nouveaux. */
    fun renew(f: UnFile, year: Int) {
        val s = state
        val leaving = s.seats.filter { it.until < year }
        if (leaving.isEmpty() || f.candidates.isEmpty()) return
        s.seats.removeAll(leaving)
        repeat(leaving.size) {
            val c = f.candidates[s.nextCandidate % f.candidates.size]
            s.nextCandidate++
            if (s.seats.none { it.id == c.id }) s.seats += UnSeat(c.id, year + 1)
        }
        ctx.notifications.news(NotificationCategory.DIPLOMACY, "ONU : ${leaving.size} nouveaux membres au Conseil de sécurité (${s.seats.takeLast(leaving.size).joinToString { name(it.id) }})")
    }

    // ---- Position de chaque membre ----

    fun support(member: String, d: UnDraft): Double {
        val t = template(d.template) ?: return 0.0
        val profile = def(member)?.profile ?: UnProfile(nonaligned = 1.0)
        var s = profile.dot(t.stances)
        val sim = simulated(member)
        if (sim != null && d.aggressor != null) {
            if (sim == d.aggressor) return -1.0
            if (sim == d.victim) return 1.0
            if (geo.allied(sim, d.aggressor)) s -= ALLY_PENALTY
            d.victim?.let { if (geo.allied(sim, it)) s += ALLY_BONUS }
            s += AGGRESSOR_WEIGHT * (0.5 - relations.score(sim, d.aggressor))
        }
        if (sim != null && sim != d.sponsor && d.sponsor in ctx.state.countries) s += SPONSOR_WEIGHT * (relations.score(sim, d.sponsor) - 0.5)
        if (member in d.lobbied) s += LOBBY
        if (d.amended) s += AMEND
        return s.coerceIn(-1.0, 1.0)
    }

    fun forecast(member: String, d: UnDraft): UnVote {
        if (member == player) return if (d.sponsor == player) UnVote.YES else d.france
        val s = support(member, d)
        return when {
            s > YES_THRESHOLD -> UnVote.YES
            isPermanent(member) && s < VETO_THRESHOLD -> UnVote.VETO
            !isPermanent(member) && s < NO_THRESHOLD -> UnVote.NO
            else -> UnVote.ABSTAIN
        }
    }

    data class Outlook(val votes: Map<String, UnVote>, val yes: Int, val vetoBy: List<String>, val adopted: Boolean)

    fun outlook(d: UnDraft): Outlook {
        val votes = members().associateWith { forecast(it, d) }
        val yes = votes.values.count { it == UnVote.YES }
        val vetoes = votes.filterValues { it == UnVote.VETO }.keys.toList()
        return Outlook(votes, yes, vetoes, yes >= MAJORITY && vetoes.isEmpty())
    }

    // ---- Projets ----

    /** Un autre membre dépose un projet : guerre en cours ou crise du monde. */
    fun proposeForeign(f: UnFile) {
        val wars = geo.activeWars()
        val draft = if (wars.isNotEmpty() && f.warTemplates.isNotEmpty() && ctx.rng.chance(WAR_SHARE)) {
            val w = ctx.rng.pick(wars)
            val aggressor = w.attackers.first()
            val victim = w.defenders.first()
            val t = ctx.rng.pick(f.warTemplates.filter { it.kind != "force" || ctx.rng.chance(0.3) })
            val sponsors = members().filter { it != player && simulated(it) != aggressor && support(it, UnDraft("", t.id, "", "", aggressor, victim, ctx.now, ctx.now)) > 0.3 }
            val sponsor = sponsors.firstOrNull { simulated(it) != null }?.let { simulated(it) } ?: return
            build(t, sponsor, aggressor, victim)
        } else {
            val pool = f.thematic.filter { it.id !in state.usedThemes }.ifEmpty { state.usedThemes.clear(); f.thematic }
            if (pool.isEmpty()) return
            val t = ctx.rng.pick(pool)
            state.usedThemes += t.id
            val sponsor = ctx.rng.pick(listOf("USA", "GBR").filter { it in ctx.state.countries }.ifEmpty { return })
            build(t, sponsor, null, null)
        }
        state.drafts += draft
        val t = template(draft.template)!!
        val body = "${name(draft.sponsor)} dépose un projet de résolution : ${draft.title.replaceFirstChar { it.lowercase() }}.\n\n${t.description}" +
            (if (t.interest.isNotEmpty()) "\n\nPour la France : ${t.interest}" else "") +
            "\n\nIl faut 9 voix sur 15 et aucun veto des membres permanents. La France est membre permanent : un vote contre de sa part est un veto. Vote dans ${f.voteDelayDays.toInt()} jours."
        ctx.state.inbox.messages += InboxMessage(
            id = ctx.state.newId("msg"), senderId = ctx.state.government.ministers["foreign"],
            senderLabel = "Représentation de la France à l'ONU", subject = "Conseil de sécurité : ${draft.title}", body = body, time = ctx.now,
            category = NotificationCategory.DIPLOMACY, origin = MessageOrigin.UN, originId = draft.id,
            options = listOf(
                MessageOption("YES", "Voter pour", "Il faut encore 9 voix"),
                MessageOption("VETO", "Opposer le veto de la France", "Le texte tombe ; ses partisans nous en voudront"),
                MessageOption("ABSTAIN", "S'abstenir", "Pas d'engagement"),
            ),
            deadline = draft.voteAt.plusDays(-1.0), defaultOptionId = "ABSTAIN",
        )
        ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.INFO, "ONU : ${draft.title}", "Vote du Conseil de sécurité dans ${f.voteDelayDays.toInt()} jours.", null, journal = false)
    }

    private fun build(t: UnTemplate, sponsor: String, aggressor: String?, victim: String?): UnDraft {
        val f = file!!
        val title = aggressor?.let { a ->
            val an = CountryNames(ctx.db.country(a).definition)
            val vn = victim?.let { CountryNames(ctx.db.country(it).definition) }
            t.title.replace("{aggressorOf}", an.of).replace("{aggressorThe}", an.the).replace("{victimThe}", vn?.the ?: "")
        } ?: t.title
        return UnDraft(ctx.state.newId("un"), t.id, title.replaceFirstChar { it.uppercase() }, sponsor, aggressor, victim, ctx.now, ctx.now.plusDays(f.voteDelayDays))
    }

    fun answer(message: InboxMessage, optionId: String, byDefault: Boolean) {
        message.chosenOptionId = optionId
        message.answeredByDefault = byDefault
        message.read = true
        state.drafts.firstOrNull { it.id == message.originId }?.france = UnVote.valueOf(optionId)
    }

    fun setFranceVote(draftId: String, vote: UnVote) {
        val d = state.drafts.firstOrNull { it.id == draftId } ?: return
        if (d.sponsor == player) return
        d.france = vote
        ctx.state.inbox.messages.firstOrNull { it.origin == MessageOrigin.UN && it.originId == draftId && it.awaitingAnswer }?.let { it.chosenOptionId = vote.name; it.read = true }
    }

    // ---- Propositions françaises ----

    data class Proposal(val template: UnTemplate, val aggressor: String?, val victim: String?, val title: String)

    fun frenchBlocker(): String? {
        if (state.drafts.any { it.sponsor == player }) return "Un projet français est déjà en discussion."
        state.lastFrench?.let { if (it.daysUntil(ctx.now) < FRENCH_COOLDOWN) return "Laissez passer quelques semaines avant un nouveau texte." }
        return AgendaService(ctx).blocker(PROPOSAL_AGENDA)
    }

    fun proposals(): List<Proposal> {
        val f = file ?: return emptyList()
        val wars = geo.activeWars().flatMap { w ->
            val a = w.attackers.first(); val v = w.defenders.first()
            f.warTemplates.filter { it.kind != "force" || player in w.defenders || geo.allied(player, v) }.map { t -> Proposal(t, a, v, build(t, player, a, v).title) }
        }
        return wars + f.thematic.map { Proposal(it, null, null, it.title) }
    }

    fun propose(templateId: String, aggressor: String?, victim: String?): Result<String> = runCatching {
        frenchBlocker()?.let { error(it) }
        val t = template(templateId)!!
        val d = build(t, player, aggressor, victim)
        state.drafts += d
        state.lastFrench = ctx.now
        AgendaService(ctx).book("Discours à l'ONU (New York)", PROPOSAL_AGENDA)
        JournalService(ctx).add("Diplomatie", "La France dépose à l'ONU : ${d.title.lowercase()}", Tone.NEUTRAL)
        val o = outlook(d)
        "Projet déposé. Vote dans ${file!!.voteDelayDays.toInt()} jours ; à ce stade : ${o.yes} voix pour" + if (o.vetoBy.isNotEmpty()) ", veto annoncé ${o.vetoBy.joinToString { name(it) }}." else "."
    }

    fun lobbyBlocker(d: UnDraft, member: String): String? = when {
        member == player -> "Impossible."
        member in d.lobbied -> "Déjà sollicité."
        else -> AgendaService(ctx).blocker(LOBBY_AGENDA)
    }

    /** Convaincre un membre du Conseil (appel, visite de l'ambassadeur, contreparties). */
    fun lobby(draftId: String, member: String): Result<String> = runCatching {
        val d = state.drafts.first { it.id == draftId }
        lobbyBlocker(d, member)?.let { error(it) }
        d.lobbied += member
        AgendaService(ctx).book("Appel au dirigeant : ${name(member)}", LOBBY_AGENDA)
        simulated(member)?.let { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("NEGOTIATION_GOODWILL", 0.01, ctx.now, "consultations à l'ONU") }
        "${name(member)} : désormais ${forecast(member, d).label.lowercase()}."
    }

    /** Adoucir un projet français pour rallier des voix (effets réduits). */
    fun amend(draftId: String): Result<String> = runCatching {
        val d = state.drafts.first { it.id == draftId }
        require(d.sponsor == player) { "Seul l'auteur peut amender." }
        require(!d.amended) { "Déjà amendé." }
        d.amended = true
        "Texte adouci : plus de voix, moins de portée."
    }

    // ---- Vote ----

    fun vote(d: UnDraft) {
        val s = state
        s.drafts.remove(d)
        val o = outlook(d)
        val result = UnResult(d.title, ctx.now, o.adopted, d.sponsor, o.votes, o.vetoBy, o.votes[player] ?: UnVote.ABSTAIN)
        s.results += result
        if (s.results.size > MAX_RESULTS) s.results.removeAt(0)
        val france = o.votes[player]
        if (france == UnVote.VETO) {
            s.vetoes++
            o.votes.filterValues { it == UnVote.YES }.keys.mapNotNull { simulated(it) }.forEach {
                ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("CONDEMNATION", -VETO_ANGER, ctx.now, "veto français à l'ONU")
            }
        }
        if (o.adopted) apply(d)
        if (d.sponsor == player) {
            if (o.adopted) { s.frenchAdopted++; ctx.effects.trigger(EffectSpec("president.popularity", 0.008), null, emptyMap(), "un") }
            else o.vetoBy.mapNotNull { simulated(it) }.forEach { ctx.state.diplomacy.relation(player, it).memories += DiplomaticMemory("DISAGREEMENT", -0.02, ctx.now, "veto à l'ONU") }
        }
        val counts = "${o.yes} pour, ${o.votes.values.count { it == UnVote.NO }} contre, ${o.votes.values.count { it == UnVote.ABSTAIN }} abstentions"
        val text = when {
            o.adopted -> "Résolution adoptée ($counts)."
            o.vetoBy.isNotEmpty() -> "Résolution rejetée : veto ${o.vetoBy.joinToString { name(it) }} ($counts)."
            else -> "Résolution rejetée faute de 9 voix ($counts)."
        }
        ctx.notifications.post(NotificationCategory.DIPLOMACY, if (d.sponsor == player) Urgency.IMPORTANT else Urgency.INFO, "ONU : ${d.title}", text, d.aggressor, journal = d.sponsor == player)
    }

    private fun apply(d: UnDraft) {
        val t = template(d.template) ?: return
        val scale = if (d.amended) AMENDED_SCALE else 1.0
        val votes = outlook(d).votes
        val yesCountries = votes.filterValues { it == UnVote.YES }.keys.mapNotNull { simulated(it) }
        t.effects.forEach { ctx.effects.trigger(it.copy(amount = it.amount * scale), null, emptyMap(), "un:${t.id}") }
        val a = d.aggressor ?: return
        val war = geo.activeWars().firstOrNull { a in it.attackers && (d.victim == null || d.victim in it.defenders) }
        when (t.kind) {
            "condemn" -> {
                yesCountries.filter { it != a }.forEach { ctx.state.diplomacy.relation(it, a).memories += DiplomaticMemory("CONDEMNATION", -CONDEMN * scale, ctx.now, "condamnation à l'ONU") }
                war?.let { it.weariness[a] = ((it.weariness[a] ?: 0.0) + CONDEMN_WEARINESS * scale).coerceAtMost(1.0) }
            }
            "ceasefire", "peacekeeping" -> war?.let { w ->
                val leader = ctx.state.characters[ctx.state.countries[a]?.leaderId]
                val chance = (if (t.kind == "peacekeeping") PEACEKEEPING_CHANCE else CEASEFIRE_CHANCE) * scale - (leader?.trait(Traits.AGGRESSIVENESS) ?: 0.5) * 0.4
                if (w.status == WarStatus.ACTIVE && ctx.rng.chance(chance)) WarService(ctx).ceasefire(w, if (t.kind == "peacekeeping") 180 else 90)
                else ctx.notifications.news(NotificationCategory.DIPLOMACY, "${ctx.db.country(a).definition.name} ignore la résolution de l'ONU")
            }
            "sanctions" -> {
                val sanctions = SanctionsService(ctx)
                yesCountries.filter { it != a && it != player }.forEach { sanctions.impose(it, a, announce = false) }
                if (votes[player] == UnVote.YES) sanctions.impose(player, a)
            }
            "force" -> if (geo.atWar(player, a)) {
                ctx.effects.trigger(EffectSpec("opinion.national", 0.01 * scale), null, emptyMap(), "un")
                yesCountries.filter { it != player }.forEach { ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("MILITARY_SUPPORT", 0.03, ctx.now, "mandat de l'ONU") }
            }
        }
    }

    companion object {
        const val MAJORITY = 9
        private const val ALLY_PENALTY = 0.6
        private const val ALLY_BONUS = 0.3
        private const val AGGRESSOR_WEIGHT = 0.8
        private const val SPONSOR_WEIGHT = 0.5
        private const val LOBBY = 0.3
        private const val AMEND = 0.15
        private const val YES_THRESHOLD = 0.15
        private const val NO_THRESHOLD = -0.15
        private const val VETO_THRESHOLD = -0.3
        private const val WAR_SHARE = 0.6
        private const val FRENCH_COOLDOWN = 30.0
        private const val MAX_RESULTS = 30
        private const val VETO_ANGER = 0.04
        private const val AMENDED_SCALE = 0.6
        private const val CONDEMN = 0.05
        private const val CONDEMN_WEARINESS = 0.05
        private const val CEASEFIRE_CHANCE = 0.7
        private const val PEACEKEEPING_CHANCE = 0.6
        val PROPOSAL_AGENDA = AgendaCost(1.0, abroad = true, place = "USA")
        val LOBBY_AGENDA = AgendaCost(0.25)
    }
}
