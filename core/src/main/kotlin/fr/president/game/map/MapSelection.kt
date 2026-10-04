package fr.president.game.map

/** Élément sélectionné sur la carte. */
sealed class MapSelection {
    data class Department(val code: String) : MapSelection()
    data class Region(val code: String) : MapSelection()
    data class City(val id: String) : MapSelection()
    data class Infrastructure(val id: String) : MapSelection()
    data class Base(val id: String) : MapSelection()
    data class Country(val id: String) : MapSelection()
    data class Unit(val id: String) : MapSelection()
    data class ForeignCity(val id: String) : MapSelection()
}
