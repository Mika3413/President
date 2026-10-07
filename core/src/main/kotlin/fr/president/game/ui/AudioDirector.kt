package fr.president.game.ui

import fr.president.engine.military.Geopolitics
import fr.president.engine.session.GameSession

/**
 * Chef d'orchestre de l'ambiance : il observe la partie et en tire les sons (canonnade quand nos
 * troupes se battent, cuivres d'une victoire, roulement d'une ville prise, grondement d'une frappe
 * nucléaire, foule d'un titre sportif ou d'une réélection), choisit la musique et décide des fêtes
 * (feu d'artifice sur la capitale). Rien n'est joué au chargement d'une partie : seul ce qui change
 * pendant qu'on regarde fait du bruit.
 */
class AudioDirector {
    private var primed = false
    private var since = 0f
    private val battleLosses = HashMap<String, Int>()
    private val outcomes = HashSet<String>()
    private val cities = HashMap<String, String>()
    private val strikes = HashSet<String>()
    private val tournaments = HashMap<String, Int>()
    private val won = HashSet<String>()
    private var elections = 0
    private var clock = 0f
    private var celebrateUntil = -1f

    /** Fêtes demandées à la carte (feu d'artifice), consommées par l'écran. */
    var pendingFireworks = 0
    /** Frappes nucléaires survenues pendant qu'on regardait (éclair à l'écran). */
    var pendingFlash = false

    fun update(session: GameSession, delta: Float) {
        clock += delta
        since += delta
        if (since < CHECK_SECONDS && primed) return
        since = 0f
        val s = session.state
        val player = s.player.countryId
        val geo = Geopolitics(session.context)
        val camp = geo.coBelligerents(player) + player
        val quiet = !primed
        // Batailles où notre camp est engagé : canonnade quand les pertes tombent, cuivres à l'issue.
        for (b in s.military.battles) {
            val ours = b.attackers.any { it in camp } || b.defenders.any { it in camp }
            val losses = b.attackerLosses + b.defenderLosses
            val before = battleLosses.put(b.id, losses)
            if (!ours || quiet) { if (b.outcome.isNotEmpty()) outcomes += b.id; continue }
            if (before != null && losses > before && b.outcome.isEmpty()) Sfx.play(Sfx.Kind.BATTLE)
            if (b.outcome.isNotEmpty() && outcomes.add(b.id)) {
                val weAttack = b.attackers.any { it in camp }
                val wonIt = (b.outcome.startsWith("Victoire") && weAttack) || (b.outcome.startsWith("Le défenseur") && !weAttack)
                Sfx.play(if (wonIt) Sfx.Kind.VICTORY else Sfx.Kind.DEFEAT)
            }
        }
        battleLosses.keys.retainAll(s.military.battles.map { it.id }.toSet())
        // Villes prises ou perdues.
        for ((city, holder) in s.military.cityControl) {
            val before = cities.put(city, holder)
            if (quiet || before == null || before == holder) continue
            when {
                holder in camp -> { Sfx.play(Sfx.Kind.CAPTURE); if (holder == player) celebrate(SHORT_PARTY) }
                before in camp -> Sfx.play(Sfx.Kind.DEFEAT)
            }
        }
        // Frappes : missiles qui nous concernent, arme nucléaire où qu'elle tombe.
        for (st in s.military.strikes) {
            val key = "${st.at.seconds}|${st.to}"
            if (!strikes.add(key) || quiet) continue
            when {
                st.nuclear -> { Sfx.play(Sfx.Kind.NUCLEAR); MusicPlayer.hush(NUCLEAR_SILENCE); pendingFlash = true }
                st.actor in camp || s.military.units.values.any { it.zoneId == st.to && it.countryId in camp } -> Sfx.play(Sfx.Kind.BATTLE, 0.8f)
            }
        }
        // Grandes compétitions : la foule à chaque tour passé, la fanfare et la fête pour un titre.
        for ((id, t) in s.majorEvents.tournaments) {
            val before = tournaments.put(id, t.stage)
            if (t.won && won.add(id) && !quiet) { Sfx.play(Sfx.Kind.FANFARE); celebrate(LONG_PARTY) }
            else if (!quiet && before != null && t.stage > before && !t.eliminated) Sfx.play(Sfx.Kind.CROWD, 0.6f)
        }
        // Élection présidentielle remportée.
        if (s.elections.results.size > elections) {
            if (!quiet && s.elections.results.last().incumbentWon) { Sfx.play(Sfx.Kind.FANFARE); celebrate(LONG_PARTY) }
            elections = s.elections.results.size
        }
        primed = true
    }

    private fun celebrate(seconds: Float) {
        celebrateUntil = maxOf(celebrateUntil, clock + seconds)
        pendingFireworks++
    }

    val celebrating: Boolean get() = clock < celebrateUntil

    /** Musique voulue : la marche quand nos troupes se battent, la fête après un triomphe. */
    fun mood(session: GameSession, tense: Boolean): MusicPlayer.Mood {
        val s = session.state
        val player = s.player.countryId
        val geo = Geopolitics(session.context)
        val camp = geo.coBelligerents(player) + player
        val fighting = geo.enemiesOf(player).isNotEmpty() && (s.military.battles.any { b -> b.active(s.time) && (b.attackers + b.defenders).any { it in camp } } ||
            s.military.strikes.any { it.at.daysUntil(s.time) < 1.0 })
        return when {
            fighting -> MusicPlayer.Mood.WAR
            celebrating -> MusicPlayer.Mood.CELEBRATION
            tense -> MusicPlayer.Mood.TENSE
            else -> MusicPlayer.Mood.CALM
        }
    }

    private companion object {
        const val CHECK_SECONDS = 0.25f
        const val SHORT_PARTY = 20f
        const val LONG_PARTY = 75f
        const val NUCLEAR_SILENCE = 7f
    }
}
