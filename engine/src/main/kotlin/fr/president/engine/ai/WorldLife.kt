package fr.president.engine.ai

import fr.president.engine.data.CountryNames
import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.diplomacy.RelationCalculator
import fr.president.engine.military.Geopolitics
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.politics.Traits
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.time.WorldTime
import fr.president.engine.util.clamp01
import kotlinx.serialization.Serializable

@Serializable
data class WorldCondition(val `var`: String, val min: Double? = null, val max: Double? = null)

@Serializable
data class WorldEffect(val target: String, val amount: Double)

/** Un événement possible dans un pays étranger (ou entre deux pays). Voir tools/datagen/world_events_fr.py. */
@Serializable
data class WorldEventDef(
    val id: String,
    val category: String,
    val headline: String,
    val detail: String = "",
    /** Probabilité mensuelle (par pays, ou par mois pour un événement entre deux pays). */
    val chance: Double,
    val effects: List<WorldEffect> = emptyList(),
    val conditions: List<WorldCondition> = emptyList(),
    val bilateral: Boolean = false,
    /** Ce que cela change pour la France, en clair. */
    val france: String = "",
    val tone: Tone = Tone.NEUTRAL,
    /** Assez grave pour une notification au président. */
    val major: Boolean = false,
)

@Serializable
data class WorldEventsFile(val events: List<WorldEventDef>)

/** Une entrée du journal du monde. */
@Serializable
data class WorldEntry(
    val time: WorldTime,
    val countries: List<String>,
    val category: String,
    val headline: String,
    val detail: String = "",
    val france: String = "",
    val tone: Tone = Tone.NEUTRAL,
)

@Serializable
class WorldJournal(val entries: MutableList<WorldEntry> = mutableListOf()) {
    fun add(e: WorldEntry) {
        entries += e
        if (entries.size > MAX) entries.removeAt(0)
    }

    companion object { const val MAX = 500 }
}

/**
 * La vie des autres pays : chaque mois, selon la situation de chacun (popularité, croissance,
 * chômage, inflation, dette, guerre, tempérament du dirigeant) et leurs relations, des événements
 * intérieurs et bilatéraux surviennent, avec leurs conséquences (popularité, économie, relations,
 * cours des matières premières, parfois la France). Tout est consigné au journal du monde.
 */
class WorldLifeSystem : SimulationSystem {
    override val name = "world-life"
    override val cadence = Cadence.MONTHLY

    override fun run(ctx: SimulationContext) {
        val file = ctx.db.worldEvents ?: return
        val life = WorldLife(ctx)
        val player = ctx.state.player.countryId
        val foreign = ctx.state.countries.keys.filter { it != player }
        for (a in foreign) {
            var count = 0
            for (def in ctx.rng.shuffled(file.events.filter { !it.bilateral })) {
                if (count >= MAX_PER_COUNTRY) break
                if (life.eligible(def, a, null) && ctx.rng.chance(def.chance)) { life.happen(def, a, null); count++ }
            }
        }
        for (def in file.events.filter { it.bilateral }) {
            if (!ctx.rng.chance(def.chance)) continue
            repeat(PAIR_TRIES) {
                val a = ctx.rng.pick(foreign)
                val b = ctx.rng.pick(foreign - a)
                if (life.eligible(def, a, b)) { life.happen(def, a, b); return@repeat }
            }
        }
    }

    private companion object {
        const val MAX_PER_COUNTRY = 2
        const val PAIR_TRIES = 12
    }
}

class WorldLife(private val ctx: SimulationContext) {
    private val geo by lazy { Geopolitics(ctx) }
    private val relations by lazy { RelationCalculator(ctx) }

    fun eligible(def: WorldEventDef, a: String, b: String?): Boolean = def.conditions.all { c ->
        val v = value(c.`var`, a, b) ?: return@all false
        (c.min == null || v >= c.min) && (c.max == null || v <= c.max)
    }

