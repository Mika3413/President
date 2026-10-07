package fr.president.engine.military

import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.stats.JournalService
import fr.president.engine.world.GameOver

/**
 * La décision nucléaire, selon la doctrine française : la dissuasion protège les intérêts vitaux ;
 * si un agresseur les menace, le président peut adresser un « ultime avertissement » (une frappe
 * unique sur un objectif militaire). L'échelle d'escalade mesure le danger ; face à une puissance
 * nucléaire, chaque marche peut entraîner une riposte, et l'échange stratégique met fin à tout.
 */
class NuclearService(private val ctx: SimulationContext) {
    private val state get() = ctx.state.defense
    private val geo get() = Geopolitics(ctx)
    private val player get() = ctx.state.player.countryId

    enum class Rung(val label: String, val text: String) {
        CALM("Dissuasion silencieuse", "La dissuasion joue son rôle sans un mot."),
        SIGNAL("Signaux", "Posture renforcée, essais ou déclarations : chacun montre ses muscles."),
        WARNING("Avertissement solennel", "Les intérêts vitaux sont invoqués publiquement."),
        STRIKE("Frappe d'avertissement", "Une arme nucléaire a été employée : le monde retient son souffle."),
        EXCHANGE("Échange nucléaire", "L'impensable."),
    }

    fun rung(): Rung = Rung.entries[state.escalation.coerceIn(0, Rung.entries.size - 1)]

