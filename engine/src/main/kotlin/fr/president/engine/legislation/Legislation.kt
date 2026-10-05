package fr.president.engine.legislation

import fr.president.engine.effects.EffectSpec
import fr.president.engine.government.PolicyKind
import fr.president.engine.government.PolicyProposal
import fr.president.engine.government.PolicyService
import fr.president.engine.government.PolicyStatus
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.ScheduledAction
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * La fabrique de la loi. Trois voies :
 *  - la loi de finances (impôts, dépenses, fiscalité, mesures chiffrées) : votée chaque année à
 *    l'automne, ou en cours d'année par un budget rectificatif ;
 *  - la loi ordinaire (société, justice, travail, libertés, Constitution, réformes) : un projet
 *    qui regroupe plusieurs changements, avec un nom proposé ;
 *  - le décret : petits réglages du gouvernement, immédiats, que le Conseil d'État peut annuler.
 * Chaque levier n'a qu'une valeur ; un texte voté la change, le Conseil constitutionnel peut en
 * censurer des articles, une loi peut être abrogée.
 */
class LegislationService(private val ctx: SimulationContext) {
    val state: LegislationState get() = ctx.state.legislation
    val levers = LeverService(ctx)
    private val policy get() = ctx.state.policy

    // ---- Textes en discussion -----------------------------------------------------------------

    fun pendingBills(): List<PolicyProposal> = policy.proposals.filter { it.kind in BILLS && it.status in OPEN }

    /** Texte en discussion qui contient déjà ce levier. */
    fun pendingFor(lever: String): PolicyProposal? = pendingBills().firstOrNull { p -> p.changes.any { it.lever == lever } }
        ?: policy.proposals.firstOrNull { it.status in OPEN && it.kind !in BILLS && legacyLever(it) == lever }

    private fun legacyLever(p: PolicyProposal): String? = when (p.kind) {
        PolicyKind.TAX_RATE -> "tax:${p.itemId}"
        PolicyKind.SPENDING -> "spend:${p.itemId}"
        PolicyKind.FISCAL -> "fiscal:${p.itemId}"
        PolicyKind.LAW -> "law:${p.itemId}"
        PolicyKind.REFORM -> "reform:${p.itemId}"
        else -> null
    }

    /** La loi de finances annuelle, tant qu'elle peut encore être amendée par le gouvernement. */
    val openPlf: PolicyProposal? get() = state.plfProposal?.let { id -> policy.proposals.firstOrNull { it.id == id } }
        ?.takeIf { it.status == PolicyStatus.PENDING_VOTE && ctx.now.daysUntil(it.voteAt) > AMEND_CLOSE_DAYS }

    // ---- Projet de budget -------------------------------------------------------------------

    /** Valeur visée dans le budget en préparation (ou la valeur en vigueur). */
    fun budgetTarget(id: String): Double =
        openPlf?.changes?.firstOrNull { it.lever == id }?.to ?: state.budgetDraft[id] ?: levers.current(id)

    fun budgetChanges(): List<LeverChange> = state.budgetDraft.map { (id, v) ->
        LeverChange(id, levers.current(id), v, measure = state.budgetMeasures[id])
    }

    fun budgetBlocker(id: String): String? {
        val lever = levers.lever(id) ?: (state.budgetMeasures[id]?.let { levers.measureLever(it) }) ?: return null
        if (lever.channel != Channel.BUDGET) return "Ce réglage ne relève pas du budget."
        val pending = pendingFor(id)
        if (pending != null && pending.id != openPlf?.id) return "Déjà dans un texte soumis au vote."
        return null
    }

    /** Régler un levier budgétaire : il entre dans le projet de budget (ou dans la loi de finances en discussion). */
    fun setBudget(id: String, value: Double, cfg: MeasureConfig? = null): String? {
        cfg?.let { state.budgetMeasures[id] = it }
        budgetBlocker(id)?.let { return it }
        val current = levers.current(id)
        val plf = openPlf
        if (plf != null) {
            plf.changes.removeAll { it.lever == id }
            if (abs(value - current) > EPS) plf.changes += LeverChange(id, current, value, measure = cfg ?: state.budgetMeasures[id])
            state.budgetDraft.remove(id)
            return null
        }
        if (abs(value - current) < EPS) { state.budgetDraft.remove(id); if (id.startsWith("m:") && id !in state.measures) state.budgetMeasures.remove(id) }
        else state.budgetDraft[id] = value
        return null
    }

