package fr.president.engine.legislation

import fr.president.engine.effects.EffectSpec
import fr.president.engine.time.WorldTime
import kotlinx.serialization.Serializable

/** Voie par laquelle un changement entre en vigueur. */
@Serializable
enum class Channel(val label: String, val short: String) {
    BUDGET("Loi de finances", "Budget"),
    LAW("Loi ordinaire", "Loi"),
    DECREE("Décret", "Décret"),
}

/** D'où vient un levier (où sa valeur unique est rangée). */
@Serializable
enum class LeverSource { TAX, SPENDING, FISCAL, LAW, REFORM, PARAM, MEASURE }

@Serializable
data class ReformLink(val reform: String, val above: Double? = null, val below: Double? = null) {
    fun holds(v: Double): Boolean = (above == null || v > above + 1e-9) && (below == null || v < below - 1e-9)
}

/** Réglage chiffré qui n'est ni un impôt du budget ni une loi du catalogue (âge de la retraite, SMIC...). */
@Serializable
data class ParameterDef(
    val id: String,
    val channel: Channel,
    val domain: String,
    val label: String,
    val description: String = "",
    val unit: String = "",
    /** « years » : affiché en années et mois. */
    val format: String = "number",
    val decimals: Int = 0,
    val reference: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    /** Les effets sont donnés pour un changement de [per] unités. */
    val per: Double = 1.0,
    val up: List<EffectSpec> = emptyList(),
    val down: List<EffectSpec> = emptyList(),
    val difficultyUp: Double = 0.0,
    val difficultyDown: Double = 0.0,
    val appealUp: Double = 0.0,
    val appealDown: Double = 0.0,
    /** Coût budgétaire annuel (Md€) par unité ; imputé au poste [spendingItem] s'il est donné. */
    val costPerUnit: Double = 0.0,
    val spendingItem: String? = null,
    /** On ne peut pas revenir en arrière (le SMIC ne se baisse pas). */
    val noDecrease: Boolean = false,
    /** Variation au-delà de laquelle un décret risque l'annulation par le Conseil d'État. */
    val decreeLimit: Double = 0.0,
    val links: List<ReformLink> = emptyList(),
)

@Serializable
data class DomainDef(val id: String, val label: String, val icon: String = "")

@Serializable
data class BanDef(
    val label: String = "Interdire",
    /** Recettes perdues (Md€ par an ; négatif : recettes gagnées, par exemple moins de fraude). */
    val revenueLoss: Double = 0.0,
    val effects: List<EffectSpec> = emptyList(),
    val liberty: Double = 0.0,
    val risk: Double = 0.0,
)

@Serializable
data class ObligationDef(val label: String, val unit: String = "ans", val max: Double = 5.0, val step: Double = 1.0, val risk: Double = 0.1)

/** Cible du constructeur : profession, groupe, entreprise, produit ou pratique, chiffrés. */
@Serializable
data class BuilderTarget(
    val id: String,
    val category: String,
    /** « les médecins libéraux » (dans une phrase). */
    val label: String,
    val short: String,
    val count: Double = 0.0,
    /** Revenu annuel moyen (€). */
    val income: Double = 0.0,
    /** Assiette (Md€ par an) pour les entreprises et les produits. */
    val base: Double = 0.0,
    val baseLabel: String = "",
    /** Sensibilité aux prélèvements : fuite, optimisation, baisse d'activité. */
    val mobility: Double = 0.2,
    val groups: Map<String, Double> = emptyMap(),
    val actors: Map<String, Double> = emptyMap(),
    val quality: String? = null,
    val qualityWeight: Double = 0.0,
    val sector: String? = null,
    val public: Boolean = false,
    /** Coût employeur annuel d'un poste (€). */
    val salary: Double = 0.0,
    val event: String? = null,
    val rural: Double = 0.2,
    val idf: Double = 0.19,
    /** Dispersion des revenus (loi log-normale). */
    val sigma: Double = 0.6,
    val priceWeight: Double = 0.0,
    val health: Double = 0.0,
    val environment: Double = 0.0,
    val symbolic: Double = 0.0,
    val richTax: Boolean = false,
    val foreign: String? = null,
    val normalIncrease: Double = 0.0,
    val capSector: String? = null,
    val capWinners: Map<String, Double> = emptyMap(),
    val capLosers: Map<String, Double> = emptyMap(),
    /** Coût budgétaire (Md€/an) d'un gel complet des prix (bouclier compensé par l'État). */
    val capCost: Double = 0.0,
    val ban: BanDef? = null,
    val obligation: ObligationDef? = null,
)

@Serializable
enum class MeasureModel { SURTAX, TAX_CUT, BONUS, TURNOVER_TAX, SUBSIDY, PRICE_CAP, BAN, OBLIGATION, RECRUIT, PAY_RAISE, JOB_CUTS }

