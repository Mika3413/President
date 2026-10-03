package fr.president.engine.data

/** Formes grammaticales d'un nom de pays : « l'Italie », « de l'Italie », « en Italie », « à l'Italie ». */
class CountryNames(private val def: CountryDefinition) {
    private val name = def.name

    /** Avec article : « la Belgique », « l'Italie », « les Pays-Bas ». */
    val the: String get() = when (def.article) {
        "" -> name
        "l'" -> "l'$name"
        else -> "${def.article} $name"
    }

    /** Complément : « de la Belgique », « du Brésil », « des États-Unis », « de l'Italie ». */
    val of: String get() = when (def.article) {
        "le" -> "du $name"
        "les" -> "des $name"
        "la" -> "de la $name"
        "l'" -> "de l'$name"
        else -> "de $name"
    }

    /** Lieu : « en Italie », « au Brésil », « aux États-Unis ». */
    val inside: String get() = when (def.article) {
        "le" -> "au $name"
        "les" -> "aux $name"
        else -> "en $name"
    }

    /** Destinataire : « à la Belgique », « au Brésil », « aux États-Unis », « à l'Italie ». */
    val to: String get() = when (def.article) {
        "le" -> "au $name"
        "les" -> "aux $name"
        "la" -> "à la $name"
        "l'" -> "à l'$name"
        else -> "à $name"
    }

    /** Variables de dialogue pour ce pays, préfixées (ex. foreignThe, ForeignThe, foreignOf...). */
    fun variables(prefix: String): Map<String, String> {
        val cap = prefix.replaceFirstChar { it.uppercaseChar() }
        return mapOf(
            prefix + "Country" to name,
            prefix + "The" to the,
            cap + "The" to the.replaceFirstChar { it.uppercaseChar() },
            prefix + "Of" to of,
            prefix + "In" to inside,
            prefix + "To" to to,
        )
    }
}