    fun clearBudget(id: String) {
        state.budgetDraft.remove(id)
        openPlf?.changes?.removeAll { it.lever == id }
        if (id !in state.measures) state.budgetMeasures.remove(id)
    }

    fun correctiveBlocker(): String? = when {
        state.budgetDraft.isEmpty() -> "Le projet de budget ne contient aucun changement."
        pendingBills().any { it.kind == PolicyKind.BUDGET_BILL } -> "Une loi de finances est déjà en discussion : vos réglages y sont ajoutés."
        else -> null
    }

    /** Budget rectificatif : le projet de budget est voté tout de suite, sans attendre l'automne. */
    fun depositCorrective(): Result<PolicyProposal> = runCatching {
        correctiveBlocker()?.let { error(it) }
        state.correctiveCount++
        val year = ctx.now.toDateTime().year
        val p = createBill(PolicyKind.BUDGET_BILL, "Loi de finances rectificative pour $year" + if (state.correctiveCount > 1) " (n° ${state.correctiveCount})" else "",
            budgetChanges(), ctx.playerData.government?.parliament?.taxVoteDelayDays ?: CORRECTIVE_DAYS)
        state.budgetDraft.clear()
        p
    }

    /** Date du dépôt de la prochaine loi de finances annuelle. */
    fun nextPlfDeposit(): WorldTime {
        val now = ctx.now.toDateTime()
        if (state.plfYear < now.year + 1 && now.monthValue >= PLF_MONTH) return ctx.now
        val year = if (state.plfYear >= now.year + 1) now.year + 1 else now.year
        return WorldTime.fromDateTime(LocalDateTime.of(year, PLF_MONTH, 1, 9, 0))
    }

    // ---- Projet de loi ----------------------------------------------------------------------

    fun lawChanges(): List<LeverChange> = state.lawDraft.changes

    fun lawBlocker(id: String, value: Double): String? {
        val lever = levers.lever(id) ?: return "Réglage inconnu."
        if (lever.channel == Channel.BUDGET) return "Ce réglage relève de la loi de finances (onglet Budget)."
        if (pendingFor(id) != null) return "Un texte sur ce sujet est déjà soumis au vote."
        if (lever.irreversible && value < levers.current(id) - EPS) return "On ne peut pas revenir en arrière."
        if (id.startsWith("law:")) LawGate.cooldown(ctx, id.substringAfter(':'))?.let { return it }
        if (id.startsWith("reform:")) PolicyService(ctx).reformBlocker(id.substringAfter(':'))?.let { if (value >= 0.5) return it }
        return null
    }

    /** Ajouter (ou remplacer) un changement dans le projet de loi en préparation. */
    fun addToLaw(id: String, value: Double, cfg: MeasureConfig? = null): String? {
        lawBlocker(id, value)?.let { if (levers.lever(id) != null || cfg == null) return it }
        val current = levers.current(id)
        state.lawDraft.changes.removeAll { it.lever == id }
        if (abs(value - current) > EPS) state.lawDraft.changes += LeverChange(id, current, value, measure = cfg)
        return null
    }

    fun removeFromLaw(id: String) { state.lawDraft.changes.removeAll { it.lever == id } }

    fun setLawName(name: String?) { state.lawDraft.name = name?.trim()?.takeIf { it.isNotEmpty() } }

    fun lawName(): String = state.lawDraft.name ?: autoName(state.lawDraft.changes)

