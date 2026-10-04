package fr.president.engine.government

import fr.president.engine.effects.EffectSpec
import fr.president.engine.inbox.InboxMessage
import fr.president.engine.inbox.MessageOption
import fr.president.engine.inbox.MessageOrigin
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.politics.Character
import fr.president.engine.politics.Traits
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.time.WorldTime
import fr.president.engine.util.clamp01
import kotlinx.serialization.Serializable

/** Projet qu'un ministre veut porter lui-même. */
@Serializable
data class InitiativeDef(
    val id: String,
    val ministry: String,
    val label: String,
    val description: String,
    val costBillions: Double = 0.0,
    val effects: List<EffectSpec> = emptyList(),
)

/** Désaccord récurrent entre deux ministères, que le président doit trancher. */
@Serializable
data class DisputeDef(
    val id: String,
    val a: String,
    val b: String,
    val subject: String,
    val aLabel: String,
    val aEffects: List<EffectSpec>,
    val bLabel: String,
    val bEffects: List<EffectSpec>,
)

@Serializable
data class CabinetFile(val initiatives: List<InitiativeDef> = emptyList(), val disputes: List<DisputeDef> = emptyList())

@Serializable
enum class AffairKind { INITIATIVE, DISPUTE, THREAT, GAFFE }

/** Une affaire du gouvernement en attente d'arbitrage présidentiel. */
@Serializable
data class CabinetAffair(
    val id: String,
    val kind: AffairKind,
    val ministerId: String,
    val otherId: String? = null,
    val refId: String? = null,
    val createdAt: WorldTime,
)

/**
 * Le gouvernement vit sans attendre le président : chaque mois, des ministres proposent leurs
 * projets, s'opposent sur un dossier, menacent de partir ou sortent de la ligne. Leur
 * tempérament (ego, prudence, agressivité) et leur loyauté décident de ce qu'ils osent.
 */
class CabinetSystem : SimulationSystem {
    override val name = "cabinet"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val file = ctx.playerData.cabinet ?: return
        if (ctx.state.player.gameOver != null) return
        val service = CabinetService(ctx)
        val gov = ctx.state.government
        val threshold = ctx.playerData.government?.ministerDrift?.resignationLoyaltyThreshold ?: DEFAULT_THRESHOLD
        var raised = 0
        for ((ministry, id) in gov.ministers.toList().shuffledWith(ctx)) {
            if (raised >= MAX_PER_MONTH) break
            val m = ctx.state.characters[id] ?: continue
            if (gov.affairs.any { it.ministerId == id }) continue
            val ego = m.trait(Traits.EGO)
            val caution = m.trait(Traits.CAUTION)
            when {
                m.loyalty < threshold + THREAT_MARGIN && ego > THREAT_EGO && ctx.rng.chance(THREAT_CHANCE) -> { service.raise(AffairKind.THREAT, m); raised++ }
                ctx.rng.chance(GAFFE_BASE + GAFFE_RECKLESS * (1 - caution) + GAFFE_DISLOYAL * (1 - m.loyalty)) -> { service.raise(AffairKind.GAFFE, m); raised++ }
                ctx.rng.chance(INITIATIVE_BASE + INITIATIVE_EGO * ego + INITIATIVE_COMPETENCE * m.competence) -> {
                    val initiative = file.initiatives.filter { it.ministry == ministry && it.id !in gov.proposedInitiatives }.let { if (it.isEmpty()) null else ctx.rng.pick(it) }
                    if (initiative != null) { service.raise(AffairKind.INITIATIVE, m, ref = initiative.id); raised++ }
                }
            }
        }
        if (raised < MAX_PER_MONTH && ctx.rng.chance(DISPUTE_CHANCE)) dispute(ctx, file, service)
    }

    private fun dispute(ctx: SimulationContext, file: CabinetFile, service: CabinetService) {
        val gov = ctx.state.government
        val possible = file.disputes.filter { d ->
            gov.ministers[d.a] != null && gov.ministers[d.b] != null &&
                gov.lastDisputes[d.id]?.let { it.daysUntil(ctx.now) < DISPUTE_COOLDOWN_DAYS } != true &&
                gov.affairs.none { it.ministerId == gov.ministers[d.a] || it.ministerId == gov.ministers[d.b] }
        }
        if (possible.isEmpty()) return
        val d = ctx.rng.pick(possible)
        val a = ctx.state.characters.getValue(gov.ministers.getValue(d.a))
        val b = ctx.state.characters.getValue(gov.ministers.getValue(d.b))
        gov.lastDisputes[d.id] = ctx.now
        service.raise(AffairKind.DISPUTE, a, other = b, ref = d.id)
    }

    private fun <T> List<T>.shuffledWith(ctx: SimulationContext): List<T> {
        val list = toMutableList()
        for (i in list.size - 1 downTo 1) { val j = ctx.rng.nextInt(i + 1); val t = list[i]; list[i] = list[j]; list[j] = t }
        return list
    }

    private companion object {
        const val MAX_PER_MONTH = 2
        const val DEFAULT_THRESHOLD = 0.2
        const val THREAT_MARGIN = 0.12
        const val THREAT_EGO = 0.45
        const val THREAT_CHANCE = 0.5
        const val GAFFE_BASE = 0.003
        const val GAFFE_RECKLESS = 0.012
        const val GAFFE_DISLOYAL = 0.015
        const val INITIATIVE_BASE = 0.01
        const val INITIATIVE_EGO = 0.03
        const val INITIATIVE_COMPETENCE = 0.015
        const val DISPUTE_CHANCE = 0.2
        const val DISPUTE_COOLDOWN_DAYS = 365.0
    }
}

