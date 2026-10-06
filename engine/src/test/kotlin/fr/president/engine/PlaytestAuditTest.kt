package fr.president.engine

import fr.president.engine.inbox.InboxSystem
import kotlin.test.Test

/**
 * Audit de parties complètes (lancé à la demande : -Daudit=true). Un joueur actif et raisonnable
 * gouverne cinq ans ; on relève tout ce qui cloche : valeurs absurdes, textes mal formés,
 * alertes en rafale, courriers sans réponse, conséquences, événements trop fréquents.
 */
class PlaytestAuditTest {
    private val badText = listOf(
        Regex("\\{[A-Za-z]") to "variable non remplacée", Regex("\\bnull\\b") to "null", Regex("NaN|Infinity") to "nombre invalide",
        Regex("  ") to "double espace", Regex(" [,.]") to "espace avant ponctuation", Regex("(?<!\\p{L})(de le|à le|de les|à les)\\b(?! \\p{L}+(er|ir|re|oir)\\b)") to "article mal contracté",
        Regex("\\.\\.(?!\\.)") to "double point", Regex("\\( *\\)") to "parenthèses vides",
    )

    @Test
    fun audit() {
        if (System.getProperty("audit") != "true") return
        val seeds = (System.getProperty("audit.seeds") ?: "11,22,33").split(',').map { it.trim().toLong() }
        val problems = sortedMapOf<String, MutableSet<String>>()
        fun problem(kind: String, detail: String) { problems.getOrPut(kind) { linkedSetOf() }.let { if (it.size < 25) it += detail.replace("\n", " ⏎ ") } }
        val titleCounts = mutableMapOf<String, Int>()
        val events = mutableMapOf<String, Int>()
        val consequences = mutableMapOf<String, Int>()
        for (seed in seeds) {
            val clock = TestData.FakeClock()
            val s = TestData.newSession(seed = seed, clock = clock)
            val countryNames = s.db.countries.values.map { it.definition }.filter { it.article.isNotEmpty() }.map { it.name }
            val bareCountry = Regex("\\b(de|à|contre|avec|entre) (" + countryNames.joinToString("|") { Regex.escape(it) } + ")\\b")
            val rng = java.util.Random(seed)
            var lastNotif = 0L
            val seenMsg = mutableSetOf<String>()
            var seenNews = 0
            var maxPending = 0
            var byDefault = 0
            var answered = 0
            var days = 0
            var maxInfl = 0.0; var maxOil = 0.0; var maxGas = 0.0
            fun checkText(where: String, text: String) {
                badText.forEach { (r, label) -> r.find(text)?.let { m -> problem("Texte : $label", "$where : « …${text.substring((m.range.first - 60).coerceAtLeast(0), (m.range.last + 60).coerceAtMost(text.length))}… »") } }
                bareCountry.find(text)?.takeIf { it.value != "de France" }?.let { m -> problem("Texte : pays sans article", "$where : « …${text.substring((m.range.first - 80).coerceAtLeast(0), (m.range.last + 40).coerceAtMost(text.length))}… »") }
                if (text.count { it == '«' } != text.count { it == '»' }) problem("Texte : guillemets déséquilibrés", "$where : « ${text.take(120)} »")
            }
            while (days < 5 * 365 && !s.isGameOver) {
                clock.advanceWorldDays(5.0); days += 5
                s.advanceToNow()
                val pending = s.state.inbox.messages.filter { it.awaitingAnswer }
                maxPending = maxOf(maxPending, pending.size)
                // Joueur actif : répond à presque tout, en choisissant l'option « acceptée » ou partielle si elle existe.
                pending.forEach { m ->
                    if (rng.nextDouble() < 0.85) {
                        val opt = m.options.firstOrNull { it.id in setOf("partial", "compromise", "accept") } ?: m.options[rng.nextInt(m.options.size)]
                        InboxSystem.answer(s.context, m, opt.id, byDefault = false); answered++
                    }
                }
                s.state.notifications.feed.filter { it.id > lastNotif }.forEach { n ->
                    if (n.urgency != fr.president.engine.notifications.Urgency.INFO) titleCounts.merge("[${n.urgency}] " + n.title.replace(Regex("[0-9]+"), "#"), 1, Int::plus)
                    checkText("Alerte", n.title); checkText("Alerte (texte)", n.body)
                }
                lastNotif = s.state.notifications.feed.lastOrNull()?.id ?: lastNotif
                s.state.inbox.messages.filter { seenMsg.add(it.id) }.forEach { m ->
                    checkText("Courrier (objet)", m.subject); checkText("Courrier", m.body)
                    m.options.forEach { o -> checkText("Option", o.label) }
                }
                s.state.inbox.messages.count { it.answeredByDefault }.let { byDefault = it }
                val news = s.state.events.news
                news.drop(seenNews.coerceAtMost(news.size)).forEach { checkText("Brève", it.headline) }
                seenNews = news.size
                // Valeurs absurdes.
                val e = s.state.playerCountry.economy
                listOf("chômage" to e.unemployment, "inflation" to e.inflation, "croissance" to e.realGrowth, "dette" to e.debtRatio, "déficit" to e.deficitRatio,
                    "confiance ménages" to e.consumerConfidence, "taux" to e.marketRate).forEach { (k, v) ->
                    if (v.isNaN() || v.isInfinite()) problem("Valeur invalide", "$k = $v (jour $days, graine $seed)")
                }
                if (e.inflation > 0.15 || e.inflation < -0.05) problem("Économie hors norme", "inflation ${"%.3f".format(e.inflation)} (jour $days, graine $seed)")
                if (e.realGrowth > 0.08 || e.realGrowth < -0.1) problem("Économie hors norme", "croissance ${"%.3f".format(e.realGrowth)} (jour $days, graine $seed)")
                if (e.unemployment > 0.2) problem("Économie hors norme", "chômage ${"%.3f".format(e.unemployment)} (jour $days, graine $seed)")
                s.state.opinion.groups.forEach { (g, o) -> if (o.effective.isNaN() || o.effective !in 0.0..1.0) problem("Opinion hors bornes", "$g = ${o.effective}") }
                s.state.territory.departments.values.forEach { d ->
                    if (d.crime.isNaN() || d.crime > 5 || d.healthAccess.isNaN() || d.healthAccess < 0.2 || d.pollution > 5)
                        problem("Département hors norme", "${d.code} crime=${"%.2f".format(d.crime)} soins=${"%.2f".format(d.healthAccess)} pollution=${"%.2f".format(d.pollution)} (jour $days)")
                }
                val so = s.state.society
                if (so.rentIndex > 2.5 || so.fertility < 1.1 || so.lifeExpectancy < 78 || so.savingsRate > 0.3) problem("Vie quotidienne hors norme", "loyers=${"%.2f".format(so.rentIndex)} fécondité=${"%.2f".format(so.fertility)} vie=${"%.1f".format(so.lifeExpectancy)} épargne=${"%.2f".format(so.savingsRate)}")
                s.state.trade.commodities.forEach { (id, c) -> if (c.price.isNaN() || c.price <= 0 || c.price > (s.db.trade?.commodities?.first { it.id == id }?.basePrice ?: 1.0) * 5) problem("Cours hors norme", "$id = ${c.price}") }
                s.state.countries.values.forEach { c -> if (c.leaderApproval.isNaN() || c.economy.unemployment > 0.4 || c.economy.gdpBillions <= 0) problem("Pays étranger hors norme", "${c.id} popularité=${c.leaderApproval} chômage=${c.economy.unemployment} PIB=${c.economy.gdpBillions}") }
                s.state.consequences.current.values.filter { it.active }.forEach { consequences.merge(it.id, 5, Int::plus) }
                maxInfl = maxOf(maxInfl, e.inflation); maxOil = maxOf(maxOil, s.state.trade.commodities["oil"]?.price ?: 0.0); maxGas = maxOf(maxGas, s.state.trade.commodities["gas"]?.price ?: 0.0)
                if (days % 365 == 0) println("AUDIT an ${days / 365} graine $seed : inflation=${"%.3f".format(e.inflation)} énergie=${"%.2f".format(e.energyPriceIndex)} pétrole=${"%.0f".format(s.state.trade.commodities["oil"]?.price ?: 0.0)} gaz=${"%.0f".format(s.state.trade.commodities["gas"]?.price ?: 0.0)} croissance=${"%.3f".format(e.realGrowth)} chômage=${"%.3f".format(e.unemployment)} popularité=${"%.2f".format(s.state.opinion.nationalApproval)} logements=${"%.2f".format(s.state.society.housePriceIndex)}")
            }
            s.state.events.firedCount.forEach { (id, n) -> events.merge(id, n, Int::plus) }
            val e = s.state.playerCountry.economy
            println("AUDIT graine $seed : jours=$days fin=${s.state.player.gameOver?.reason} popularité=${"%.2f".format(s.state.opinion.nationalApproval)} chômage=${"%.3f".format(e.unemployment)} dette=${"%.2f".format(e.debtRatio)} " +
                "inflation max=${"%.3f".format(maxInfl)} pétrole max=${"%.0f".format(maxOil)} gaz max=${"%.0f".format(maxGas)} courriers en attente max=$maxPending, répondus=$answered, par défaut=$byDefault, monde=${s.state.world.entries.size}, guerres=${s.state.military.wars.size}, mouvements=${s.state.unrest.past.size}")
        }
        println("AUDIT alertes les plus répétées : " + titleCounts.entries.sortedByDescending { it.value }.take(25).joinToString(" | ") { "${it.key} ×${it.value}" })
        println("AUDIT événements les plus fréquents : " + events.entries.sortedByDescending { it.value }.take(25).joinToString(" | ") { "${it.key} ×${it.value}" })
        println("AUDIT événements jamais vus : " + (TestData.db.events.map { it.id } - events.keys).size + " / " + TestData.db.events.size)
        println("AUDIT conséquences (jours actives cumulés) : " + consequences.entries.sortedByDescending { it.value }.joinToString(" | ") { "${it.key} ${it.value} j" })
        problems.forEach { (k, v) -> println("AUDIT PROBLÈME [$k] ${v.size} cas : " + v.joinToString(" ‖ ")) }
    }
}
