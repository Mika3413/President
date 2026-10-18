package fr.president.engine.stats

import fr.president.engine.readout.Tone
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/** Grand moment mis en scène : flash spécial à la télévision ou une de journal illustrée. */
@Serializable
data class BigNews(
    val seq: Int,
    val time: WorldTime,
    val flash: Boolean,
    /** Illustration : WAR, PEACE, NUCLEAR, TROPHY, BETRAYAL, PARLIAMENT, ATTACK, DISASTER, RIOT. */
    val picture: String,
    val title: String,
    val subtitle: String,
    val tone: Tone,
)

/** Publie les grands moments ; l'écran les montre en plein écran, un à la fois. */
class BigNewsService(private val ctx: SimulationContext) {
    fun publish(picture: String, title: String, subtitle: String, tone: Tone, flash: Boolean = false) {
        val stats = ctx.state.stats
        val seq = (stats.bigNews.lastOrNull()?.seq ?: 0) + 1
        stats.bigNews += BigNews(seq, ctx.now, flash, picture, title, subtitle, tone)
        while (stats.bigNews.size > MAX) stats.bigNews.removeAt(0)
    }

    /** Le dernier grand moment pas encore montré (les plus anciens, manqués pendant une absence, sont passés). */
    fun pending(): BigNews? = ctx.state.stats.bigNews.lastOrNull()?.takeIf { it.seq > ctx.state.player.bigNewsSeen }

    fun seen() { ctx.state.stats.bigNews.lastOrNull()?.let { ctx.state.player.bigNewsSeen = it.seq } }

    private companion object {
        const val MAX = 40
    }
}