@Serializable
data class BuilderAction(
    val id: String,
    val verb: String,
    /** Libellé du bouton dans le constructeur. */
    val button: String = "",
    val model: MeasureModel,
    val categories: List<String>,
    val unit: String = "",
    val min: Double,
    val max: Double,
    val step: Double,
    val default: Double,
    val channel: Channel,
    val conditions: List<String> = emptyList(),
    /** Champ que la cible doit avoir (public, ban, obligation, normalIncrease). */
    val requires: String? = null,
    val phrase: String = "",
)

@Serializable
data class BuilderFile(
    val targets: List<BuilderTarget>,
    val actions: List<BuilderAction>,
    val categories: List<DomainDef> = emptyList(),
    val thresholds: List<Double> = emptyList(),
    val below: List<Double> = emptyList(),
    val zones: List<DomainDef> = emptyList(),
    val durations: List<Int> = emptyList(),
)

@Serializable
data class LegislationFile(
    val parameters: List<ParameterDef> = emptyList(),
    val incidence: Map<String, Map<String, Double>> = emptyMap(),
    val domains: List<DomainDef> = emptyList(),
    val builder: BuilderFile? = null,
)

/** Réglage d'une mesure du constructeur ; sa clé (sans la durée) identifie la règle. */
@Serializable
data class MeasureConfig(
    val action: String,
    val target: String,
    /** Revenus au-dessus de (ou en dessous de, pour une prime) : 0 = tous. */
    val threshold: Double = 0.0,
    val zone: String = "national",
    /** Exonérer les zones rurales (là où le service manque). */
    val exemptRural: Boolean = false,
    /** Années ; 0 = permanente. */
    val durationYears: Int = 0,
    val phaseIn: Boolean = false,
) {
    val key: String get() = "m:$action:$target:${threshold.toLong()}:$zone:${if (exemptRural) 1 else 0}"
}

@Serializable
class MeasureState(val config: MeasureConfig, var value: Double = 0.0, var since: WorldTime? = null, var until: WorldTime? = null)

/** Un changement : un levier, sa valeur avant et la valeur visée. */
@Serializable
class LeverChange(
    val lever: String,
    val from: Double,
    var to: Double,
    var censured: Boolean = false,
    /** Configuration d'une mesure nouvelle (constructeur). */
    val measure: MeasureConfig? = null,
)

@Serializable
class LawDraft(var name: String? = null, val changes: MutableList<LeverChange> = mutableListOf())

@Serializable
enum class BillStatus(val label: String) { ADOPTED("Adoptée"), FORCED("Adoptée sans vote (49.3)"), REFERENDUM("Adoptée par référendum"), REJECTED("Rejetée"), DECREE("Décret"), ABROGATED("Abrogée"), ARTICLE_16("Imposée (article 16)") }

/** Texte entré en vigueur (ou rejeté) : l'historique législatif du mandat. */
@Serializable
class BillRecord(
    val id: String,
    val title: String,
    val channel: Channel,
    val time: WorldTime,
    var status: BillStatus,
    val changes: List<LeverChange>,
    val censured: List<String> = emptyList(),
)

@Serializable
class BillReferendum(val proposalId: String, val at: WorldTime)

@Serializable
class DecreeContest(val lever: String, val from: Double, val to: Double, val at: WorldTime, val chance: Double)

@Serializable
class LegislationState(
    /** Valeurs des réglages chiffrés (absent = valeur de référence). */
    val params: MutableMap<String, Double> = mutableMapOf(),
    /** Mesures du constructeur en vigueur (clé = règle). */
    val measures: MutableMap<String, MeasureState> = mutableMapOf(),
    /** Projet de budget en préparation : levier -> valeur visée. */
    val budgetDraft: MutableMap<String, Double> = mutableMapOf(),
    /** Mesures nouvelles du constructeur en attente dans le projet de budget. */
    val budgetMeasures: MutableMap<String, MeasureConfig> = mutableMapOf(),
    var lawDraft: LawDraft = LawDraft(),
    val bills: MutableList<BillRecord> = mutableListOf(),
    /** Dernière loi de finances annuelle déposée (année du budget). */
    var plfYear: Int = 0,
    var plfProposal: String? = null,
    var correctiveCount: Int = 0,
    val referendums: MutableList<BillReferendum> = mutableListOf(),
    val contests: MutableList<DecreeContest> = mutableListOf(),
    val lastDecree: MutableMap<String, WorldTime> = mutableMapOf(),
    /** Points d'indice de libertés retirés ou ajoutés par les mesures du constructeur. */
    var libertyOffset: Double = 0.0,
    /** Pleins pouvoirs (article 16) jusqu'à cette date : les projets de loi s'appliquent sans vote. */
    var article16Until: WorldTime? = null,
)