/** Création des affaires (courrier au président) et application de l'arbitrage. */
class CabinetService(private val ctx: SimulationContext) {
    private val gov get() = ctx.state.government
    private val file get() = ctx.playerData.cabinet ?: CabinetFile()

    fun raise(kind: AffairKind, minister: Character, other: Character? = null, ref: String? = null): InboxMessage {
        val affair = CabinetAffair(ctx.state.newId("aff"), kind, minister.id, other?.id, ref, ctx.now)
        gov.affairs += affair
        if (kind == AffairKind.INITIATIVE && ref != null) gov.proposedInitiatives += ref
        val title = titleOf(minister)
        val (subject, body, options, default) = when (kind) {
            AffairKind.INITIATIVE -> initiativeLetter(minister, title, file.initiatives.first { it.id == ref })
            AffairKind.DISPUTE -> disputeLetter(minister, other!!, file.disputes.first { it.id == ref })
            AffairKind.THREAT -> threatLetter(minister, title)
            AffairKind.GAFFE -> gaffeLetter(minister, title)
        }
        if (kind == AffairKind.GAFFE) {
            ctx.effects.trigger(EffectSpec("opinion.national", -GAFFE_SHOCK), null, emptyMap(), "cabinet")
            ctx.notifications.news(NotificationCategory.GOVERNMENT, "${minister.fullName} contredit la ligne du gouvernement", null)
        }
        val message = InboxMessage(
            id = ctx.state.newId("msg"),
            senderId = if (kind == AffairKind.DISPUTE) gov.primeMinisterId else minister.id,
            senderLabel = if (kind == AffairKind.DISPUTE) pmLabel() else "${minister.fullName}, $title",
            subject = subject, body = body, time = ctx.now,
            category = NotificationCategory.GOVERNMENT, origin = MessageOrigin.CABINET, originId = affair.id,
            options = options, deadline = ctx.now.plusDays(RESPONSE_DAYS), defaultOptionId = default,
        )
        ctx.state.inbox.messages += message
        ctx.notifications.post(NotificationCategory.GOVERNMENT, fr.president.engine.notifications.Urgency.IMPORTANT,
            "Gouvernement : $subject", message.senderLabel, null, journal = false)
        return message
    }

    fun answer(message: InboxMessage, optionId: String, byDefault: Boolean) {
        message.chosenOptionId = optionId
        message.answeredByDefault = byDefault
        message.read = true
        val affair = gov.affairs.firstOrNull { it.id == message.originId } ?: return
        gov.affairs.remove(affair)
        val m = ctx.state.characters[affair.ministerId] ?: return
        if (!m.active) return
        when (affair.kind) {
            AffairKind.INITIATIVE -> initiative(m, file.initiatives.firstOrNull { it.id == affair.refId } ?: return, optionId)
            AffairKind.DISPUTE -> dispute(m, affair.otherId?.let { ctx.state.characters[it] } ?: return, file.disputes.firstOrNull { it.id == affair.refId } ?: return, optionId)
            AffairKind.THREAT -> threat(m, optionId)
            AffairKind.GAFFE -> gaffe(m, optionId)
        }
    }

    // --- Initiatives -----------------------------------------------------------------------------

