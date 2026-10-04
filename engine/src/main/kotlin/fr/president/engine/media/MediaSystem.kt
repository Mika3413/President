package fr.president.engine.media

import fr.president.engine.elections.ElectionSimulator
import fr.president.engine.readout.Tone
import fr.president.engine.simulation.Cadence
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.simulation.SimulationSystem
import fr.president.engine.util.Formatting
import kotlin.math.abs

/**
 * La presse et les sondages : chaque jour, chaque journal choisit son sujet de une selon
 * l'actualité et sa spécialité, et le traite selon sa ligne (proche du président ou critique).
 * Chaque semaine, un institut publie un sondage.
 */
class MediaSystem : SimulationSystem {
    override val name = "media"
    override val cadence = Cadence.DAILY

    override fun run(ctx: SimulationContext) {
        val def = ctx.db.media ?: return
        val media = ctx.state.media
        val day = ctx.now.dayIndex
        if (day != media.lastPressDay) {
            media.lastPressDay = day
            publishFrontPages(ctx, def)
        }
        if (media.lastPollDay == Long.MIN_VALUE || day - media.lastPollDay >= POLL_DAYS) {
            media.lastPollDay = day
            publishPoll(ctx, def)
        }
    }

    private data class Story(
        val topic: String,
        val weight: Double,
        val vars: Map<String, String>,
        /** Bonne (true) ou mauvaise (false) nouvelle pour le président, ou neutre (null). */
        val good: Boolean?,
        val economic: Boolean = false,
        val social: Boolean = false,
        val subtitle: String = "",
    )

    private fun stories(ctx: SimulationContext): List<Story> {
        val s = ctx.state
        val e = s.playerCountry.economy
        val since = ctx.now.plusDays(-1.0)
        val list = mutableListOf<Story>()
        s.stats.journal.filter { it.time > since }.forEach { j ->
            when {
                j.kind == "Décision" -> list += Story("decision", DECISION, mapOf("decision" to j.text), null, subtitle = "Le chef de l'État a tranché : reste à convaincre.")
                j.kind == "Loi" && j.text.startsWith(ADOPTED) -> list += Story("law_passed", LAW, mapOf("decision" to j.text.removePrefix(ADOPTED)), true, subtitle = "Le texte a été adopté par le Parlement.")
                j.kind == "Loi" && j.text.startsWith(REJECTED) -> list += Story("law_rejected", LAW_REJECTED, mapOf("decision" to j.text.removePrefix(REJECTED)), false, subtitle = "Les députés ont rejeté le texte du gouvernement.")
            }
        }
        s.events.news.filter { it.time > since }.forEach { n ->
            val strong = n.category.name in setOf("DISASTER", "MILITARY")
            // Un fait local fait moins souvent la une nationale qu'un fait national.
            val local = if (n.focusId != null && !strong) LOCAL_NEWS else 1.0
            list += Story("news", (if (strong) NEWS_STRONG else NEWS) * local, mapOf("headline" to n.headline), if (strong) false else null, subtitle = n.category.label)
        }
        val approval = s.stats.series["approval"]
        val approvalBefore = approval?.ago(MONTH)
        if (approvalBefore != null) {
            val d = s.opinion.nationalApproval - approvalBefore
            if (abs(d) >= APPROVAL_MOVE) list += Story(if (d > 0) "approval_up" else "approval_down", MOVE + abs(d) * MOVE_SCALE,
                mapOf("value" to Formatting.wholePercent(s.opinion.nationalApproval), "before" to Formatting.wholePercent(approvalBefore)), d > 0, social = true,
                subtitle = "${Formatting.wholePercent(s.opinion.nationalApproval)} des Français approuvent l'action du président.")
        }
        val unemploymentBefore = s.stats.series["unemployment"]?.ago(MONTH)
        if (unemploymentBefore != null && abs(e.unemployment - unemploymentBefore) >= UNEMPLOYMENT_MOVE) {
            val up = e.unemployment > unemploymentBefore
            list += Story(if (up) "unemployment_up" else "unemployment_down", MOVE, mapOf("value" to Formatting.percent(e.unemployment), "before" to Formatting.percent(unemploymentBefore)),
                !up, economic = true, social = true, subtitle = "Le taux de chômage s'établit à ${Formatting.percent(e.unemployment)}.")
        }
        if (e.realGrowth < WEAK_GROWTH) list += Story("growth_weak", ECONOMY, mapOf("value" to Formatting.signedPercent(e.realGrowth)), false, economic = true, subtitle = "Croissance sur un an : ${Formatting.signedPercent(e.realGrowth)}.")
        if (e.realGrowth > STRONG_GROWTH) list += Story("growth_strong", ECONOMY, mapOf("value" to Formatting.signedPercent(e.realGrowth)), true, economic = true, subtitle = "Croissance sur un an : ${Formatting.signedPercent(e.realGrowth)}.")
        if (e.deficitRatio > HIGH_DEFICIT) list += Story("deficit_high", ECONOMY_LOW, mapOf("value" to Formatting.percent(e.deficitRatio)), false, economic = true, subtitle = "Bruxelles demande moins de 3 % de déficit.")
        if (e.debtRatio > HIGH_DEBT) list += Story("debt_high", ECONOMY_LOW, mapOf("value" to Formatting.wholePercent(e.debtRatio)), false, economic = true, subtitle = "La charge de la dette pèse sur le budget.")
        if (e.inflation > HIGH_INFLATION) list += Story("inflation_high", INFLATION, mapOf("value" to Formatting.percent(e.inflation)), false, economic = true, social = true, subtitle = "Les prix ont augmenté de ${Formatting.percent(e.inflation)} en un an.")
        if (fr.president.engine.military.Geopolitics(ctx).isAtWar(s.player.countryId)) list += Story("war", WAR, emptyMap(), null, subtitle = "Les armées françaises sont engagées.")
        val days = ctx.now.daysUntil(s.elections.nextElection).toInt()
        if (days in 0 until ELECTION_DAYS) list += Story("election", ELECTION, mapOf("days" to days.toString()), null, subtitle = "La campagne présidentielle s'intensifie.")
        list += Story("calm", CALM, emptyMap(), null, subtitle = "Analyse de la semaine politique.")
        return list
    }