    /** Agresseur contre lequel la frappe est concevable : il occupe notre sol, ou il a frappé le premier. */
    fun legitimateTarget(): String? {
        state.nuclearAttackBy?.let { if (geo.atWar(player, it)) return it }
        val occupiers = ctx.state.military.occupied.filter { (zone, occ) -> geo.ownerOf(zone) == player && geo.atWar(player, occ) }.values
        return occupiers.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    fun strikeBlocker(): String? = when {
        ctx.state.military.units.values.none { it.countryId == player && !it.destroyed && (it.type == SSBN || it.type == FIGHTER) } ->
            "Il faut un sous-marin lanceur d'engins ou les Forces aériennes stratégiques."
        legitimateTarget() == null -> "La doctrine l'interdit : seulement si un agresseur occupe le territoire national ou a employé l'arme nucléaire contre nous."
        state.lastNuclearStrike?.let { it.daysUntil(ctx.now) < STRIKE_COOLDOWN } == true -> "Un avertissement nucléaire vient d'être donné."
        else -> null
    }

    /** Cible militaire : la plus forte concentration de l'agresseur sur notre sol, sinon près du front. */
    fun target(enemy: String): String? {
        val units = ctx.state.military.units.values.filter { it.countryId == enemy && !it.destroyed && !ctx.db.zones.zone(it.zoneId).sea }
        val onOurSoil = units.filter { geo.ownerOf(it.zoneId) == player }
        return (onOurSoil.ifEmpty { units }).groupBy { it.zoneId }.maxByOrNull { (_, us) -> us.sumOf { it.strength } }?.key
    }

    /** L'ultime avertissement : une frappe unique sur un objectif militaire. */
    fun warningStrike(): Result<String> = runCatching {
        strikeBlocker()?.let { error(it) }
        val enemy = legitimateTarget()!!
        val zone = target(enemy) ?: error("Aucun objectif militaire identifié.")
        devastate(zone)
        state.lastNuclearStrike = ctx.now
        state.escalation = maxOf(state.escalation, Rung.STRIKE.ordinal)
        state.credibility = (state.credibility + 0.2).coerceAtMost(1.0)
        worldOutrage(player, ALLY_OUTRAGE, WORLD_OUTRAGE)
        ctx.effects.trigger(EffectSpec("opinion.national", -0.03), null, emptyMap(), "nuclear")
        val name = ctx.db.country(enemy).definition.name
        JournalService(ctx).add("Défense", "Ultime avertissement nucléaire contre ${fr.president.engine.data.CountryNames(ctx.db.country(enemy).definition).the}", Tone.BAD)
        ctx.notifications.news(NotificationCategory.MILITARY, "La France emploie l'arme nucléaire : frappe d'avertissement contre les forces ${fr.president.engine.data.CountryNames(ctx.db.country(enemy).definition).of}", zone)
        val war = geo.warBetween(player, enemy)
        when {
            geo.isNuclear(enemy) && ctx.rng.chance(RETALIATION_CHANCE) -> {
                enemyStrike(enemy)
                "L'avertissement est parti. $name riposte : une arme nucléaire frappe nos forces. Une décision vous attend."
            }
            war != null && ctx.rng.chance(if (geo.isNuclear(enemy)) NUCLEAR_ENEMY_YIELDS else YIELDS) -> {
                WarService(ctx).peace(war, keepOccupied = false, outcome = "Paix imposée par l'ultime avertissement nucléaire")
                "$name a compris : ses troupes se retirent et la paix est signée. Le monde entier vous regarde avec effroi."
            }
            else -> {
                war?.let { w -> w.weariness[enemy] = ((w.weariness[enemy] ?: 0.0) + 0.4).coerceAtMost(1.0) }
                "$name vacille mais poursuit la guerre. La pression internationale devient immense."
            }
        }
    }

    /** Frappe nucléaire ennemie sur nos forces : le président doit choisir. */
    fun enemyStrike(enemy: String) {
        val zone = ctx.state.military.units.values.filter { it.countryId == player && !it.destroyed && !ctx.db.zones.zone(it.zoneId).sea }
            .groupBy { it.zoneId }.maxByOrNull { (_, us) -> us.sumOf { it.strength } }?.key ?: return
        devastate(zone)
        state.nuclearAttackBy = enemy
        state.nuclearAttackAt = ctx.now
        state.escalation = maxOf(state.escalation, Rung.STRIKE.ordinal)
        worldOutrage(enemy, ALLY_OUTRAGE, WORLD_OUTRAGE)
        ctx.state.opinion.groups.values.forEach { it.shock -= 0.03 }
        val by = fr.president.engine.data.CountryNames(ctx.db.country(enemy).definition).the.replaceFirstChar { it.uppercase() }
        ctx.notifications.post(NotificationCategory.MILITARY, Urgency.URGENT, "$by a employé l'arme nucléaire contre nos forces",
            "Une frappe a anéanti nos unités (${BattlePlaces(ctx).name(zone)}). Décision nucléaire : panneau Défense ou note du jour. Chaque heure compte.", zone)
        JournalService(ctx).add("Défense", "Frappe nucléaire ennemie contre nos forces", Tone.BAD)
    }

    enum class Response(val label: String, val text: String) {
        MASSIVE("Riposte stratégique massive", "Toute la puissance de la Force océanique stratégique. L'agresseur sera anéanti ; s'il est une puissance nucléaire, nous aussi."),
        LIMITED("Riposte limitée", "Une frappe proportionnée sur un objectif militaire : rétablir la dissuasion, au risque de l'escalade."),
        RESTRAINT("Retenue et cessez-le-feu", "Ne pas répondre, saisir l'ONU et nos alliés : le monde se range derrière nous, mais certains y verront de la faiblesse."),
    }

    fun pendingDecision(): Boolean = state.nuclearAttackBy != null && state.decisionTaken != true

    fun respond(response: Response): Result<String> = runCatching {
        val enemy = state.nuclearAttackBy ?: error("Aucune décision en attente.")
        require(state.decisionTaken != true) { "La décision a déjà été prise." }
        state.decisionTaken = true
        val name = ctx.db.country(enemy).definition.name
        val war = geo.warBetween(player, enemy)
        when (response) {
            Response.MASSIVE -> {
                state.escalation = Rung.EXCHANGE.ordinal
                if (geo.isNuclear(enemy)) {
                    ctx.state.player.gameOver = GameOver(ctx.now, "L'échange nucléaire avec ${fr.president.engine.data.CountryNames(ctx.db.country(enemy).definition).the} a eu lieu. Il n'y a plus de vainqueur.")
                    "Les missiles sont partis des deux côtés."
                } else {
                    war?.let { WarService(ctx).peace(it, keepOccupied = false, outcome = "Capitulation après la riposte nucléaire française") }
                    worldOutrage(player, 0.2, 0.3)
                    ctx.state.countries[enemy]?.economy?.let { it.pendingOutputShock -= 0.3 }
                    "$name capitule. La France a riposté de toute sa force : le monde ne l'oubliera jamais."
                }
            }
            Response.LIMITED -> {
                val zone = target(enemy)
                if (zone != null) devastate(zone)
                state.credibility = (state.credibility + 0.1).coerceAtMost(1.0)
                if (geo.isNuclear(enemy) && ctx.rng.chance(LIMITED_ESCALATION)) {
                    state.escalation = Rung.EXCHANGE.ordinal
                    ctx.state.player.gameOver = GameOver(ctx.now, "La riposte limitée n'a pas suffi : l'escalade nucléaire a tout emporté.")
                    "L'escalade est hors de contrôle."
                } else {
                    war?.let { WarService(ctx).ceasefire(it, CEASEFIRE_DAYS) }
                    "La dissuasion est rétablie : $name accepte un cessez-le-feu de $CEASEFIRE_DAYS jours."
                }
            }
            Response.RESTRAINT -> {
                war?.let { if (ctx.rng.chance(RESTRAINT_CEASEFIRE)) WarService(ctx).ceasefire(it, CEASEFIRE_DAYS) }
                state.credibility = (state.credibility - 0.25).coerceAtLeast(0.1)
                ctx.state.countries.keys.filter { it != player && it != enemy }.forEach {
                    ctx.state.diplomacy.relation(it, player).memories += DiplomaticMemory("AID", 0.08, ctx.now, "retenue nucléaire")
                }
                ctx.effects.trigger(EffectSpec("president.popularity", -0.06), null, emptyMap(), "nuclear")
                "Vous retenez la main. Le monde salue votre sang-froid ; une partie du pays vous reproche votre faiblesse."
            }
        }.also { JournalService(ctx).add("Défense", "Décision nucléaire : ${response.label.lowercase()}", Tone.WARNING) }
    }

    /** Anéantissement d'une zone : unités détruites, ouvrages rasés, retombées. */
    private fun devastate(zoneId: String) {
        ctx.state.military.units.values.filter { it.zoneId == zoneId && !it.destroyed }.forEach { u ->
            u.destroyed = true
            geo.activeWars().firstOrNull { u.countryId in it.participants }?.casualties?.merge(u.countryId, u.personnel, Int::plus)
        }
        ctx.state.military.works.removeAll { it.zoneId == zoneId }
        ctx.state.military.damage[zoneId] = 1.0
        ctx.state.military.nuclearZones[zoneId] = ctx.now
        ctx.db.zones.zone(zoneId).department?.let { ctx.effects.apply("dept.$it.approval", -0.2) }
        ctx.state.playerCountry.economy.pendingOutputShock -= MARKET_SHOCK
    }

    private fun worldOutrage(culprit: String, allies: Double, others: Double) {
        ctx.state.countries.keys.filter { it != culprit }.forEach { c ->
            val hit = if (geo.allied(c, culprit)) allies else others
            ctx.state.diplomacy.relation(c, culprit).memories += DiplomaticMemory("WARNING", -hit, ctx.now, "emploi de l'arme nucléaire")
        }
    }

    companion object {
        const val SSBN = "SSBN_FORCE"
        const val FIGHTER = "FIGHTER_WING"
        private const val STRIKE_COOLDOWN = 30.0
        private const val ALLY_OUTRAGE = 0.08
        private const val WORLD_OUTRAGE = 0.2
        private const val RETALIATION_CHANCE = 0.35
        private const val NUCLEAR_ENEMY_YIELDS = 0.45
        private const val YIELDS = 0.8
        private const val LIMITED_ESCALATION = 0.35
        private const val RESTRAINT_CEASEFIRE = 0.7
        private const val CEASEFIRE_DAYS = 90
        private const val MARKET_SHOCK = 0.02
    }
}

/**
 * Une puissance nucléaire en guerre contre la France, acculée (sa capitale prise, ou une grande
 * partie de son sol occupée), peut employer l'arme la première contre nos forces.
 */
class NuclearSystem : SimulationSystem {
    override val name = "nuclear"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val geo = Geopolitics(ctx)
        val player = ctx.state.player.countryId
        val defense = ctx.state.defense
        // L'échelle redescend lentement quand la paix revient.
        if (!geo.isAtWar(player) && defense.escalation > 0 && ctx.now.dayIndex % DECAY_DAYS == 0L) defense.escalation--
        if (defense.nuclearAttackBy != null && defense.decisionTaken == true && !geo.isAtWar(player)) {
            defense.nuclearAttackBy = null
            defense.decisionTaken = null
        }
        if (defense.nuclearAttackBy != null) return
        for (enemy in geo.enemiesOf(player).filter { geo.isNuclear(it) }) {
            val home = geo.territoryOf(enemy).size.coerceAtLeast(1)
            val lost = ctx.state.military.occupied.count { (z, occ) -> geo.ownerOf(z) == enemy && (occ == player || occ in geo.coBelligerents(player)) }
            val capital = ctx.db.country(enemy).definition.strategic.capital
            val capitalZone = capital?.let { ctx.db.zones.nearest(it.lon, it.lat) { z -> !z.sea && z.owner == enemy }?.id }
            val cornered = lost.toDouble() / home > CORNERED_SHARE || (capitalZone != null && geo.controllerOf(capitalZone) != enemy)
            if (cornered && ctx.rng.chance(FIRST_USE_CHANCE)) {
                NuclearService(ctx).enemyStrike(enemy)
                return
            }
        }
    }

    private companion object {
        const val DECAY_DAYS = 60L
        const val CORNERED_SHARE = 0.2
        const val FIRST_USE_CHANCE = 0.03
    }
}