    private fun initiativeLetter(m: Character, title: String, i: InitiativeDef): Letter {
        val cost = if (i.costBillions > 0) " (coût : ${fr.president.engine.util.Formatting.billions(i.costBillions)})" else ""
        val ego = m.trait(Traits.EGO) > HIGH_TRAIT
        val body = "${honorific()},\n\n${pick(OPENINGS)} Je souhaite lancer un projet qui me tient à cœur : ${i.label.lowercase()}$cost.\n\n${i.description}\n\n" +
            (if (ego) "Je suis convaincu que c'est ce que les Français attendent, et je compte l'annoncer rapidement. " else "Je ne le lancerai qu'avec votre accord. ") +
            "Sans réponse de votre part, ${if (ego) "j'irai de l'avant" else "je le mettrai de côté"}.\n\n${m.fullName}"
        return Letter("Projet du ministère : ${i.label}", body, listOf(
            MessageOption("back", "Soutenir pleinement le projet", "Coût et effets complets ; ministre ravi"),
            MessageOption("scaled", "Le soutenir en version réduite", "Moitié du coût et des effets"),
            MessageOption("refuse", "Refuser", "Aucun coût ; ministre froissé"),
            MessageOption("silence", "Ne pas répondre", if (ego) "Il l'annoncera sans votre aval" else "Le projet sera abandonné"),
        ), "silence")
    }

    private fun initiative(m: Character, i: InitiativeDef, option: String) {
        val ego = m.trait(Traits.EGO) > HIGH_TRAIT
        val share = when (option) {
            "back" -> 1.0
            "scaled" -> HALF
            "silence" -> if (ego) HALF else 0.0
            else -> 0.0
        }
        if (share > 0) {
            if (i.costBillions > 0) trigger("budget.oneOff", i.costBillions * share, days = PROJECT_DAYS)
            i.effects.forEach { ctx.effects.trigger(it.copy(amount = it.amount * share), null, emptyMap(), "cabinet:${i.id}") }
            journal("Projet de ${m.fullName} : ${i.label}" + if (share < 1) " (version réduite)" else "", fr.president.engine.readout.Tone.GOOD)
        }
        when (option) {
            "back" -> { m.loyalty = (m.loyalty + LOYALTY_GAIN).clamp01(); m.popularity = (m.popularity + POPULARITY_GAIN).clamp01() }
            "scaled" -> m.loyalty = (m.loyalty + LOYALTY_GAIN / 3).clamp01()
            "refuse" -> { m.loyalty = (m.loyalty - LOYALTY_LOSS).clamp01(); m.relationWithPlayer = (m.relationWithPlayer - RELATION_LOSS).clamp01() }
            "silence" -> if (ego) {
                // Annonce sans l'aval de l'Élysée : l'autorité présidentielle en pâtit.
                trigger("president.popularity", -AUTHORITY_LOSS)
                ctx.notifications.news(NotificationCategory.GOVERNMENT, "${m.fullName} annonce seul son projet : ${i.label.lowercase()}", null)
            } else m.loyalty = (m.loyalty - LOYALTY_LOSS / 2).clamp01()
        }
    }

    // --- Disputes ---------------------------------------------------------------------------------

    private fun disputeLetter(a: Character, b: Character, d: DisputeDef): Letter {
        val ta = titleOf(a)
        val tb = titleOf(b)
        val body = "${honorific()},\n\nUn désaccord oppose ${a.fullName} ($ta) et ${b.fullName} ($tb) sur ${d.subject}. " +
            "Les deux ministres se sont exprimés dans la presse et la majorité attend un arbitrage.\n\n" +
            "${a.fullName} défend : « ${d.aLabel} ». ${b.fullName} plaide : « ${d.bLabel} ».\n\n" +
            "Je peux trancher à votre place si vous le souhaitez, mais l'opinion attend que le président tranche.\n\n${pmName()}"
        return Letter("Désaccord au gouvernement : ${d.subject}", body, listOf(
            MessageOption("a", "Trancher pour ${a.fullName} : ${d.aLabel.lowercase()}", "${b.fullName} sera désavoué"),
            MessageOption("b", "Trancher pour ${b.fullName} : ${d.bLabel.lowercase()}", "${a.fullName} sera désavoué"),
            MessageOption("compromise", "Imposer un compromis", "Moitié des effets ; aucun des deux n'est satisfait"),
            MessageOption("pm", "Laisser le Premier ministre arbitrer", "Vous restez au-dessus de la mêlée ; on vous dira absent"),
        ), "pm")
    }

