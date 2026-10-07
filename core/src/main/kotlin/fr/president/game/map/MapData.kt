package fr.president.game.map

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import fr.president.engine.data.GameDatabase
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class MarkerKind { CITY, FOREIGN_CITY, NUCLEAR, POWER, INDUSTRY, PORT, AIRPORT, MILITARY }

/** Élément ponctuel de la carte (ville, centrale, base...). */
class MapMarker(
    val id: String,
    val kind: MarkerKind,
    val label: String,
    val x: Float,
    val y: Float,
    /** Importance : 1 = majeur. Sert au niveau de détail. */
    val rank: Int,
)

/** Réseau linéaire (LGV, autoroute) tracé entre villes. */
class NetworkLine(val id: String, val kind: String, val name: String, val points: FloatArray)

/** Toutes les données géographiques de la carte, préparées une fois au chargement. */
class MapData(db: GameDatabase, white: TextureRegion, val playerCountryId: String) {
    val capitalId = db.country(playerCountryId).definition.capitalCityId
    private val loader = GeoLoader(white)
    /** Contours du pays joué : la France a les siens, les autres pays jouables un préfixe (geo/DEU_...). */
    private val geoPrefix = db.country(playerCountryId).definition.geo
    val countries: List<GeoFeature> = loader.load(WORLD)
    val regions: List<GeoFeature> = loader.load(geoPrefix?.let { "data/geo/${it}_regions.json" } ?: REGIONS)
    val departments: List<GeoFeature> = loader.load(geoPrefix?.let { "data/geo/${it}_departments.json" } ?: DEPARTMENTS)
    val departmentsById = departments.associateBy { it.id }
    val regionsById = regions.associateBy { it.id }
    val countriesById = countries.associateBy { it.id }
    val markers: List<MapMarker>
    val markersById: Map<String, MapMarker>
    /** Pays de chaque ville étrangère. */
    val foreignCountry: Map<String, String>
    val networks: List<NetworkLine>
    /** Habitants par km² et par département. */
    val density: Map<String, Float>
    /** Cadres des médaillons d'outre-mer, en coordonnées monde. */
    val insets: List<Rectangle> = if (geoPrefix == null) loadInsets() else emptyList()

    val departmentGrid = SpatialGrid<GeoFeature>(GRID_CELL).also { g -> departments.forEach { g.insert(it, it.bounds) } }
    val countryGrid = SpatialGrid<GeoFeature>(GRID_CELL * WORLD_GRID_FACTOR).also { g -> countries.forEach { g.insert(it, it.bounds) } }

    init {
        val country = db.country(playerCountryId)
        val territory = country.territory!!
        val cityMarkers = territory.cities.map {
            MapMarker(it.id, MarkerKind.CITY, it.name, GeoProjection.x(it.lon), GeoProjection.y(it.lat), it.rank)
        }
        val infra = (country.energy?.items.orEmpty() + country.transport?.items.orEmpty()).map {
            val kind = when (it.type) {
                "NUCLEAR_PLANT" -> MarkerKind.NUCLEAR
                "HYDRO_DAM", "GAS_PLANT" -> MarkerKind.POWER
                "REFINERY" -> MarkerKind.INDUSTRY
                "PORT" -> MarkerKind.PORT
                "AIRPORT" -> MarkerKind.AIRPORT
                else -> MarkerKind.INDUSTRY
            }
            MapMarker(it.id, kind, it.name, GeoProjection.x(it.lon), GeoProjection.y(it.lat), if (kind == MarkerKind.NUCLEAR) 1 else 2)
        }
        val bases = country.military?.bases.orEmpty().map {
            MapMarker(it.id, MarkerKind.MILITARY, it.name, GeoProjection.x(it.lon), GeoProjection.y(it.lat), 2)
        }
        // Villes étrangères : capitales et grandes villes des pays simulés (rang 1 = capitale).
        val foreign = db.worldCities.filter { it.country != playerCountryId }.map {
            MapMarker(it.id, MarkerKind.FOREIGN_CITY, it.name, GeoProjection.x(it.lon), GeoProjection.y(it.lat), it.rank)
        }
        foreignCountry = db.worldCities.filter { it.country != playerCountryId }.associate { it.id to it.country }
        markers = cityMarkers + infra + bases + foreign
        markersById = markers.associateBy { it.id }
        val cities = cityMarkers.associateBy { it.id }
        networks = country.transport?.networks.orEmpty().map { n ->
            val pts = n.cityIds.mapNotNull { cities[it] }.flatMap { listOf(it.x, it.y) }.toFloatArray()
            NetworkLine(n.id, n.kind, n.name, pts)
        }
        val populations = territory.departments.associate { it.code to it.population }
        density = departments.associate { f ->
            f.id to ((populations[f.id] ?: 0L) / (f.area * GeoProjection.KM2_PER_UNIT2)).toFloat()
        }
    }

    private fun loadInsets(): List<Rectangle> = runCatching {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(com.badlogic.gdx.Gdx.files.internal(INSETS).readString("UTF-8"))
        root.jsonObject.getValue("insets").jsonArray.map { e ->
            val b = e.jsonObject.getValue("box").jsonArray.map { it.jsonPrimitive.double }
            val x0 = GeoProjection.x(b[0]); val y0 = GeoProjection.y(b[1])
            val x1 = GeoProjection.x(b[2]); val y1 = GeoProjection.y(b[3])
            Rectangle(minOf(x0, x1), minOf(y0, y1), kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))
        }
    }.getOrDefault(emptyList())

    fun visibleDepartments(view: Rectangle): Set<GeoFeature> = departmentGrid.query(view)

    /** Centre et largeur de vue du pays joué (coordonnées monde), sans les îles lointaines (Alaska, Hawaï...). */
    fun homeView(): Triple<Float, Float, Float> {
        val capital = markersById[capitalId]
        val cx = capital?.x ?: departments.map { it.bounds.x + it.bounds.width / 2 }.sorted().let { it[it.size / 2] }
        val near = departments.filter { kotlin.math.abs(it.bounds.x + it.bounds.width / 2 - cx) < HOME_SPAN && it.bounds.width < HOME_SPAN }
            .ifEmpty { departments }
        val minX = near.minOf { it.bounds.x }; val maxX = near.maxOf { it.bounds.x + it.bounds.width }
        val minY = near.minOf { it.bounds.y }; val maxY = near.maxOf { it.bounds.y + it.bounds.height }
        return Triple((minX + maxX) / 2, (minY + maxY) / 2, (maxX - minX) * 1.1f)
    }

    fun visibleCountries(view: Rectangle): Set<GeoFeature> = countryGrid.query(view)

    private companion object {
        const val WORLD = "data/geo/world_countries.json"
        const val REGIONS = "data/geo/france_regions.json"
        const val DEPARTMENTS = "data/geo/france_departments.json"
        const val INSETS = "data/geo/france_insets.json"
        const val GRID_CELL = 50f
        /** Demi-largeur (unités monde) autour de la capitale pour cadrer le pays : 30 degrés. */
        const val HOME_SPAN = 3000f
        const val WORLD_GRID_FACTOR = 10
    }
}