    /** Nom proposé : « Loi Dupont sur la société », « Loi constitutionnelle Dupont »... */
    fun autoName(changes: List<LeverChange>): String {
        val president = ctx.state.characters[ctx.state.player.presidentId]?.lastName ?: ""
        if (changes.isEmpty()) return "Projet de loi"
        val ls = changes.mapNotNull { levers.lever(it.lever) }
        if (ls.any { it.constitutional }) return "Loi constitutionnelle $president".trim()
        if (ls.size == 1) return "Loi $president : ${ls[0].label.replaceFirstChar { it.lowercase() }}".replace("  ", " ")
        val domain = ls.groupingBy { it.domain }.eachCount().maxByOrNull { it.value }?.key
        return "Loi $president ${DOMAIN_NAMES[domain] ?: "portant diverses dispositions"}".replace("  ", " ")
    }

    fun lawDepositBlocker(): String? {
        val changes = state.lawDraft.changes
        if (changes.isEmpty()) return "Le projet de loi est vide : ajoutez au moins un changement."
        if (pendingBills().count { it.kind == PolicyKind.LAW_BILL } >= MAX_LAW_BILLS) return "Le Parlement examine déjà $MAX_LAW_BILLS projets de loi."
        changes.forEach { c -> lawBlocker(c.lever, c.to)?.let { r -> return "${levers.lever(c.lever)?.label ?: c.lever} : $r" } }
        return null
    }

    fun referendumBlocker(): String? {
        lawDepositBlocker()?.let { return it }
        if (state.referendums.isNotEmpty() || ctx.state.parliament.referendumReform != null || ctx.state.laws.referendums.isNotEmpty()) return "Un référendum est déjà convoqué."
        return null
    }

    /** Déposer le projet de loi au Parlement, ou le soumettre directement au peuple. */
    fun depositLaw(referendum: Boolean = false): Result<PolicyProposal> = runCatching {
        (if (referendum) referendumBlocker() else lawDepositBlocker())?.let { error(it) }
        val changes = state.lawDraft.changes.map { LeverChange(it.lever, levers.current(it.lever), it.to, measure = it.measure) }
        if (!referendum && article16Active()) {
            // Pleins pouvoirs : le texte s'applique tout de suite, sans Parlement ni Conseil constitutionnel.
            val p = PolicyProposal(ctx.state.newId("pol"), PolicyKind.LAW_BILL, "bill", 0.0, 0.0, ctx.now, ctx.now, status = PolicyStatus.ADOPTED, title = lawName())
            p.changes += changes
            state.lawDraft = LawDraft()
            enactBill(p, BillStatus.ARTICLE_16)
            ctx.state.opinion.groups.values.forEach { it.shock -= ARTICLE16_LAW_COST }
            return@runCatching p
        }
        val p = createBill(PolicyKind.LAW_BILL, lawName(), changes, ctx.playerData.reforms?.voteDelayDays ?: LAW_DAYS, referendum)
        state.lawDraft = LawDraft()
        p
    }

    // ---- Aperçu d'un ensemble de changements ------------------------------------------------

    fun preview(changes: List<LeverChange>): Preview {
        val total = Preview()
        var maxDiff = 0.0
        var sumDiff = 0.0
        changes.forEach { c ->
            val p = if (c.measure != null && c.lever !in state.measures) Preview().also { levers.previewMeasure(it, c.measure, c.from, c.to) }
                else levers.preview(c.lever, c.from, c.to)
            total.add(p)
            maxDiff = maxOf(maxDiff, p.difficulty); sumDiff += p.difficulty
            if (p.censure > total.censure) { total.censure = p.censure; total.censureReason = p.censureReason }
        }
        // Un texte touffu coûte plus que sa mesure la plus difficile, moins que leur somme.
        total.difficulty = maxDiff + COMBINED * (sumDiff - maxDiff)
        if (changes.any { levers.lever(it.lever)?.constitutional == true }) total.difficulty += PolicyService.CONSTITUTIONAL_EXTRA
        return total
    }

    fun previewOf(c: LeverChange): Preview =
        if (c.measure != null && c.lever !in state.measures) Preview().also { levers.previewMeasure(it, c.measure, c.from, c.to) } else levers.preview(c.lever, c.from, c.to)

