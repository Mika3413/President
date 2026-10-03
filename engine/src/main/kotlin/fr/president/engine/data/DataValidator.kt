package fr.president.engine.data

/** Contrôles de cohérence des données : on échoue tôt plutôt que de corrompre une partie. */
object DataValidator {
    private const val SHARE_TOLERANCE = 0.02

    fun validate(db: GameDatabase) {
        val errors = mutableListOf<String>()
        db.snapshot.playableCountries.forEach { id ->
            val country = db.countries[id]
            if (country == null) {
                errors += "Pays jouable absent : $id"
            } else if (country.definition.detail != DetailLevel.FULL) {
                errors += "Pays jouable sans simulation complète : $id"
            } else {
                validatePlayable(country, errors)
            }
        }
        db.countries.values.forEach { c ->
            if (c.definition.namePool !in db.names) errors += "${c.id} : réserve de noms inconnue ${c.definition.namePool}"
        }
        db.events.forEach { e ->
            e.message?.let { m ->
                if (m.template !in db.dialogue) errors += "Événement ${e.id} : modèle ${m.template} inconnu"
                if (m.options.none { it.id == m.defaultOption }) errors += "Événement ${e.id} : option par défaut absente"
            }
        }
        if (errors.isNotEmpty()) throw DataException("Données incohérentes :\n" + errors.joinToString("\n"))
    }

    private fun validatePlayable(country: CountryData, errors: MutableList<String>) {
        val id = country.id
        val territory = country.territory ?: run { errors += "$id : territoire manquant"; return }
        val regions = territory.regions.map { it.code }.toSet()
        territory.departments.filter { it.region !in regions }
            .forEach { errors += "$id : département ${it.code} rattaché à une région inconnue" }
        val departments = territory.departments.map { it.code }.toSet()
        territory.cities.filter { it.department !in departments }
            .forEach { errors += "$id : ville ${it.id} dans un département inconnu" }
        if (country.government == null) errors += "$id : gouvernement manquant"
        if (country.elections == null) errors += "$id : élections manquantes"
        if (country.economy.budget == null) errors += "$id : budget détaillé manquant"
        country.socialGroups?.let { groups ->
            groups.partitions.forEach { p ->
                val sum = groups.groups.filter { it.partition == p.id }.sumOf { it.populationShare }
                if (kotlin.math.abs(sum - 1.0) > SHARE_TOLERANCE) errors += "$id : partition ${p.id} totalise $sum"
            }
        } ?: run { errors += "$id : groupes sociaux manquants" }
        val ministries = country.government?.ministries?.map { it.id }?.toSet().orEmpty()
        country.economy.budget?.spending?.filter { it.ministry !in ministries }
            ?.forEach { errors += "$id : dépense ${it.id} rattachée à un ministère inconnu ${it.ministry}" }
    }
}
