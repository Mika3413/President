package fr.president.engine

import fr.president.engine.save.SaveRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolidityTest {

    @Test
    fun `une sauvegarde abîmée et sa copie se rattrapent avec la copie du jour`() {
        val dir = kotlin.io.path.createTempDirectory("solidite").toFile()
        val repo = SaveRepository(dir)
        val s = TestData.newSession()
        repo.write(s.toSaveFile("test"), "p")
        repo.write(s.toSaveFile("test"), "p")
        val daily = dir.listFiles().orEmpty().filter { it.name.contains(".jour") }
        assertEquals(1, daily.size, "Une copie du jour doit exister")
        // Les deux fichiers principaux sont détruits : la copie du jour sauve la partie.
        dir.listFiles().orEmpty().filter { it.name == "p.save" || it.name == "p.save.bak" }.forEach { it.writeText("abîmé") }
        val back = repo.read("p")
        assertEquals(s.state.player.presidentId, back.state.player.presidentId)
        assertEquals(1, repo.slots().size, "Les copies ne comptent pas comme des parties")
        dir.deleteRecursively()
    }

    @Test
    fun `la simulation reste rapide`() {
        val clock = TestData.FakeClock()
        val s = TestData.newSession(clock = clock)
        clock.advanceWorldDays(5.0); s.advanceToNow()
        val start = System.nanoTime()
        clock.advanceWorldDays(60.0); s.advanceToNow()
        val msPerDay = (System.nanoTime() - start) / 1e6 / 60
        println("PERF ms par jour simulé : ${"%.1f".format(msPerDay)}")
        // Une journée doit se simuler bien plus vite qu'elle ne passe à l'écran.
        assertTrue(msPerDay < MAX_MS_PER_DAY, "Trop lent : $msPerDay ms par jour")
    }

    private companion object {
        const val MAX_MS_PER_DAY = 400.0
    }
}
