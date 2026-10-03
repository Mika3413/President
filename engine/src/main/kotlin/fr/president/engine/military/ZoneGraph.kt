package fr.president.engine.military

import fr.president.engine.data.ZoneDef
import fr.president.engine.data.ZonesFile
import java.util.PriorityQueue
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Graphe des zones de théâtre : voisinages, distances, plus court chemin.
 * Immuable (données) ; le contrôle des zones vit dans l'état de la partie.
 */
class ZoneGraph(file: ZonesFile) {
    val zones: Map<String, ZoneDef> = file.zones.associateBy { it.id }
    private val byOwner: Map<String, List<ZoneDef>> = file.zones.filter { !it.sea }.groupBy { it.owner }
    private val byDepartment: Map<String, List<ZoneDef>> = file.zones.filter { it.department != null }.groupBy { it.department!! }

    fun zone(id: String): ZoneDef = zones[id] ?: error("Zone inconnue : $id")
    fun ownedBy(country: String): List<ZoneDef> = byOwner[country].orEmpty()
    fun ofDepartment(code: String): List<ZoneDef> = byDepartment[code].orEmpty()
    fun neighbors(id: String): List<ZoneDef> = zone(id).n.mapNotNull { zones[it] }

    fun distanceKm(a: String, b: String): Double = haversine(zone(a), zone(b))

    /** Zone la plus proche d'un point, éventuellement restreinte (terrestre, pays...). */
    fun nearest(lon: Double, lat: Double, filter: (ZoneDef) -> Boolean = { true }): ZoneDef? =
        zones.values.filter(filter).minByOrNull { (it.lon - lon) * (it.lon - lon) + (it.lat - lat) * (it.lat - lat) }

    /**
     * Plus court chemin (A*) entre deux zones en ne traversant que les zones autorisées.
     * Renvoie la liste des zones à parcourir (départ exclu), ou null si inaccessible.
     */
    fun path(from: String, to: String, passable: (ZoneDef) -> Boolean): List<String>? {
        if (from == to) return emptyList()
        val target = zones[to] ?: return null
        val cost = HashMap<String, Double>().apply { put(from, 0.0) }
        val previous = HashMap<String, String>()
        val open = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        open.add(from to haversine(zone(from), target))
        while (open.isNotEmpty()) {
            val (current, _) = open.poll()
            if (current == to) break
            for (n in neighbors(current)) {
                if (!passable(n)) continue
                val c = cost.getValue(current) + haversine(zone(current), n)
                if (c < (cost[n.id] ?: Double.MAX_VALUE)) {
                    cost[n.id] = c
                    previous[n.id] = current
                    open.add(n.id to c + haversine(n, target))
                }
            }
            if (cost.size > MAX_EXPLORED) return null
        }
        if (to !in previous) return null
        val result = ArrayDeque<String>()
        var step = to
        while (step != from) {
            result.addFirst(step)
            step = previous.getValue(step)
        }
        return result.toList()
    }

    /** Zones à moins de [steps] pas d'une zone de départ (parcours en largeur). */
    fun within(start: Collection<String>, steps: Int, passable: (ZoneDef) -> Boolean): Map<String, Int> {
        val depth = HashMap<String, Int>()
        val queue = ArrayDeque<String>()
        start.forEach { depth[it] = 0; queue.add(it) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val d = depth.getValue(current)
            if (d >= steps) continue
            for (n in neighbors(current)) {
                if (n.id in depth || !passable(n)) continue
                depth[n.id] = d + 1
                queue.add(n.id)
            }
        }
        return depth
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private const val MAX_EXPLORED = 6000

        fun haversine(a: ZoneDef, b: ZoneDef): Double = haversine(a.lon, a.lat, b.lon, b.lat)

        fun haversine(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
        }
    }
}
