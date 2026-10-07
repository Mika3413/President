package fr.president.engine.util

/**
 * Adapte au pays joué les textes écrits pour la France : « la France » → « l'Allemagne »,
 * « l'Élysée » → « la Chancellerie »... Les règles (expression régulière, remplacement) viennent
 * des données du pays et s'appliquent dans l'ordre.
 */
class Localizer(rules: List<List<String>>) {
    private val compiled = rules.filter { it.size == 2 }.map { (pattern, replacement) -> Regex(pattern) to replacement }

    val isEmpty: Boolean get() = compiled.isEmpty()

    fun apply(text: String): String {
        if (compiled.isEmpty() || text.isEmpty()) return text
        var out = text
        for ((regex, replacement) in compiled) out = regex.replace(out) { replacement }
        return out
    }
}
