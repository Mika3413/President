package fr.president.engine

import fr.president.engine.notifications.Urgency
import fr.president.engine.presidency.OnboardingService
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Le joueur naïf : il fait ce qu'un débutant ferait (le premier matin, puis toujours la première
 * réponse proposée, sans rien ouvrir d'autre) pendant un an. On vérifie ce qui ferait fuir un
 * vrai joueur : plantage, messagerie qui déborde, avalanche d'alertes, chiffres absurdes.
 */
class NaivePlayerTest {

    @Test
    fun `un débutant passe sa première année sans que le jeu déraille`() {
        val clock = TestData.FakeClock()
        val s = GameSession.newGame(TestData.db, NewGameOptions("normal", 11L, clock.now, careerId = "medecin", variedWorld = true), clock)
        val onboarding = OnboardingService(s.context)
        val morning = onboarding.startFirstMorning()
        assertTrue(morning != null, "Le premier matin doit se déclencher")
        s.answer(morning.messageId, s.state.inbox.messages.first { it.id == morning.messageId }.options.first().id)
        assertTrue(onboarding.outcome() != null)
        onboarding.outcomeSeen()
        var maxPending = 0
        var answered = 0
        val alertsPerWeek = ArrayList<Int>()
        var lastSeen = s.state.notifications.feed.lastOrNull()?.id ?: 0L
        repeat(52) {
            repeat(7) {
                clock.advanceWorldDays(1.0); s.advanceToNow()
                val pending = s.state.inbox.messages.filter { it.awaitingAnswer }
                maxPending = maxOf(maxPending, pending.size)
                // Le débutant répond une fois par jour, toujours la première option.
                pending.forEach { m -> s.answer(m.id, m.options.first().id); answered++ }
            }
            val fresh = s.state.notifications.feed.filter { it.id > lastSeen }
            alertsPerWeek += fresh.count { it.urgency != Urgency.INFO }
            lastSeen = s.state.notifications.feed.lastOrNull()?.id ?: lastSeen
            if (s.isGameOver) return@repeat
        }
        val e = s.state.playerCountry.economy
        val numbers = listOf(s.state.opinion.nationalApproval, e.unemployment, e.debtRatio, e.inflation, e.realGrowth)
        println("NAIVE fin=${s.state.player.gameOver?.reason ?: "en poste"} popularité=${"%.2f".format(s.state.opinion.nationalApproval)} " +
            "messages traités=$answered file max=$maxPending alertes/semaine moy=${"%.1f".format(alertsPerWeek.average())} max=${alertsPerWeek.maxOrNull()} " +
            "écrans ouverts=${s.state.player.unlockedScreens.size} grands moments=${s.state.stats.bigNews.size} situation=${s.state.player.startSituation}")
        assertTrue(numbers.all { !it.isNaN() && !it.isInfinite() }, "Chiffres invalides : $numbers")
        assertTrue(maxPending <= MAX_PENDING, "La messagerie déborde : $maxPending en attente")
        assertTrue(alertsPerWeek.average() <= MAX_ALERTS_PER_WEEK, "Trop d'alertes : ${alertsPerWeek.average()} par semaine")
        assertTrue(s.state.player.unlockedScreens.size >= MIN_SCREENS, "Les écrans doivent s'ouvrir au fil de l'année")
    }

    private companion object {
        const val MAX_PENDING = 15
        const val MAX_ALERTS_PER_WEEK = 4.0
        const val MIN_SCREENS = 8
    }
}