    /** Chance d'adoption au Parlement pour une difficulté donnée. */
    fun passChance(difficulty: Double, bonus: Double = 0.0): Double {
        val p = ctx.playerData.government?.parliament ?: return 0.0
        val z = (ctx.state.government.parliamentSupport + bonus - p.passThreshold - difficulty) / p.voteNoise.coerceAtLeast(1e-3)
        return (1 / (1 + kotlin.math.exp(-1.7 * z))).coerceIn(0.01, 0.99)
    }

    /** Part de « oui » estimée à un référendum (popularité du président et intérêt de chaque groupe). */
    fun referendumEstimate(preview: Preview): Double =
        fr.president.engine.government.ParliamentService(ctx).estimateWithInterest(preview.groups).first + preview.appeal * APPEAL_WEIGHT

    fun difficulty(p: PolicyProposal): Double = preview(p.changes).difficulty + if (p.kind == PolicyKind.BUDGET_BILL) BUDGET_BASE else 0.0

    // ---- Décrets ----------------------------------------------------------------------------

    fun decreeBlocker(id: String, value: Double): String? {
        val lever = levers.lever(id) ?: return "Réglage inconnu."
        if (lever.channel != Channel.DECREE) return "Ce réglage demande une loi."
        if (abs(value - levers.current(id)) < EPS) return "Déjà en vigueur."
        if (lever.irreversible && value < levers.current(id)) return "On ne peut pas revenir en arrière."
        if (pendingFor(id) != null) return "Un texte sur ce sujet est soumis au vote."
        if (state.contests.any { it.lever == id }) return "Le Conseil d'État examine déjà le précédent décret."
        state.lastDecree[id]?.let { last -> val left = DECREE_COOLDOWN - last.daysUntil(ctx.now); if (left > 0) return "Possible à nouveau dans ${kotlin.math.ceil(left).toInt()} jours." }
        return null
    }

    /** Un décret s'applique tout de suite ; s'il va trop loin, le Conseil d'État peut l'annuler. */
    fun decree(id: String, value: Double): Result<String> = runCatching {
        decreeBlocker(id, value)?.let { error(it) }
        val lever = levers.lever(id)!!
        val from = levers.current(id)
        val preview = levers.preview(id, from, value)
        val change = LeverChange(id, from, value)
        levers.apply(change)
        state.lastDecree[id] = ctx.now
        val title = "Décret : ${lever.label.replaceFirstChar { it.lowercase() }} (${levers.format(lever, value)})"
        state.bills += BillRecord(ctx.state.newId("bill"), title, Channel.DECREE, ctx.now, BillStatus.DECREE, listOf(change))
        JournalService(ctx).add("Décret", "${lever.label} : ${levers.format(lever, from)} → ${levers.format(lever, value)}", Tone.NEUTRAL)
        ctx.notifications.news(NotificationCategory.POLITICS, "Décret publié au Journal officiel : ${lever.label.lowercase()}")
        reactions(listOf(change), preview)
        if (preview.censure > 0) {
            state.contests += DecreeContest(id, from, value, ctx.now.plusDays(CONTEST_DAYS), preview.censure)
            "Décret publié. Des associations saisissent le Conseil d'État (décision dans un mois)."
        } else "Décret publié : en vigueur dès aujourd'hui."
    }

    // ---- Textes ----------------------------------------------------------------------------