    private fun value(v: String, a: String, b: String?): Double? {
        val country = ctx.state.countries[a] ?: return null
        val e = country.economy
        val def = ctx.db.country(a).definition
        val leader = ctx.state.characters[country.leaderId]
        fun bool(x: Boolean) = if (x) 1.0 else 0.0
        return when (v) {
            "approval" -> country.leaderApproval
            "growth" -> e.realGrowth
            "unemployment" -> e.unemployment
            "inflation" -> e.inflation
            "debt" -> e.debtRatio
            "gdp" -> e.gdpBillions
            "atWar" -> bool(geo.isAtWar(a))
            "aggressiveness" -> leader?.trait(Traits.AGGRESSIVENESS) ?: 0.5
            "openness" -> leader?.trait(Traits.OPENNESS) ?: 0.5
            "eu" -> bool(ctx.db.alliances.any { it.id == "EU" && a in it.members })
            "nato" -> bool(ctx.db.alliances.any { it.id == "NATO" && a in it.members })
            "relationFrance" -> relations.score(a, ctx.state.player.countryId)
            "military" -> def.strategic.militaryBudgetBillions
            "seismic" -> bool("earthquake" in def.strategic.hazards)
            "hot" -> bool("wildfire" in def.strategic.hazards)
            "tourist" -> bool(a in TOURIST)
            "neighborEurope" -> bool(a in BORDER_EUROPE)
            "arms" -> bool(a in ARMS_EXPORTERS)
            else -> when {
                v.startsWith("producer_") -> ctx.db.trade?.commodities?.firstOrNull { it.id == v.removePrefix("producer_") }?.producers?.get(a) ?: 0.0
                b == null -> null
                v == "pairRelation" -> (relations.score(a, b) + relations.score(b, a)) / 2
                v == "otherGdp" -> ctx.state.countries[b]?.economy?.gdpBillions
                v == "neighbors" -> bool(b in geo.landNeighbors(a))
                v == "richer" -> bool((ctx.state.countries[b]?.economy?.gdpBillions ?: 0.0) * 1.5 < e.gdpBillions)
                else -> null
            }
        }
    }

    fun happen(def: WorldEventDef, a: String, b: String?) {
        val headline = text(def.headline, a, b).replaceFirstChar { it.uppercase() }
        val detail = text(def.detail, a, b)
        val france = text(def.france, a, b)
        def.effects.forEach { apply(it, a, b) }
        ctx.state.world.add(WorldEntry(ctx.now, listOfNotNull(a, b), def.category, headline, detail, france, def.tone))
        if (def.major) ctx.notifications.post(NotificationCategory.DIPLOMACY, Urgency.IMPORTANT, headline,
            detail + if (france.isNotEmpty()) "\nPour la France : $france" else "", a, journal = false, world = false)
        else ctx.notifications.news(NotificationCategory.DIPLOMACY, headline, a, world = false)
    }

    private fun apply(fx: WorldEffect, a: String, b: String?) {
        val p = fx.target.split('.')
        fun country(code: String) = ctx.state.countries[code]
        when (p[0]) {
            "A", "B" -> {
                val c = country(if (p[0] == "A") a else b ?: return) ?: return
                when (p[1]) {
                    "approval" -> c.leaderApproval = (c.leaderApproval + fx.amount).clamp01()
                    "output" -> c.economy.pendingOutputShock += fx.amount
                    "unemployment" -> c.economy.unemployment = (c.economy.unemployment + fx.amount).coerceAtLeast(0.01)
                    "inflation" -> c.economy.inflation += fx.amount
                    "confidence" -> {
                        c.economy.consumerConfidence = (c.economy.consumerConfidence + fx.amount).clamp01()
                        c.economy.businessConfidence = (c.economy.businessConfidence + fx.amount).clamp01()
                    }
                    "leader" -> if (fx.amount > 0) WorldPoliticsSystem.newLeader(ctx, c.id, announce = false)
                }
            }
            "AB" -> if (b != null) {
                ctx.state.diplomacy.relation(a, b).memories += DiplomaticMemory(p[1], fx.amount, ctx.now)
                ctx.state.diplomacy.relation(b, a).memories += DiplomaticMemory(p[1], fx.amount, ctx.now)
            }
            "commodity" -> ctx.state.trade.commodities[p[1]]?.let { it.price *= 1 + fx.amount }
            else -> {
                val target = fx.target.replace("{A}", a).replace("{B}", b ?: "")
                if (fx.amount != 0.0) ctx.effects.apply(target, fx.amount)
            }
        }
    }

    private fun text(t: String, a: String, b: String?): String {
        var s = t
        fun put(prefix: String, code: String) {
            val n = CountryNames(ctx.db.country(code).definition)
            val name = ctx.db.country(code).definition.name
            s = s.replace("{le_$prefix}", n.the).replace("{de_$prefix}", n.of).replace("{en_$prefix}", n.inside).replace("{$prefix}", name)
        }
        put("A", a)
        b?.let { put("B", it) }
        return s
    }

    private companion object {
        val TOURIST = setOf("ESP", "ITA", "GRC", "PRT", "MAR", "TUR", "TUN", "USA", "GBR", "EGY")
        val BORDER_EUROPE = setOf("ITA", "ESP", "GRC", "POL", "TUR", "MAR", "TUN", "DZA", "EGY", "ROU")
        val ARMS_EXPORTERS = setOf("USA", "RUS", "CHN", "GBR", "DEU", "ITA", "ESP", "TUR", "SWE", "ISR", "KOR")
    }
}