    private fun dispute(a: Character, b: Character, d: DisputeDef, option: String) {
        fun apply(effects: List<EffectSpec>, share: Double) = effects.forEach { ctx.effects.trigger(it.copy(amount = it.amount * share), null, emptyMap(), "cabinet:${d.id}") }
        fun win(w: Character, l: Character) {
            w.loyalty = (w.loyalty + LOYALTY_GAIN).clamp01()
            l.loyalty = (l.loyalty - LOYALTY_LOSS).clamp01()
            l.relationWithPlayer = (l.relationWithPlayer - RELATION_LOSS).clamp01()
        }
        when (option) {
            "a" -> { apply(d.aEffects, 1.0); win(a, b); journal("Arbitrage pour ${a.fullName} : ${d.aLabel}", fr.president.engine.readout.Tone.NEUTRAL) }
            "b" -> { apply(d.bEffects, 1.0); win(b, a); journal("Arbitrage pour ${b.fullName} : ${d.bLabel}", fr.president.engine.readout.Tone.NEUTRAL) }
            "compromise" -> {
                apply(d.aEffects, HALF); apply(d.bEffects, HALF)
                listOf(a, b).forEach { it.loyalty = (it.loyalty - LOYALTY_LOSS / 3).clamp01() }
                trigger("government.parliamentSupport", -COMPROMISE_COST)
                journal("Compromis imposé : ${d.subject}", fr.president.engine.readout.Tone.NEUTRAL)
            }
            else -> {
                // Le Premier ministre penche pour le ministre le plus influent.
                val aWins = a.popularity + a.competence >= b.popularity + b.competence
                if (aWins) { apply(d.aEffects, HALF); win(a, b) } else { apply(d.bEffects, HALF); win(b, a) }
                trigger("president.popularity", -AUTHORITY_LOSS / 2)
                journal("Le Premier ministre tranche pour ${if (aWins) a.fullName else b.fullName}", fr.president.engine.readout.Tone.NEUTRAL)
            }
        }
    }

    // --- Menaces de démission ---------------------------------------------------------------------

    private fun threatLetter(m: Character, title: String): Letter {
        val spending = spendingOf(m)
        val body = "${honorific()},\n\n${pick(THREATS)}\n\nMon ministère manque de moyens et mes propositions restent sans suite. " +
            "Si rien ne change, je ne pourrai pas rester au gouvernement.\n\n${m.fullName}"
        return Letter("${m.fullName} menace de démissionner", body, listOf(
            MessageOption("concede", "Lui accorder une rallonge pour son ministère", "Coût : 300 M€" + (spending?.let { " et dépenses relevées" } ?: "") + " ; loyauté retrouvée"),
            MessageOption("reassure", "Le recevoir et le rassurer", "Gratuit ; peut suffire, ou pas"),
            MessageOption("accept", "Prendre acte : il peut partir", "Départ immédiat ; ministère à pourvoir"),
            MessageOption("ignore", "Ne pas répondre", "Il risque fort de démissionner avec fracas"),
        ), "ignore")
    }

    private fun threat(m: Character, option: String) {
        when (option) {
            "concede" -> {
                trigger("budget.oneOff", CONCESSION_BILLIONS)
                spendingOf(m)?.let { trigger("spending.$it", SPENDING_RAISE) }
                m.loyalty = (m.loyalty + CONCESSION_LOYALTY).clamp01()
                journal("Rallonge accordée à ${m.fullName}", fr.president.engine.readout.Tone.NEUTRAL)
            }
            "reassure" -> {
                val convinced = ctx.rng.chance((REASSURE_BASE + m.relationWithPlayer * REASSURE_RELATION).coerceAtMost(REASSURE_MAX))
                m.loyalty = (m.loyalty + if (convinced) LOYALTY_GAIN * 2 else LOYALTY_GAIN / 2).clamp01()
                m.relationWithPlayer = (m.relationWithPlayer + RELATION_LOSS).clamp01()
            }
            "accept" -> {
                GovernmentChanges(ctx).dismiss(m.id)
                journal("Départ de ${m.fullName}", fr.president.engine.readout.Tone.BAD)
            }
            else -> m.loyalty = (m.loyalty - LOYALTY_LOSS).clamp01()
        }
    }

    // --- Sorties de route -------------------------------------------------------------------------

