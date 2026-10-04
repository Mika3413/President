package fr.president.engine.readout

import fr.president.engine.diplomacy.EuVote
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting

/**
 * Bilan historique du président : une note sur 20 et un verdict, à partir de ce que le pays est
 * devenu depuis la prise de fonctions (opinion, emploi, croissance, dette, déficit), des réformes,
 * de la gestion des crises, de l'Europe, des guerres, des élections et des affaires.
 * Provisoire pendant le mandat, définitif à la fin de la partie.
 */
class LegacyReadout(private val ctx: SimulationContext) {

    data class Line(val label: String, val points: Double, val detail: String)
    data class Verdict(val grade: Double, val title: String, val summary: String, val lines: List<Line>)

    fun verdict(): Verdict {
        val s = ctx.state
        val e = s.playerCountry.economy
        fun first(key: String, now: Double) = s.stats.series[key]?.all()?.firstOrNull() ?: now
        val lines = mutableListOf<Line>()

        val approval = s.opinion.nationalApproval
        lines += Line("Opinion", ((approval - NEUTRAL_APPROVAL) * APPROVAL_SCALE).coerceIn(-MAX, MAX), "popularité de ${Formatting.wholePercent(approval)}")

        val u0 = first("unemployment", e.unemployment)
        lines += Line("Emploi", (-(e.unemployment - u0) * PERCENT).coerceIn(-MAX, MAX),
            "chômage de ${Formatting.percent(u0)} à ${Formatting.percent(e.unemployment)}")

        // Les premières semaines (avant le premier relevé de croissance) ne comptent pas.
        val growth = s.stats.series["growth"]?.all()?.toList()?.let { if (it.size > WARMUP * 2) it.drop(WARMUP) else it }
            ?.takeIf { it.isNotEmpty() }?.average() ?: e.realGrowth
        lines += Line("Croissance", ((growth - REFERENCE_GROWTH) * PERCENT).coerceIn(-MAX, MAX), "${Formatting.signedPercent(growth)} par an en moyenne")

        val d0 = first("debt", e.debtRatio)
        lines += Line("Dette", (-(e.debtRatio - d0) * DEBT_SCALE).coerceIn(-MAX, MAX),
            "de ${Formatting.wholePercent(d0)} à ${Formatting.wholePercent(e.debtRatio)} du PIB")

        val deficit = e.deficitRatio
        lines += Line("Déficit", when { deficit <= GOOD_DEFICIT -> 1.0; deficit >= BAD_DEFICIT -> -1.0; else -> 0.0 }, "${Formatting.percent(deficit)} du PIB")

        val reforms = s.policy.adoptedReforms.size
        lines += Line("Réformes", (reforms * REFORM_POINTS).coerceAtMost(MAX), "$reforms réforme(s) adoptée(s)")

        val journal = s.stats.journal
        val setbacks = journal.count { it.kind == "Mesure" && ("Conseil" in it.text || "Parlement" in it.text) }
        val crashes = journal.count { it.kind == "Économie" && "Krach" in it.text }
        val crisis = -(setbacks * SETBACK_POINTS + crashes * SETBACK_POINTS)
        if (setbacks + crashes > 0) lines += Line("Crises", crisis.coerceAtLeast(-MAX), "$setbacks mesure(s) désavouée(s), $crashes krach(s)")

        val euVotes = s.eu.results.filter { it.france != EuVote.ABSTAIN }
        if (euVotes.isNotEmpty()) {
            val won = euVotes.count { (it.france == EuVote.YES) == it.adopted }.toDouble() / euVotes.size
            lines += Line("Europe", ((won - NEUTRAL_SHARE) * EU_SCALE).coerceIn(-MAX, MAX), "${Math.round(won * PERCENT)} % des votes européens gagnés")
        }

        val atWar = fr.president.engine.military.Geopolitics(ctx).enemiesOf(s.player.countryId).isNotEmpty()
        if (atWar || s.player.warsThisTerm > 0) lines += Line("Guerre", if (atWar) -1.0 else 0.5, if (atWar) "le pays est encore en guerre" else "guerre terminée")
        if (s.player.gameOver?.reason?.contains("capitale") == true) lines += Line("Défaite", -CAPITULATION, "la capitale est tombée")

        val terms = s.player.termNumber
        val lastElection = s.elections.results.lastOrNull()
        when {
            terms > 1 -> lines += Line("Élections", REELECTION * (terms - 1), "réélu(e) ${terms - 1} fois")
            lastElection != null && !lastElection.incumbentWon -> lines += Line("Élections", -1.0, "battu(e) à la présidentielle")
        }

        val scandals = s.characters[s.player.presidentId]?.scandals ?: 0
        if (scandals > 0) lines += Line("Affaires", -(scandals * SCANDAL_POINTS).coerceAtMost(MAX), "$scandals affaire(s) vous visant")

        val grade = (BASE_GRADE + lines.sumOf { it.points }).coerceIn(0.0, MAX_GRADE)
        val (title, summary) = when {
            grade >= GREAT -> "Grand président" to "L'Histoire retiendra votre nom : le pays sort transformé de vos années au pouvoir."
            grade >= REFORMER -> "Président réformateur" to "Un bilan solide : des réformes, une économie tenue, une autorité reconnue."
            grade >= HONORABLE -> "Mandat honorable" to "Le pays a tenu bon ; vos successeurs hériteront d'une situation correcte."
            grade >= MIXED -> "Mandat contrasté" to "Des réussites, mais des échecs qui pèseront dans les mémoires."
            else -> "Mandat calamiteux" to "Les historiens seront sévères : le pays sort affaibli de votre présidence."
        }
        return Verdict(grade, title, summary, lines.sortedByDescending { kotlin.math.abs(it.points) })
    }

    private companion object {
        const val PERCENT = 100.0
        const val WARMUP = 8
        const val MAX = 2.0
        const val BASE_GRADE = 10.0
        const val MAX_GRADE = 20.0
        const val NEUTRAL_APPROVAL = 0.42
        const val APPROVAL_SCALE = 10.0
        const val REFERENCE_GROWTH = 0.01
        const val DEBT_SCALE = 20.0
        const val GOOD_DEFICIT = 0.03
        const val BAD_DEFICIT = 0.06
        const val REFORM_POINTS = 0.4
        const val SETBACK_POINTS = 0.5
        const val NEUTRAL_SHARE = 0.5
        const val EU_SCALE = 3.0
        const val CAPITULATION = 5.0
        const val REELECTION = 2.0
        const val SCANDAL_POINTS = 0.5
        const val GREAT = 16.0
        const val REFORMER = 13.0
        const val HONORABLE = 10.0
        const val MIXED = 7.0
    }
}
