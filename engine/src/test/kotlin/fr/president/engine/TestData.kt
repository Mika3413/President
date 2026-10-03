package fr.president.engine

import fr.president.engine.data.DataLoader
import fr.president.engine.data.FileDataSource
import fr.president.engine.data.GameDatabase
import fr.president.engine.session.GameSession
import fr.president.engine.setup.NewGameOptions
import java.io.File

object TestData {
    val db: GameDatabase by lazy {
        val root = System.getProperty("president.assets") ?: "../assets"
        DataLoader(FileDataSource(File(root))).load()
    }

    const val START_REAL = 1_790_000_000_000L
    const val HOUR_MS = 3_600_000L

    /** Horloge réelle contrôlable. */
    class FakeClock(var now: Long = START_REAL) : () -> Long {
        override fun invoke(): Long = now
        fun advanceHours(hours: Double) { now += (hours * HOUR_MS).toLong() }
        /** Avance d'un nombre de jours du monde au rythme « normal » (1 h réelle = 1 jour). */
        fun advanceWorldDays(days: Double) = advanceHours(days)
    }

    fun newSession(seed: Long = 42L, clock: FakeClock = FakeClock(), pace: String = "normal"): GameSession =
        GameSession.newGame(db, NewGameOptions(pace, seed, clock.now), clock)
}
