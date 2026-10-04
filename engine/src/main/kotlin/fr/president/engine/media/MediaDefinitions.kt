package fr.president.engine.media

import fr.president.engine.readout.Tone
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/** Journal fictif : ligne politique (-1 gauche, +1 droite) et spécialité (general, economy, social). */
@Serializable
data class PaperDef(val id: String, val name: String, val leaning: Double, val focus: String = "general", val color: String = "2f4a6b")

/** Institut de sondage fictif, avec un léger biais systématique. */
@Serializable
data class InstituteDef(val id: String, val name: String, val bias: Double = 0.0)

@Serializable
data class MediaFile(
    val papers: List<PaperDef>,
    val institutes: List<InstituteDef>,
    /** Sujet -> ton (pro, con, neutral) -> gabarits de titres. */
    val templates: Map<String, Map<String, List<String>>>,
)

/** Une du jour d'un journal. */
@Serializable
data class FrontPage(
    val time: WorldTime,
    val paperId: String,
    val topic: String,
    val headline: String,
    val subtitle: String,
    val tone: Tone,
)

/** Sondage publié par un institut. */
@Serializable
data class PollRelease(
    val time: WorldTime,
    val instituteId: String,
    val approval: Double,
    /** Intentions de vote pour le président au premier tour, si des candidats sont déclarés. */
    val voteIntention: Double? = null,
    /** Première préoccupation des Français. */
    val topConcern: String = "",
)

@Serializable
class MediaState(
    val frontPages: MutableList<FrontPage> = mutableListOf(),
    val polls: MutableList<PollRelease> = mutableListOf(),
    var lastPressDay: Long = Long.MIN_VALUE,
    var lastPollDay: Long = Long.MIN_VALUE,
    /** Climat médiatique lissé, de -1 (presse unanimement hostile) à +1 (presse favorable). */
    var climate: Double = 0.0,
)
