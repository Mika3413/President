package fr.president.engine.diplomacy

import fr.president.engine.data.GameDatabase
import fr.president.engine.util.Formatting

/** Transforme des clauses en phrases lisibles pour le joueur et les messages. */
class ProposalDescriber(private val db: GameDatabase) {

    fun describe(clause: Clause, from: String, to: String): String {
        val def = db.diplomacy.clause(clause.type)
        val params = def.params.joinToString(", ") { p ->
            "${p.label} : ${Formatting.amount(clause.params[p.id] ?: p.default)} ${p.unit}".trim()
        }
        val parties = if (def.mutual) {
            "${db.country(from).definition.name} et ${db.country(to).definition.name}"
        } else {
            val receiver = if (clause.giver == from) to else from
            "${db.country(clause.giver).definition.name} → ${db.country(receiver).definition.name}"
        }
        return "${def.label} ($parties) — $params"
    }

    fun describeAll(clauses: List<Clause>, from: String, to: String, years: Int): String =
        clauses.joinToString("\n") { "• " + describe(it, from, to) } + "\n• Durée : $years an" + if (years > 1) "s" else ""
}
