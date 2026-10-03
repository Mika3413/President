package fr.president.engine.data

/**
 * Formes grammaticales des noms de lieux français, pour des textes corrects :
 * « dans le Finistère », « en Gironde », « de l'Ain », « des Landes », « la centrale du Bugey ».
 */
object PlaceNames {
    /** Variables de dialogue d'un département : departmentThe, DepartmentThe, departmentOf, departmentIn. */
    fun department(name: String, article: String): Map<String, String> {
        val the = when (article) { "" -> name; "l'" -> "l'$name"; else -> "$article $name" }
        val of = when (article) { "" -> "de $name"; "le" -> "du $name"; "les" -> "des $name"; "la" -> "de la $name"; else -> "de l'$name" }
        val inside = when (article) { "" -> "à $name"; "le" -> "dans le $name"; "les" -> "dans les $name"; "la" -> "en $name"; else -> "dans l'$name" }
        return mapOf(
            "departmentThe" to the, "DepartmentThe" to the.replaceFirstChar { it.uppercaseChar() },
            "departmentOf" to of, "departmentIn" to inside,
        )
    }

    private val FEMININE = setOf("centrale", "raffinerie", "usine", "base", "gare", "station")

    /** Variables d'un équipement : infrastructureThe, InfrastructureThe, infrastructureOf, infrastructureAt. */
    fun infrastructure(name: String): Map<String, String> {
        val first = name.substringBefore(' ')
        // Nom commun en tête (« Centrale nucléaire du Bugey ») : minuscule et article ; nom propre : article élidé.
        val common = first.lowercase() in COMMON_NOUNS
        val body = if (common) name.replaceFirstChar { it.lowercaseChar() } else name
        val feminine = first.lowercase() in FEMININE
        val vowel = body.first().lowercaseChar() in VOWELS
        val the = when { vowel -> "l'$body"; feminine -> "la $body"; else -> "le $body" }
        val of = when { vowel -> "de l'$body"; feminine -> "de la $body"; else -> "du $body" }
        val at = when { vowel -> "à l'$body"; feminine -> "à la $body"; else -> "au $body" }
        return mapOf(
            "infrastructureThe" to the, "InfrastructureThe" to the.replaceFirstChar { it.uppercaseChar() },
            "infrastructureOf" to of, "infrastructureAt" to at,
        )
    }

    private val COMMON_NOUNS = setOf("centrale", "raffinerie", "barrage", "port", "aéroport", "usine", "base", "gare")
    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y', 'é', 'è', 'ê', 'â', 'î', 'ô', 'h')
}
