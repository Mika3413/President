package fr.president.engine.infrastructure

import fr.president.engine.data.CountryData
import fr.president.engine.data.InfrastructureDef
import fr.president.engine.data.MilitaryBaseDef

/** Index des définitions d'infrastructures et de bases du pays joueur. */
class InfrastructureCatalog(country: CountryData) {
    val items: Map<String, InfrastructureDef> =
        (country.energy?.items.orEmpty() + country.transport?.items.orEmpty()).associateBy { it.id }
    val bases: Map<String, MilitaryBaseDef> = country.military?.bases.orEmpty().associateBy { it.id }

    fun item(id: String): InfrastructureDef? = items[id]
    fun departmentOf(id: String): String? = items[id]?.department ?: bases[id]?.department
}