    private fun publishFrontPages(ctx: SimulationContext, def: MediaFile) {
        val stories = stories(ctx)
        val president = ctx.state.characters[ctx.state.player.presidentId]?.economicLeaning ?: 0.0
        val pages = ctx.state.media.frontPages
        val taken = mutableMapOf<Story, Int>()
        for (paper in def.papers) {
            val yesterday = pages.lastOrNull { it.paperId == paper.id }?.topic
            val story = stories.maxBy { st ->
                var w = st.weight
                if (paper.focus == "economy" && st.economic) w *= FOCUS_BOOST
                if (paper.focus == "social" && st.social) w *= FOCUS_BOOST
                if (st.topic == yesterday && st.topic != "news") w *= REPEAT_PENALTY
                // Chaque rédaction cherche sa propre une : un sujet déjà repris pèse moins.
                repeat(taken[st] ?: 0) { w *= SHARED_PENALTY }
                w + ctx.rng.nextDouble() * JITTER
            }
            taken[story] = (taken[story] ?: 0) + 1
            val stance = when {
                paper.leaning == 0.0 -> "neutral"
                abs(paper.leaning - president) < CLOSE -> "pro"
                else -> "con"
            }
            val variants = def.templates[story.topic]?.get(stance).orEmpty().ifEmpty { def.templates[story.topic]?.get("neutral").orEmpty() }
            if (variants.isEmpty()) continue
            var headline = variants[ctx.rng.nextInt(variants.size)]
            story.vars.forEach { (k, v) -> headline = headline.replace("{$k}", v) }
            val tone = when (stance) {
                "pro" -> if (story.good == false) Tone.NEUTRAL else Tone.GOOD
                "con" -> if (story.good == true) Tone.NEUTRAL else Tone.BAD
                else -> when (story.good) { true -> Tone.GOOD; false -> Tone.BAD; null -> Tone.NEUTRAL }
            }
            pages += FrontPage(ctx.now, paper.id, story.topic, headline.replaceFirstChar { it.uppercase() }, story.subtitle, tone)
        }
        while (pages.size > MAX_PAGES) pages.removeAt(0)
    }

    private fun publishPoll(ctx: SimulationContext, def: MediaFile) {
        if (def.institutes.isEmpty()) return
        val s = ctx.state
        val polls = s.media.polls
        val institute = def.institutes[polls.size % def.institutes.size]
        val approval = (s.opinion.nationalApproval + institute.bias + ctx.rng.nextGaussian() * POLL_NOISE).coerceIn(0.0, 1.0)
        val candidates = s.elections.candidates
        val intention = if (candidates.size >= 2) {
            runCatching { ElectionSimulator(ctx).simulate(candidates, POLL_NOISE).shares[s.player.presidentId] }.getOrNull()
        } else null
        val factors = ctx.playerData.socialGroups?.factors.orEmpty()
        val concern = factors.minByOrNull { s.opinion.factorScores[it.id] ?: 0.0 }?.label.orEmpty()
        polls += PollRelease(ctx.now, institute.id, approval, intention, concern)
        while (polls.size > MAX_POLLS) polls.removeAt(0)
    }

    private companion object {
        const val POLL_DAYS = 7
        const val MONTH = 4
        const val ADOPTED = "Adoptée : "
        const val REJECTED = "Rejetée : "
        const val DECISION = 3.0
        const val LAW = 3.5
        const val LAW_REJECTED = 4.5
        const val NEWS = 3.0
        const val NEWS_STRONG = 4.5
        const val LOCAL_NEWS = 0.55
        const val SHARED_PENALTY = 0.55
        const val MOVE = 2.0
        const val MOVE_SCALE = 50.0
        const val APPROVAL_MOVE = 0.02
        const val UNEMPLOYMENT_MOVE = 0.002
        const val ECONOMY = 1.5
        const val ECONOMY_LOW = 1.1
        const val INFLATION = 1.8
        const val WAR = 5.0
        const val ELECTION = 2.5
        const val CALM = 0.5
        const val WEAK_GROWTH = 0.005
        const val STRONG_GROWTH = 0.02
        const val HIGH_DEFICIT = 0.05
        const val HIGH_DEBT = 1.2
        const val HIGH_INFLATION = 0.03
        const val ELECTION_DAYS = 200
        const val FOCUS_BOOST = 1.6
        const val REPEAT_PENALTY = 0.4
        const val JITTER = 0.6
        const val CLOSE = 0.6
        const val POLL_NOISE = 0.015
        const val MAX_PAGES = 120
        const val MAX_POLLS = 60
    }
}