    private fun createBill(kind: PolicyKind, title: String, changes: List<LeverChange>, delayDays: Int, referendum: Boolean = false): PolicyProposal {
        val at = ctx.now.plusDays(delayDays.toLong())
        val p = PolicyProposal(ctx.state.newId("pol"), kind, "bill", 0.0, 0.0, ctx.now,
            if (referendum) ctx.now.plusDays(REFERENDUM_DAYS.toLong()) else at,
            status = if (referendum) PolicyStatus.PENDING_REFERENDUM else PolicyStatus.PENDING_VOTE, title = title)
        p.changes += changes
        policy.proposals += p
        if (referendum) {
            state.referendums += BillReferendum(p.id, p.voteAt)
            ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Référendum convoqué : $title",
                "Les Français voteront le ${Formatting.date(p.voteAt)}. Le vote portera autant sur vous que sur le texte.")
        } else {
            ctx.scheduler.schedule(ScheduledAction.PolicyVote(p.voteAt, p.id))
            ctx.notifications.news(NotificationCategory.POLITICS, "Le gouvernement dépose : $title")
        }
        JournalService(ctx).add("Loi", "Dépôt : $title", Tone.NEUTRAL)
        return p
    }

    /** Un texte est adopté : contrôle du Conseil constitutionnel, entrée en vigueur, réactions. */
    fun enactBill(p: PolicyProposal, status: BillStatus) {
        val censured = mutableListOf<String>()
        val previews = p.changes.associate { it.lever to previewOf(it) }
        // Le peuple souverain n'est pas censuré : pas de contrôle pour une loi référendaire.
        if (status != BillStatus.REFERENDUM && status != BillStatus.ARTICLE_16) for (c in p.changes) {
            val risk = previews[c.lever]?.censure ?: 0.0
            if (risk > 0 && ctx.rng.chance(risk)) { c.censured = true; censured += (levers.lever(c.lever)?.label ?: c.lever) + " — " + (previews[c.lever]?.censureReason ?: "") }
        }
        val applied = p.changes.filter { !it.censured }
        applied.forEach { levers.apply(it, p.effectScale) }
        state.bills += BillRecord(p.id, p.title, if (p.kind == PolicyKind.BUDGET_BILL) Channel.BUDGET else Channel.LAW, ctx.now, status, p.changes.toList(), censured)
        if (state.bills.size > MAX_RECORDS) state.bills.removeAt(0)
        if (censured.isNotEmpty()) {
            val all = censured.size == p.changes.size
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT,
                if (all) "Le Conseil constitutionnel censure « ${p.title} »" else "Censure partielle de « ${p.title} »",
                "Articles censurés :\n" + censured.joinToString("\n") { "• $it" } + if (all) "" else "\n\nLe reste du texte entre en vigueur.")
            JournalService(ctx).add("Loi", "Censure du Conseil constitutionnel : ${p.title}", Tone.BAD)
            ctx.effects.trigger(EffectSpec("president.popularity", -CENSURE_COST * censured.size), null, emptyMap(), "censure")
        }
        if (applied.isNotEmpty()) reactions(applied, preview(applied))
        if (p.id == state.plfProposal) ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.IMPORTANT, "${p.title} adoptée",
            if (applied.isEmpty()) "Un budget de reconduction : les impôts et les dépenses restent ce qu'ils sont." else "${applied.size} changement(s) entrent en vigueur au 1er janvier.", journal = false)
    }

    /** Rejet par le Parlement. Un budget annuel rejeté : la loi spéciale reconduit l'ancien. */
    fun billRejected(p: PolicyProposal) {
        state.bills += BillRecord(p.id, p.title, if (p.kind == PolicyKind.BUDGET_BILL) Channel.BUDGET else Channel.LAW, ctx.now, BillStatus.REJECTED, p.changes.toList())
        if (state.bills.size > MAX_RECORDS) state.bills.removeAt(0)
        if (p.id == state.plfProposal) {
            ctx.effects.trigger(EffectSpec("economy.businessConfidence", -PLF_REJECTED), null, emptyMap(), "plf")
            ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.URGENT, "Le budget est rejeté",
                "Une loi spéciale reconduit le budget de l'an dernier : vos changements ne s'appliquent pas. Les marchés s'inquiètent. Vous pouvez passer en force (49.3) ou déposer un budget rectificatif.")
        }
    }

    /** Remet les changements d'un texte voté dans un projet, en sens inverse : l'abrogation. */
    fun abrogateBlocker(record: BillRecord): String? = when {
        record.status !in setOf(BillStatus.ADOPTED, BillStatus.FORCED, BillStatus.REFERENDUM, BillStatus.DECREE) -> "Ce texte n'est pas en vigueur."
        record.changes.none { !it.censured && revertible(it) } -> "Rien à abroger : ces changements ont déjà été modifiés ou sont irréversibles."
        else -> null
    }

    private fun revertible(c: LeverChange): Boolean {
        val l = levers.lever(c.lever) ?: c.measure?.let { levers.measureLever(it) } ?: return false
        return !l.irreversible && abs(levers.current(c.lever) - c.to) < EPS
    }

    fun abrogate(recordId: String): Result<String> = runCatching {
        val r = state.bills.first { it.id == recordId }
        abrogateBlocker(r)?.let { error(it) }
        var budget = 0
        var law = 0
        r.changes.filter { !it.censured && revertible(it) }.forEach { c ->
            val l = levers.lever(c.lever) ?: c.measure?.let { levers.measureLever(it) } ?: return@forEach
            when (l.channel) {
                Channel.BUDGET -> { setBudget(c.lever, c.from, c.measure); budget++ }
                else -> { addToLaw(c.lever, c.from, c.measure); law++ }
            }
        }
        if (law > 0 && state.lawDraft.name == null) state.lawDraft.name = "Loi d'abrogation : ${r.title}"
        listOfNotNull(if (law > 0) "$law changement(s) dans le projet de loi" else null, if (budget > 0) "$budget dans le projet de budget" else null)
            .joinToString(" et ").let { "Abrogation préparée : $it. Il reste à les faire voter." }
    }

    // ---- Anciennes commandes (un seul changement) -----------------------------------------------

    fun singleBudgetBill(id: String, value: Double): Result<PolicyProposal> = runCatching {
        // Un nouveau texte sur le même poste remplace le précédent, comme avant.
        pendingBills().filter { it.kind == PolicyKind.BUDGET_BILL && it.id != state.plfProposal && it.changes.size == 1 && it.changes[0].lever == id }
            .forEach { it.status = PolicyStatus.REJECTED }
        val lever = levers.lever(id) ?: error("Réglage inconnu.")
        val current = levers.current(id)
        if (pendingFor(id) != null) error("Déjà au vote.")
        if (abs(value - current) < EPS) error("Déjà en vigueur.")
        state.correctiveCount++
        createBill(PolicyKind.BUDGET_BILL, "Loi de finances rectificative : ${lever.label.replaceFirstChar { it.lowercase() }}",
            listOf(LeverChange(id, current, value)), ctx.playerData.government?.parliament?.taxVoteDelayDays ?: CORRECTIVE_DAYS)
    }

    fun singleLawBill(id: String, value: Double): Result<PolicyProposal> = runCatching {
        lawBlocker(id, value)?.let { error(it) }
        val current = levers.current(id)
        if (abs(value - current) < EPS) error("Déjà en vigueur.")
        val change = LeverChange(id, current, value)
        createBill(PolicyKind.LAW_BILL, autoName(listOf(change)), listOf(change), ctx.playerData.reforms?.voteDelayDays ?: LAW_DAYS)
    }

    // ---- Chaque jour ------------------------------------------------------------------------

    fun article16Active(): Boolean = state.article16Until?.let { it > ctx.now } == true

    /** Déclenche les pleins pouvoirs pour [days] jours. */
    fun startArticle16(days: Double) {
        state.article16Until = ctx.now.plusDays(days)
        state.libertyOffset -= ARTICLE16_LIBERTY
        JournalService(ctx).add("Article 16", "Le président exerce les pleins pouvoirs pendant ${days.toInt()} jours.", Tone.BAD)
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.URGENT, "Article 16 : pleins pouvoirs",
            "Jusqu'au ${Formatting.date(state.article16Until!!)}, vos projets de loi s'appliquent sans vote. Les libertés reculent, l'opposition crie au coup d'État.")
    }

    private fun article16Expiry() {
        val until = state.article16Until ?: return
        if (until > ctx.now) return
        state.article16Until = null
        state.libertyOffset += ARTICLE16_LIBERTY
        ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Fin des pleins pouvoirs",
            "Le Parlement retrouve ses droits. Les textes adoptés pendant l'article 16 restent en vigueur ; on vous en demandera compte.")
    }

    fun daily() {
        article16Expiry()
        annualBudget()
        expireMeasures()
        contests()
        referendums()
    }

    /** Chaque 1er octobre, le gouvernement dépose la loi de finances de l'année suivante. */
    private fun annualBudget() {
        val now = ctx.now.toDateTime()
        val year = now.year + 1
        if (state.plfYear >= year || now.monthValue < PLF_MONTH) return
        if (ctx.state.player.gameOver != null) return
        state.plfYear = year
        val vote = WorldTime.fromDateTime(LocalDateTime.of(now.year, PLF_VOTE_MONTH, PLF_VOTE_DAY, 15, 0))
            .let { if (ctx.now.daysUntil(it) < MIN_PLF_DAYS) ctx.now.plusDays(MIN_PLF_DAYS) else it }
        val p = PolicyProposal(ctx.state.newId("pol"), PolicyKind.BUDGET_BILL, "bill", 0.0, 0.0, ctx.now, vote, title = "Loi de finances pour $year")
        p.changes += budgetChanges()
        state.budgetDraft.clear()
        policy.proposals += p
        state.plfProposal = p.id
        ctx.scheduler.schedule(ScheduledAction.PolicyVote(vote, p.id))
        ctx.notifications.post(NotificationCategory.ECONOMY, Urgency.IMPORTANT, "Le budget $year est déposé au Parlement",
            "Vote le ${Formatting.date(vote)}. Jusqu'au ${Formatting.date(vote.plusDays(-AMEND_CLOSE_DAYS))}, tous vos réglages d'impôts et de dépenses y sont ajoutés (écran « Lois et budget »)." +
                if (p.changes.isEmpty()) " Pour l'instant, c'est un budget de reconduction." else " Il contient déjà ${p.changes.size} changement(s).")
    }

    private fun expireMeasures() {
        state.measures.values.filter { m -> m.until?.let { it <= ctx.now } == true }.toList().forEach { m ->
            val lever = levers.measureLever(m.config)
            levers.apply(LeverChange(m.config.key, m.value, 0.0, measure = m.config))
            ctx.notifications.post(NotificationCategory.POLITICS, Urgency.INFO, "Fin d'une mesure temporaire", "${lever?.label ?: m.config.key} : la mesure arrive à son terme.", journal = false)
        }
    }

    private fun contests() {
        val due = state.contests.filter { it.at <= ctx.now }
        state.contests.removeAll(due)
        due.forEach { c ->
            val lever = levers.lever(c.lever) ?: return@forEach
            if (ctx.rng.chance(c.chance) && abs(levers.current(c.lever) - c.to) < EPS) {
                levers.apply(LeverChange(c.lever, c.to, c.from))
                ctx.effects.trigger(EffectSpec("president.popularity", -CENSURE_COST), null, emptyMap(), "conseil_etat")
                ctx.notifications.post(NotificationCategory.POLITICS, Urgency.IMPORTANT, "Le Conseil d'État annule un décret",
                    "${lever.label} : le décret est annulé, l'ancienne règle (${levers.format(lever, c.from)}) s'applique de nouveau. Il faudra passer par la loi.")
                JournalService(ctx).add("Décret", "Annulé par le Conseil d'État : ${lever.label}", Tone.BAD)
            } else ctx.notifications.news(NotificationCategory.POLITICS, "Le Conseil d'État valide le décret : ${lever.label.lowercase()}")
        }
    }

    private fun referendums() {
        val due = state.referendums.filter { it.at <= ctx.now }
        state.referendums.removeAll(due)
        due.forEach { r ->
            val p = policy.proposals.firstOrNull { it.id == r.proposalId } ?: return@forEach
            val yes = (referendumEstimate(preview(p.changes)) + ctx.rng.nextGaussian() * REFERENDUM_NOISE).coerceIn(0.05, 0.95)
            if (yes > 0.5) {
                p.status = PolicyStatus.ADOPTED
                enactBill(p, BillStatus.REFERENDUM)
                ctx.effects.trigger(EffectSpec("president.popularity", REFERENDUM_WIN), null, emptyMap(), "referendum")
                ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Référendum : le oui l'emporte",
                    "${p.title} : ${Formatting.percent(yes)} de oui. Le texte entre en vigueur, sans contrôle du Conseil constitutionnel.")
            } else {
                p.status = PolicyStatus.REJECTED
                billRejected(p)
                ctx.state.opinion.groups.values.forEach { it.shock -= REFERENDUM_LOSS }
                ctx.notifications.post(NotificationCategory.ELECTIONS, Urgency.URGENT, "Référendum : le non l'emporte",
                    "${p.title} : seulement ${Formatting.percent(yes)} de oui. Un désaveu personnel.")
            }
        }
    }

    /** Ce que la rue et les syndicats retiennent d'un texte. */
    private fun reactions(applied: List<LeverChange>, total: Preview) {
        val unrest = fr.president.engine.politics.UnrestService(ctx)
        applied.forEach { c ->
            val key = c.lever.substringAfter(':')
            when {
                c.lever.startsWith("reform:") && c.to >= 0.5 -> unrest.onPolicy(PolicyKind.REFORM, key, 0)
                c.lever.startsWith("law:") -> ctx.playerData.laws?.laws?.firstOrNull { it.id == key }?.let { l ->
                    val opt = fr.president.engine.government.LawService(ctx).currentWeights(l).maxByOrNull { it.value }?.key ?: c.to.toInt()
                    unrest.onPolicy(PolicyKind.LAW, key, opt)
                }
                c.lever.startsWith("param:") -> levers.linkedReforms.filter { it in ctx.state.policy.adoptedReforms }.forEach { r ->
                    if (ctx.playerData.legislation?.parameters?.firstOrNull { it.id == key }?.links?.any { it.reform == r } == true && c.to > c.from) unrest.onPolicy(PolicyKind.REFORM, r, 0)
                }
            }
        }
        unrest.onDiscontent(total.groups)
    }

    companion object {
        const val ARTICLE16_LIBERTY = 15.0
        const val ARTICLE16_LAW_COST = 0.004
        val BILLS = setOf(PolicyKind.BUDGET_BILL, PolicyKind.LAW_BILL)
        val OPEN = setOf(PolicyStatus.PENDING_VOTE, PolicyStatus.PENDING_CENSURE, PolicyStatus.PENDING_REFERENDUM)
        const val EPS = 1e-6
        const val AMEND_CLOSE_DAYS = 5.0
        private const val CORRECTIVE_DAYS = 14
        private const val LAW_DAYS = 30
        private const val REFERENDUM_DAYS = 45
        private const val PLF_MONTH = 10
        private const val PLF_VOTE_MONTH = 12
        private const val PLF_VOTE_DAY = 15
        private const val MIN_PLF_DAYS = 20.0
        private const val MAX_LAW_BILLS = 3
        private const val COMBINED = 0.5
        private const val BUDGET_BASE = 0.0
        private const val APPEAL_WEIGHT = 0.3
        private const val DECREE_COOLDOWN = 120.0
        private const val CONTEST_DAYS = 30.0
        private const val MAX_RECORDS = 80
        private const val CENSURE_COST = 0.01
        private const val PLF_REJECTED = 0.015
        private const val REFERENDUM_NOISE = 0.04
        private const val REFERENDUM_WIN = 0.02
        private const val REFERENDUM_LOSS = 0.02
        private val DOMAIN_NAMES = mapOf(
            "societe" to "sur la société", "justice" to "pour la sécurité et la justice", "travail" to "pour le travail",
            "libertes" to "sur les libertés", "institutions" to "sur les institutions", "reformes" to "de réformes",
            "measures" to "de mesures ciblées", "solidarite" to "de solidarité", "securite" to "sur la sécurité routière",
        )
    }
}

/** Délai avant de pouvoir rechanger une loi du catalogue. */
internal object LawGate {
    fun cooldown(ctx: SimulationContext, lawId: String): String? {
        val last = ctx.state.laws.changedAt[lawId] ?: return null
        val left = fr.president.engine.government.LawService.COOLDOWN_DAYS - last.daysUntil(ctx.now)
        return if (left > 0) "Loi modifiée récemment : possible à nouveau dans ${kotlin.math.ceil(left).toInt()} jours." else null
    }
}

/** Budget annuel, mesures temporaires, recours contre les décrets, référendums. */
class LegislationSystem : SimulationSystem {
    override val name = "legislation"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        if (ctx.playerData.legislation == null) return
        LegislationService(ctx).daily()
    }
}
