package fr.president.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Le moteur d'expressions régulières d'Android (ICU) refuse une accolade fermante non échappée,
 * que Java accepte sur PC : toutes les expressions du code doivent l'échapper.
 */
class AndroidRegexTest {
    @Test
    fun closingBracesAreEscaped() {
        val root = File(System.getProperty("president.assets")).parentFile
        val literal = Regex("Regex\\(\"\"\"(.*?)\"\"\"\\)|Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)")
        val offenders = listOf("engine/src/main", "core/src/main", "android/src/main")
            .map { File(root, it) }.filter { it.exists() }
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }
            .flatMap { file ->
                literal.findAll(file.readText()).mapNotNull { m ->
                    val pattern = m.groupValues[1].ifEmpty { m.groupValues[2] }
                    val bad = pattern.indices.any { i -> pattern[i] == '}' && (i == 0 || pattern[i - 1] != '\\') }
                    if (bad) "${file.name} : $pattern" else null
                }.toList()
            }
        assertTrue(offenders.isEmpty(), "Accolade fermante non échappée (plantage sur Android) : $offenders")
    }
}
