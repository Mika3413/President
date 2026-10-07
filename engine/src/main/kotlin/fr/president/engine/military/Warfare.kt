package fr.president.engine.military

import fr.president.engine.data.GameDatabase
import fr.president.engine.effects.EffectSpec
import fr.president.engine.notifications.NotificationCategory
import fr.president.engine.notifications.Urgency
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- Données

@Serializable
data class FortLevelDef(val label: String, val costBillions: Double, val days: Int, val effects: Map<String, Double> = emptyMap())

@Serializable
data class FortTypeDef(
    val id: String,
    val label: String,
    val icon: String,
    val description: String,
    val levels: List<FortLevelDef>,
    val coastalOnly: Boolean = false,
)

@Serializable
data class TerrainDef(val id: String, val label: String, val icon: String, val defense: Double, val categories: Map<String, Double> = emptyMap(), val hint: String = "")

@Serializable
data class TerrainArea(val terrain: String, val lon: Double, val lat: Double, val rx: Double, val ry: Double, val name: String = "")

@Serializable
data class RiverDef(val name: String, val points: List<List<Double>>)

@Serializable
data class MatchupDef(val attacker: String, val defender: String, val factor: Double, val label: String = "")

@Serializable
data class WarfareFile(
    val fortifications: List<FortTypeDef> = emptyList(),
    val terrains: List<TerrainDef> = emptyList(),
    val areas: List<TerrainArea> = emptyList(),
    val rivers: List<RiverDef> = emptyList(),
    val matchups: List<MatchupDef> = emptyList(),
    val categories: Map<String, String> = emptyMap(),
    val urbanMinMillions: Double = 1.0,
    val urbanRadiusKm: Double = 70.0,
    val riverCrossing: Double = 0.75,
    val amphibiousPenalty: Double = 0.8,
    val artilleryVsFortification: Double = 0.5,
)

// ---------------------------------------------------------------- État

/** Ouvrage militaire bâti dans une zone : ligne de défense, défense sol-air, dépôt... */
@Serializable
class Fortification(
    val id: String,
    val type: String,
    val zoneId: String,
    var countryId: String,
    /** Niveau achevé (0 : premier chantier en cours). */
    var level: Int = 0,
    /** État (1 = intact) : les combats et les frappes l'abîment, le génie le répare. */
    var condition: Double = 1.0,
    /** Niveau visé par le chantier en cours, et date d'achèvement. */
    var building: Int = 0,
    var readyAt: WorldTime? = null,
)

// ---------------------------------------------------------------- Terrain

/** Relief de chaque zone, fleuves à franchir et forces/faiblesses des types d'unités. */
class Terrain(private val ctx: SimulationContext) {
    private val file get() = ctx.db.warfare

    fun of(zoneId: String): TerrainDef? {
        val f = file ?: return null
        val id = map(ctx).getOrElse(zoneId) { "PLAINS" }
        return f.terrains.firstOrNull { it.id == id }
    }

    fun category(typeId: String): String = ctx.db.unitTypes[typeId]?.category.orEmpty()

    fun categoryLabel(category: String): String = file?.categories?.get(category) ?: category

    /** Multiplicateur du terrain pour un type d'unité (les chars en montagne, l'infanterie en ville). */
    fun unitFactor(zoneId: String, typeId: String): Double = of(zoneId)?.categories?.get(category(typeId)) ?: 1.0

    fun defenseFactor(zoneId: String): Double = of(zoneId)?.defense ?: 1.0

    fun matchup(attackerCategory: String, defenderCategory: String): MatchupDef? =
        file?.matchups?.firstOrNull { it.attacker == attackerCategory && it.defender == defenderCategory }

    /** Le passage d'une zone à l'autre traverse-t-il un grand fleuve ? Renvoie son nom. */
    fun riverBetween(from: String, to: String): String? {
        val f = file ?: return null
        val a = ctx.db.zones.zones[from] ?: return null
        val b = ctx.db.zones.zones[to] ?: return null
        val key = if (from < to) "$from|$to" else "$to|$from"
        return crossings(ctx.db).getOrPut(key) {
            f.rivers.firstOrNull { r -> r.points.zipWithNext().any { (p, q) -> intersects(a.lon, a.lat, b.lon, b.lat, p[0], p[1], q[0], q[1]) } }?.name ?: ""
        }.ifEmpty { null }
    }

