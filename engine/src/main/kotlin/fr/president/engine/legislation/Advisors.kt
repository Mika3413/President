package fr.president.engine.legislation

import fr.president.engine.government.MinistryEffectiveness
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.util.Formatting
import kotlin.math.abs

/**
 * L'avis des ministres sur un changement, comme en réunion à l'Élysée : Bercy parle d'argent, le
 * ministre concerné parle de son domaine, l'Intérieur parle de la rue. Leurs chiffres sont des
 * estimations : plus le ministre est compétent, plus la fourchette est étroite.
 */
class Advisors(private val ctx: SimulationContext) {
    /** [stance] : +1 pour, 0 réservé, −1 contre. */
    data class Advice(val minister: String, val role: String, val stance: Int, val text: String)

    fun opinions(leverId: String, p: Preview): List<Advice> {
        val out = mutableListOf<Advice>()
        val eff = MinistryEffectiveness(ctx)
        val gov = ctx.playerData.government ?: return out
        fun who(id: String) = eff.ministerOf(id)?.fullName ?: "L'intérim"
        fun role(id: String) = gov.ministries.firstOrNull { it.id == id }?.shortTitle ?: id
        val economy = ctx.state.playerCountry.economy

        // Bercy : l'argent, avec une fourchette qui dépend de la compétence du ministre.
        if (abs(p.balance) >= MIN_BILLIONS) {
            val err = abs(p.balance) * (0.1 + 0.5 * (1 - eff.of("economy")))
            val low = abs(p.balance) - err
            val high = abs(p.balance) + err
            val range = "entre ${Formatting.billions(low.coerceAtLeast(0.0))} et ${Formatting.billions(high)} par an"
            out += if (p.balance < 0) {
                val strained = economy.deficitRatio > STRAINED_DEFICIT
                Advice(who("economy"), role("economy"), if (strained) -1 else 0,
                    "Coût estimé $range. " + if (strained) "Je m'y oppose : le déficit est déjà à ${Formatting.percent(economy.deficitRatio)} du PIB, Bruxelles et les marchés nous regardent."
                    else "C'est finançable, mais il faudra le payer un jour.")
            } else {
                val heavy = p.warnings.any { "impôt" in it.lowercase() || "exil" in it.lowercase() || "entreprises s'en vont" in it.lowercase() }
                Advice(who("economy"), role("economy"), if (heavy) 0 else 1,
                    "Gain estimé $range pour les finances publiques. " + if (heavy) "Attention : à ce niveau, une partie de la recette fuira." else "Bonne nouvelle pour le déficit.")
            }
        }

        // Le ministre du domaine touché.
        domainMinistry(leverId, p)?.let { id ->
            val domain = gov.ministries.firstOrNull { it.id == id }?.domain
            val q = domain?.let { p.quality[it] } ?: p.quality.values.sum()
            val warning = p.warnings.firstOrNull()?.substringBefore(" :")
            out += when {
                warning != null -> Advice(who(id), role(id), -1, "Je m'y oppose : $warning. On le paiera cher sur le terrain.")
                q > SMALL -> Advice(who(id), role(id), 1, "Indispensable : mon administration en a besoin, le service s'améliorera.")
                q < -SMALL -> Advice(who(id), role(id), -1, "Le service va se dégrader, et ce sont les plus fragiles qui le sentiront d'abord.")
                p.groups.values.sum() < -SMALL -> Advice(who(id), role(id), 0, "Techniquement faisable, mais les personnes concernées vont mal le prendre.")
                else -> Advice(who(id), role(id), 1, "Rien à redire de mon côté.")
            }
        }

        // L'Intérieur : la rue.
        if (p.events.isNotEmpty() || p.groups.values.count { it < -0.01 } >= 2) {
            out += Advice(who("interior"), role("interior"), 0, "Attendez-vous à des grèves et des manifestations. Mes services se préparent.")
        }
        return out.distinctBy { it.role }
    }

    /** Ministère compétent pour un levier : crédits d'un poste, réglage social, mesure ciblée... */
    private fun domainMinistry(leverId: String, p: Preview): String? {
        val gov = ctx.playerData.government ?: return null
        // Les retraites relèvent du ministre du Travail et des Solidarités.
        fun byDomain(d: String?) = d?.let { dom -> gov.ministries.firstOrNull { it.domain == (if (dom == "pensions") "social" else dom) }?.id }
        val key = leverId.substringAfter(':')
        return when {
            leverId.startsWith("spend:") -> byDomain(ctx.state.playerCountry.economy.budget?.spending?.get(key)?.domain)
            leverId.startsWith("param:") && key in SOCIAL -> "labour"
            leverId.startsWith("param:") && key == "speed_limit" -> "interior"
            leverId.startsWith("param:") && key == "civil_service_index" -> "pm"
            leverId.startsWith("law:") -> "justice"
            else -> byDomain(p.quality.maxByOrNull { abs(it.value) }?.key)
        }
    }

    private companion object {
        const val MIN_BILLIONS = 0.3
        const val STRAINED_DEFICIT = 0.045
        const val SMALL = 0.002
        val SOCIAL = setOf("rsa_amount", "smic_boost", "pension_age", "pension_indexation", "housing_aid", "family_allowance", "activity_bonus", "apprentice_aid")
    }
}
