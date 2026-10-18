package fr.president.engine.readout

import fr.president.engine.simulation.SimulationContext
import fr.president.engine.stats.JournalEntry
import fr.president.engine.time.WorldTime
import fr.president.engine.util.Formatting

/**
 * Le livre de votre présidence : un chapitre par année, titré d'après ce qui l'a marquée
 * (guerre, titre sportif, trahison, réformes, épreuves...), raconté par ses grands faits, avec
 * ses chiffres ; puis l'épilogue, le jugement de l'Histoire.
 */
class HistoryBookReadout(private val ctx: SimulationContext) {
    data class Chapter(val number: Int, val period: String, val title: String, val paragraphs: List<String>, val figures: List<Pair<String, String>>, val tone: Tone)
    data class Book(val title: String, val subtitle: String, val chapters: List<Chapter>, val epilogueTitle: String, val epilogue: String, val grade: String)

    fun book(): Book {
        val s = ctx.state
        val president = s.characters.getValue(s.player.presidentId)
        val start = s.meta.startTime
        val years = Math.max(1, Math.ceil(start.daysUntil(s.time) / DAYS_PER_YEAR).toInt())
        val chapters = (0 until years).map { chapter(it, start.plusDays(it * DAYS_PER_YEAR), start.plusDays((it + 1) * DAYS_PER_YEAR).let { e -> if (e > s.time) s.time else e }) }
        val verdict = LegacyReadout(ctx).verdict()
        val e = if (president.female) "e" else ""
        return Book(
            "La présidence de ${president.fullName}",
            "${Formatting.date(start)} — ${Formatting.date(s.time)} · ${s.player.termNumber} mandat${if (s.player.termNumber > 1) "s" else ""}",
            chapters,
            "Épilogue : ${verdict.title}",
            verdict.summary + " On retiendra surtout " + verdict.lines.sortedByDescending { Math.abs(it.points) }.take(2).joinToString(" et ") { it.label.lowercase() } +
                ". Note de l'Histoire : ${Math.round(verdict.grade * 10) / 10.0} / 20. Ainsi s'achève le récit de cette présidence, que chacun jugera à sa façon : celle d'un$e président$e qui aura laissé sa marque.",
            "${Math.round(verdict.grade * 10) / 10.0} / 20",
        )
    }

    private fun chapter(index: Int, from: WorldTime, to: WorldTime): Chapter {
        val s = ctx.state
        val inPeriod = { t: WorldTime -> t >= from && t < to }
        val news = s.stats.bigNews.filter { inPeriod(it.time) }
        val facts = s.stats.journal.filter { inPeriod(it.time) && it.kind in STORY_KINDS && (it.tone != Tone.NEUTRAL || it.kind in ALWAYS) }
        val approvalFrom = seriesAt("approval", from)
        val approvalTo = seriesAt("approval", to)
        val trend = if (approvalFrom != null && approvalTo != null) approvalTo - approvalFrom else 0.0
        val title = when {
            news.any { it.picture == "WAR" } -> "L'année de la guerre"
            news.any { it.picture == "NUCLEAR" } -> "L'année où le monde a tremblé"
            news.any { it.picture == "PEACE" } -> "L'année de la paix retrouvée"
            news.any { it.picture == "TROPHY" } -> "L'année des champions"
            news.any { it.picture == "BETRAYAL" } -> "L'année de la trahison"
            news.any { it.picture == "PARLIAMENT" } -> "L'année du coup de tonnerre"
            news.any { it.picture == "ATTACK" || it.picture == "RIOT" } -> "L'année des épreuves"
            facts.count { it.kind == "Loi" } >= 3 -> "L'année des réformes"
            index == 0 -> "Les premiers pas"
            trend > TREND -> "L'année de la confiance"
            trend < -TREND -> "L'année du doute"
            else -> "L'année du gros temps"
        }
        // Les grands faits de l'année, dans l'ordre : d'abord les plus marquants.
        val chosen = (news.map { JournalEntry(it.time, "Une", it.title, it.tone) } + facts)
            .sortedBy { when (it.tone) { Tone.BAD -> 0; Tone.GOOD -> 1; Tone.WARNING -> 2; Tone.NEUTRAL -> 3 } }
            .distinctBy { it.text.take(DEDUP) }.take(MAX_FACTS).sortedBy { it.time }
        val paragraphs = chosen.map { f -> "En ${Formatting.monthYear(f.time).substringBefore(' ')}, ${f.text.replaceFirstChar { it.lowercase() }.trimEnd('.')}." }
            .ifEmpty { listOf("Une année sans fracas : le pays a vécu au rythme des réformes ordinaires et des crises vite oubliées.") }
        val figures = listOfNotNull(
            pair("Popularité", "approval", from, to),
            pair("Chômage", "unemployment", from, to),
            pair("Dette publique", "debt", from, to),
        )
        val tone = when { trend > TREND -> Tone.GOOD; trend < -TREND -> Tone.BAD; else -> Tone.NEUTRAL }
        val period = "${from.toDateTime().year}" + if (to.toDateTime().year != from.toDateTime().year) "–${to.toDateTime().year}" else ""
        return Chapter(index + 1, period, title, paragraphs, figures, tone)
    }

    private fun pair(label: String, key: String, from: WorldTime, to: WorldTime): Pair<String, String>? {
        val a = seriesAt(key, from) ?: return null
        val b = seriesAt(key, to) ?: return null
        return label to "${Formatting.percent(a)} → ${Formatting.percent(b)}"
    }

    /** Valeur hebdomadaire la plus proche d'une date (les courbes commencent au début de la partie). */
    private fun seriesAt(key: String, t: WorldTime): Double? {
        val values = ctx.state.stats.series[key]?.all() ?: return null
        if (values.isEmpty()) return null
        val weeksFromEnd = (t.daysUntil(ctx.state.time) / fr.president.engine.stats.StatsSystem.DAYS_PER_POINT).toInt().coerceAtLeast(0)
        return values[(values.size - 1 - weeksFromEnd).coerceIn(0, values.size - 1)]
    }

    private companion object {
        const val DAYS_PER_YEAR = 365.0
        const val TREND = 0.04
        const val MAX_FACTS = 6
        const val DEDUP = 40
        val STORY_KINDS = setOf("Guerre", "Élections", "Loi", "Président", "Article 16", "Gouvernement", "Défense", "Militaire", "Société", "Économie", "Europe", "Sécurité", "Catastrophes", "Politique")
        val ALWAYS = setOf("Loi", "Élections", "Guerre")
    }
}