    private fun intersects(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, x4: Double, y4: Double): Boolean {
        fun cross(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double) = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        val d1 = cross(x3, y3, x4, y4, x1, y1)
        val d2 = cross(x3, y3, x4, y4, x2, y2)
        val d3 = cross(x1, y1, x2, y2, x3, y3)
        val d4 = cross(x1, y1, x2, y2, x4, y4)
        return d1 * d2 < 0 && d3 * d4 < 0
    }

    companion object {
        private val maps = java.util.WeakHashMap<GameDatabase, Map<String, String>>()
        private val rivers = java.util.WeakHashMap<GameDatabase, HashMap<String, String>>()

        private fun crossings(db: GameDatabase): HashMap<String, String> = synchronized(rivers) { rivers.getOrPut(db) { HashMap() } }

        /** Terrain de chaque zone terrestre : ville, puis la première grande région de relief qui la contient. */
        fun map(ctx: SimulationContext): Map<String, String> = synchronized(maps) {
            maps.getOrPut(ctx.db) {
                val f = ctx.db.warfare ?: return@getOrPut emptyMap()
                val cities = WorldCities(ctx).all().filter { it.populationMillions >= f.urbanMinMillions || it.capital }
                ctx.db.zones.zones.values.filter { !it.sea }.associate { z ->
                    val urban = cities.any { ZoneGraph.haversine(z.lon, z.lat, it.lon, it.lat) <= f.urbanRadiusKm }
                    val area = f.areas.firstOrNull { a ->
                        val dx = (z.lon - a.lon) / a.rx
                        val dy = (z.lat - a.lat) / a.ry
                        dx * dx + dy * dy <= 1.0
                    }
                    z.id to (if (urban) "URBAN" else area?.terrain ?: "PLAINS")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Ouvrages

/**
 * Fortifications et bâtiments militaires : construction (coût, délai), niveaux, effets en
 * combat, prise par l'ennemi. Les effets sont lus ici par les autres systèmes militaires.
 */
class FortificationService(private val ctx: SimulationContext) {
    private val file get() = ctx.db.warfare
    private val works get() = ctx.state.military.works
    private val geo get() = Geopolitics(ctx)
    private val player get() = ctx.state.player.countryId

    fun types(): List<FortTypeDef> = file?.fortifications.orEmpty()
    fun def(type: String): FortTypeDef? = types().firstOrNull { it.id == type }

    fun at(zoneId: String): List<Fortification> = works.filter { it.zoneId == zoneId }
    fun of(country: String): List<Fortification> = works.filter { it.countryId == country }
    fun work(zoneId: String, type: String): Fortification? = works.firstOrNull { it.zoneId == zoneId && it.type == type }

    /** Valeur d'un effet d'un ouvrage achevé dans la zone (tenant compte de son état). */
    fun effect(zoneId: String, type: String, key: String, owners: Set<String>? = null): Double {
        val w = work(zoneId, type) ?: return 0.0
        if (w.level <= 0 || (owners != null && w.countryId !in owners)) return 0.0
        return value(w, key)
    }

    fun value(w: Fortification, key: String): Double {
        val level = def(w.type)?.levels?.getOrNull(w.level - 1) ?: return 0.0
        return (level.effects[key] ?: 0.0) * w.condition
    }

    /** Effet le plus fort d'un type d'ouvrage couvrant la zone (rayon d'action), parmi ceux des pays donnés. */
    fun coverage(zoneId: String, type: String, key: String, owners: Set<String>): Double {
        val zone = ctx.db.zones.zones[zoneId] ?: return 0.0
        return works.filter { it.type == type && it.level > 0 && it.countryId in owners }.maxOfOrNull { w ->
            val radius = def(w.type)?.levels?.getOrNull(w.level - 1)?.effects?.get("radiusKm") ?: 0.0
            val z = ctx.db.zones.zones[w.zoneId] ?: return@maxOfOrNull 0.0
            if (w.zoneId == zoneId || ZoneGraph.haversine(z, zone) <= radius) value(w, key) else 0.0
        } ?: 0.0
    }

    /** Défense sol-air couvrant une zone, renforcée par les radars. */
    fun airDefense(zoneId: String, owners: Set<String>): Double =
        coverage(zoneId, "air_defense", "airDefense", owners) * (1 + coverage(zoneId, "radar", "radar", owners))

    fun interception(zoneId: String, owners: Set<String>): Double =
        (coverage(zoneId, "air_defense", "interception", owners) * (1 + coverage(zoneId, "radar", "radar", owners))).coerceAtMost(MAX_INTERCEPTION)

    fun constructions(country: String): List<Fortification> = works.filter { it.countryId == country && it.readyAt != null }

    fun blocker(type: String, zoneId: String, country: String = player): String? {
        val def = def(type) ?: return "Ouvrage inconnu."
        val zone = ctx.db.zones.zones[zoneId] ?: return "Zone inconnue."
        if (zone.sea) return "Impossible en mer."
        if (geo.controllerOf(zoneId) != country) return "La zone doit être sous notre contrôle."
        if (def.coastalOnly && !zone.coastal) return "Réservé aux zones côtières."
        val w = work(zoneId, type)
        if (w?.readyAt != null) return "Chantier en cours."
        if ((w?.level ?: 0) >= def.levels.size) return "Niveau maximal atteint."
        if (country == player && constructions(country).size >= MAX_SITES) return "Le génie militaire mène déjà $MAX_SITES chantiers."
        if (ctx.state.military.units.values.any { it.zoneId == zoneId && it.inCombat }) return "Impossible pendant les combats."
        return null
    }

    fun nextLevel(type: String, zoneId: String): FortLevelDef? = def(type)?.levels?.getOrNull(work(zoneId, type)?.level ?: 0)

    /** Durée du chantier : plus rapide en économie de guerre. */
    fun days(level: FortLevelDef, country: String = player): Long =
        (level.days * if (country == player && ctx.state.military.stocks.warEconomy) WAR_ECONOMY_SPEED else 1.0).toLong().coerceAtLeast(1)

    fun build(type: String, zoneId: String, country: String = player): Result<String> = runCatching {
        blocker(type, zoneId, country)?.let { error(it) }
        val def = def(type)!!
        val w = work(zoneId, type) ?: Fortification(ctx.state.newId("fort"), type, zoneId, country).also { works += it }
        val level = def.levels[w.level]
        w.building = w.level + 1
        w.readyAt = ctx.now.plusDays(days(level, country))
        if (country == player) {
            ctx.effects.trigger(EffectSpec("budget.oneOff", level.costBillions, days = level.days.toDouble()), null, emptyMap(), w.id)
            // Les chantiers font travailler le BTP.
            ctx.effects.trigger(EffectSpec("economy.output", level.costBillions / ctx.state.playerCountry.economy.gdpBillions * 0.5, days = level.days.toDouble()), null, emptyMap(), w.id)
        }
        "${level.label} : chantier lancé (${days(level, country)} jours, ${fr.president.engine.util.Formatting.billions(level.costBillions)})."
    }

    /** Chantiers achevés, réparations, harcèlement côtier. Appelé chaque jour. */
    fun daily() {
        for (w in works) {
            val ready = w.readyAt
            if (ready != null && ready <= ctx.now) {
                w.level = w.building
                w.readyAt = null
                w.condition = 1.0
                if (w.countryId == player) {
                    val def = def(w.type)
                    ctx.notifications.post(NotificationCategory.MILITARY, Urgency.INFO, "Ouvrage achevé : ${def?.levels?.getOrNull(w.level - 1)?.label ?: def?.label}",
                        "${def?.label} opérationnel (niveau ${w.level}).", w.zoneId)
                }
            }
            val fighting = ctx.state.military.units.values.any { it.zoneId == w.zoneId && it.inCombat }
            if (!fighting && w.condition < 1.0) w.condition = (w.condition + REPAIR_PER_DAY).coerceAtMost(1.0)
        }
        coastalFire()
    }

    private fun coastalFire() {
        for (w in works.filter { it.type == "coastal" && it.level > 0 }) {
            val damage = value(w, "naval")
            if (damage <= 0) continue
            val near = ctx.db.zones.neighbors(w.zoneId).filter { it.sea }.map { it.id }.toSet()
            ctx.state.military.units.values.filter { !it.destroyed && it.zoneId in near && geo.atWar(it.countryId, w.countryId) }
                .forEach { it.strength = (it.strength - damage).coerceAtLeast(MIN_STRENGTH) }
        }
    }

    /** La zone change de mains : les ouvrages passent à l'ennemi, abîmés ; les chantiers sont perdus. */
    fun onCapture(zoneId: String, by: String) {
        for (w in at(zoneId)) {
            if (w.countryId == by) continue
            w.countryId = by
            w.condition *= CAPTURE_CONDITION
            if (w.readyAt != null) { w.readyAt = null; w.building = w.level }
        }
        works.removeAll { it.zoneId == zoneId && it.level <= 0 }
    }

    /** Usure des ouvrages d'une zone pendant une heure de combat (l'artillerie double les dégâts). */
    fun batter(zoneId: String, artilleryShare: Double) {
        at(zoneId).forEach { it.condition = (it.condition - BATTER_PER_HOUR * (1 + artilleryShare)).coerceAtLeast(MIN_CONDITION) }
    }

    /** Frappe : les ouvrages de la zone sont endommagés. */
    fun strikeDamage(zoneId: String) {
        at(zoneId).forEach { it.condition = (it.condition - STRIKE_DAMAGE).coerceAtLeast(MIN_CONDITION) }
    }

    /** Accélération de la production d'unités (casernes), plafonnée. */
    fun productionBonus(country: String): Double =
        of(country).filter { it.type == "barracks" && it.level > 0 }.sumOf { value(it, "production") }.coerceAtMost(MAX_PRODUCTION_BONUS)

    fun trainingBonus(country: String): Double =
        of(country).filter { it.type == "barracks" && it.level > 0 }.maxOfOrNull { value(it, "training") } ?: 0.0

    /** Zones d'où part le ravitaillement : dépôts tenus par le pays. */
    fun depots(country: String): List<Fortification> =
        of(country).filter { it.type == "depot" && it.level > 0 && geo.controllerOf(it.zoneId) == country }

    /** Ouvrages de départ : les lignes réelles d'Ukraine, de Russie, de Pologne, de Finlande ; la défense de Paris. */
    fun seed() {
        val military = ctx.state.military
        if (military.worksSeeded || file == null) return
        military.worksSeeded = true
        fun border(country: String, other: Set<String>) = ctx.db.zones.ownedBy(country)
            .filter { z -> ctx.db.zones.neighbors(z.id).any { !it.sea && it.owner in other } }.map { it.id }
        fun place(type: String, zone: String, country: String, level: Int) {
            if (country !in ctx.state.countries && country != player) return
            if (work(zone, type) == null) works += Fortification(ctx.state.newId("fort"), type, zone, country, level)
        }
        border("UKR", setOf("RUS", "BLR")).forEach { place("line", it, "UKR", 2) }
        border("RUS", setOf("UKR")).forEach { place("line", it, "RUS", 2) }
        border("POL", setOf("RUS", "BLR")).forEach { place("line", it, "POL", 1) }
        border("FIN", setOf("RUS")).forEach { place("line", it, "FIN", 1) }
        border("KOR", setOf("PRK")).forEach { place("line", it, "KOR", 3) }
        val capital = ctx.playerData.definition.strategic.capital
        if (capital != null) {
            val paris = MilitarySetup(ctx).zoneFor(player, capital.lon, capital.lat, false)
            place("air_defense", paris, player, 1)
            place("radar", paris, player, 1)
        }
        listOf(-4.5 to 48.4, 5.9 to 43.1).forEach { (lon, lat) ->
            val z = ctx.db.zones.nearest(lon, lat) { !it.sea && it.owner == player && it.coastal }
            if (z != null) place("coastal", z.id, player, 1)
        }
    }

    companion object {
        const val MAX_SITES = 4
        private const val WAR_ECONOMY_SPEED = 0.7
        private const val REPAIR_PER_DAY = 0.02
        private const val CAPTURE_CONDITION = 0.5
        private const val BATTER_PER_HOUR = 0.004
        private const val STRIKE_DAMAGE = 0.15
        private const val MIN_CONDITION = 0.1
        private const val MIN_STRENGTH = 0.05
        private const val MAX_INTERCEPTION = 0.8
        private const val MAX_PRODUCTION_BONUS = 0.3
    }
}

/**
 * Chaque jour : chantiers achevés, réparations, tirs des batteries côtières ; les pays IA en
 * guerre fortifient leurs zones de front (lignes de défense, puis dépôts près du front).
 */
class FortificationSystem : fr.president.engine.simulation.SimulationSystem {
    override val name = "fortifications"
    override val cadence = fr.president.engine.simulation.Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        if (ctx.db.warfare == null) return
        val service = FortificationService(ctx)
        service.seed()
        service.daily()
        aiBuild(ctx, service)
    }

    private fun aiBuild(ctx: SimulationContext, service: FortificationService) {
        val geo = Geopolitics(ctx)
        val player = ctx.state.player.countryId
        for (country in geo.activeWars().flatMap { it.participants }.distinct().filter { it != player && it in ctx.state.countries }) {
            if (service.constructions(country).size >= AI_SITES || service.of(country).size >= AI_MAX_WORKS) continue
            // Zone de front la plus menacée : le plus d'ennemis en face, sans ligne achevée au maximum.
            val enemies = geo.enemiesOf(country)
            val front = (geo.territoryOf(country) + ctx.state.military.occupied.filter { it.value == country }.keys)
                .filter { z -> geo.controllerOf(z) == country && ctx.db.zones.neighbors(z).any { !it.sea && geo.controllerOf(it.id) in enemies } }
            val target = front.filter { service.blocker("line", it, country) == null }
                .maxByOrNull { z -> ctx.db.zones.neighbors(z).sumOf { n -> ctx.state.military.units.values.count { it.zoneId == n.id && it.countryId in enemies && !it.destroyed } } }
                ?: continue
            if (ctx.state.time.dayIndex % AI_PERIOD_DAYS == (country.hashCode() and 0x7fffffff) % AI_PERIOD_DAYS) service.build("line", target, country)
        }
    }

    private companion object {
        const val AI_SITES = 2
        const val AI_MAX_WORKS = 12
        const val AI_PERIOD_DAYS = 10L
    }
}

/** Nom d'un lieu de bataille : la grande ville la plus proche, sinon le pays. */
class BattlePlaces(private val ctx: SimulationContext) {
    /** « Bataille de Kharkiv », « Bataille d'Odessa », « Bataille en Ukraine », « Bataille au Royaume-Uni ». */
    fun title(zoneId: String): String {
        val zone = ctx.db.zones.zones[zoneId] ?: return "Bataille"
        if (zone.sea) return "Bataille navale"
        val city = cityNear(zoneId)
        if (city != null) return "Bataille " + fr.president.engine.util.Formatting.contract(if (city.first().lowercaseChar() in VOWELS) "d'$city" else "de $city")
        zone.department?.let { d -> ctx.playerData.territory?.departments?.firstOrNull { it.code == d }?.let { return "Bataille de ${it.name}" } }
        val owner = Geopolitics(ctx).ownerOf(zoneId)
        return ctx.db.countries[owner]?.let { "Bataille " + fr.president.engine.data.CountryNames(it.definition).inside } ?: "Bataille"
    }

    private fun cityNear(zoneId: String): String? {
        val zone = ctx.db.zones.zones[zoneId] ?: return null
        val city = WorldCities(ctx).all().minByOrNull { ZoneGraph.haversine(zone.lon, zone.lat, it.lon, it.lat) } ?: return null
        return city.name.takeIf { ZoneGraph.haversine(zone.lon, zone.lat, city.lon, city.lat) < NEAR_KM }
    }

    fun name(zoneId: String): String {
        val zone = ctx.db.zones.zones[zoneId] ?: return zoneId
        if (zone.sea) return "en mer"
        val city = WorldCities(ctx).all().minByOrNull { ZoneGraph.haversine(zone.lon, zone.lat, it.lon, it.lat) }
        if (city != null && ZoneGraph.haversine(zone.lon, zone.lat, city.lon, city.lat) < NEAR_KM) return city.name
        val owner = Geopolitics(ctx).ownerOf(zoneId)
        zone.department?.let { d -> ctx.playerData.territory?.departments?.firstOrNull { it.code == d }?.let { return it.name } }
        return ctx.db.countries[owner]?.definition?.name ?: owner
    }

    private companion object {
        const val NEAR_KM = 140.0
        const val VOWELS = "aeiouyéèêâîôûàAEIOUYÉÈÊÂÎÔÛ"
    }
}