    private fun gaffeLetter(m: Character, title: String): Letter {
        val body = "${honorific()},\n\n${m.fullName} ($title) a tenu ce matin à la radio des propos qui contredisent la ligne du gouvernement. " +
            "L'opposition s'en est emparée et les députés de la majorité s'interrogent.\n\nIl faut décider de la réponse de l'Élysée.\n\n${pmName()}"
        return Letter("${m.fullName} sort de la ligne", body, listOf(
            MessageOption("rebuke", "Le recadrer publiquement", "Autorité affirmée ; ministre humilié"),
            MessageOption("cover", "Le couvrir", "Ministre reconnaissant ; on vous reprochera la cacophonie"),
            MessageOption("ignore", "Laisser passer", "La polémique s'éteindra... ou pas"),
        ), "ignore")
    }

    private fun gaffe(m: Character, option: String) {
        when (option) {
            "rebuke" -> {
                m.loyalty = (m.loyalty - LOYALTY_LOSS).clamp01()
                trigger("opinion.national", GAFFE_SHOCK / 2)
                trigger("government.parliamentSupport", COMPROMISE_COST)
            }
            "cover" -> { m.loyalty = (m.loyalty + LOYALTY_GAIN / 2).clamp01(); trigger("opinion.national", -GAFFE_SHOCK / 2) }
            else -> trigger("president.popularity", -AUTHORITY_LOSS / 2)
        }
    }

    // --- Outils -----------------------------------------------------------------------------------

    private data class Letter(val subject: String, val body: String, val options: List<MessageOption>, val default: String)

    private fun trigger(target: String, amount: Double, days: Double = 0.0) =
        ctx.effects.trigger(EffectSpec(target, amount, days = days), null, emptyMap(), "cabinet")

    private fun journal(text: String, tone: fr.president.engine.readout.Tone) = fr.president.engine.stats.JournalService(ctx).add("Gouvernement", text, tone)

    private fun pick(list: List<String>) = ctx.rng.pick(list)

    private fun honorific(): String {
        val inst = ctx.playerData.definition.institutions
        val female = ctx.state.characters[ctx.state.player.presidentId]?.female == true
        return if (female) inst.honorificFemale else inst.honorificMale
    }

    private fun ministryOf(m: Character) = gov.ministers.entries.firstOrNull { it.value == m.id }?.key

    private fun titleOf(m: Character) = ministryOf(m)?.let { id -> ctx.playerData.government?.ministries?.firstOrNull { it.id == id }?.title } ?: "ministre"

    private fun spendingOf(m: Character) = ministryOf(m)?.let { id -> ctx.playerData.economy.budget?.spending?.firstOrNull { it.ministry == id }?.id }

    private fun pmName() = gov.primeMinisterId?.let { ctx.state.characters[it]?.fullName } ?: "Le secrétaire général de l'Élysée"

    private fun pmLabel(): String {
        val pm = gov.primeMinisterId?.let { ctx.state.characters[it] } ?: return "Secrétariat général de l'Élysée"
        val inst = ctx.playerData.definition.institutions
        return "${pm.fullName}, ${if (pm.female) inst.headOfGovernmentTitleFemale ?: inst.headOfGovernmentTitle else inst.headOfGovernmentTitle}"
    }

    private companion object {
        const val RESPONSE_DAYS = 5.0
        const val HIGH_TRAIT = 0.6
        const val HALF = 0.5
        const val PROJECT_DAYS = 180.0
        const val LOYALTY_GAIN = 0.06
        const val LOYALTY_LOSS = 0.08
        const val RELATION_LOSS = 0.05
        const val POPULARITY_GAIN = 0.03
        const val AUTHORITY_LOSS = 0.006
        const val COMPROMISE_COST = 0.004
        const val GAFFE_SHOCK = 0.004
        const val CONCESSION_BILLIONS = 0.3
        const val SPENDING_RAISE = 0.02
        const val CONCESSION_LOYALTY = 0.2
        const val REASSURE_BASE = 0.2
        const val REASSURE_RELATION = 0.6
        const val REASSURE_MAX = 0.85

        val OPENINGS = listOf(
            "Je me permets de vous écrire directement.",
            "Après plusieurs semaines de travail avec mes équipes, je viens vers vous.",
            "Je voulais vous soumettre une idée avant qu'elle ne fuite dans la presse.",
            "Nos administrations sont prêtes : il ne manque que votre feu vert.",
        )
        val THREATS = listOf(
            "Je dois vous le dire franchement : je songe à quitter le gouvernement.",
            "Ma loyauté envers vous est intacte, mais ma patience a des limites.",
            "Je ne peux plus défendre une politique dont je ne partage plus les choix.",
        )
    }
}
