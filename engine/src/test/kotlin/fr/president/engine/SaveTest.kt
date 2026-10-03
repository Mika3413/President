package fr.president.engine

import fr.president.engine.data.GameJson
import fr.president.engine.save.SaveCodec
import fr.president.engine.save.SaveFile
import fr.president.engine.save.SaveRepository
import fr.president.engine.session.GameSession
import fr.president.engine.world.WorldState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SaveTest {
    private fun json(s: WorldState) = GameJson.save.encodeToString(WorldState.serializer(), s)

    @Test
    fun `une sauvegarde relue est identique`() {
        val clock = TestData.FakeClock()
        val session = TestData.newSession(clock = clock)
        clock.advanceWorldDays(200.0)
        session.advanceToNow()
        val codec = SaveCodec()
        val restored = codec.decode(codec.encode(session.toSaveFile("test")))
        assertEquals(json(session.state), json(restored.state))
        assertEquals(SaveFile.CURRENT_FORMAT, restored.formatVersion)
    }

    @Test
    fun `une partie rechargée continue exactement comme l'originale`() {
        val clock = TestData.FakeClock()
        val original = TestData.newSession(seed = 5L, clock = clock)
        clock.advanceWorldDays(100.0)
        original.advanceToNow()
        val codec = SaveCodec()
        val reloaded = GameSession.fromSave(TestData.db, codec.decode(codec.encode(original.toSaveFile("test"))), clock)
        clock.advanceWorldDays(300.0)
        original.advanceToNow()
        reloaded.advanceToNow()
        assertEquals(json(original.state), json(reloaded.state))
    }

    @Test
    fun `le dépôt se rabat sur la copie de secours si la sauvegarde est corrompue`() {
        val dir = Files.createTempDirectory("saves").toFile()
        val repo = SaveRepository(dir)
        val session = TestData.newSession()
        repo.write(session.toSaveFile("v1"))
        repo.write(session.toSaveFile("v2"))
        dir.resolve("president.save").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals("v1", repo.read().gameVersion)
    }

    @Test
    fun `une sauvegarde d'un format futur est refusée`() {
        val codec = SaveCodec()
        val session = TestData.newSession()
        val future = SaveFile(SaveFile.CURRENT_FORMAT + 1, 0L, "futur", session.state)
        assertFailsWith<fr.president.engine.save.SaveException> { codec.decode(codec.encode(future)) }
    }
}
