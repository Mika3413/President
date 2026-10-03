package fr.president.engine.readout

import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.ProjectStatus
import fr.president.engine.util.Formatting

/** Fiches lisibles des territoires et équipements, ouvertes depuis la carte. */
class LocalReadouts(private val ctx: SimulationContext) {
    private val scales = ctx.db.readouts
    private val territoryDef = ctx.playerData.territory!!

    data class Sheet(
        val title: String,
        val subtitle: String,
        val indicators: List<Indicator>,
        val people: List<Pair<String, String>> = emptyList(),
        val problems: List<String> = emptyList(),
        val projects: List<String> = emptyList(),
    )

    fun department(code: String): Sheet {
        val d = ctx.state.territory.departments.getValue(code)
        val def = territoryDef.departments.first { it.code == code }
        val region = territoryDef.regions.first { it.code == d.region }
        val titles = ctx.playerData.government!!.localTitles
        return Sheet(
            title = "${def.name} (${def.code})",
            subtitle = region.name + " · " + Formatting.population(d.population),
            indicators = listOf(
                opinion(d.approval),
                scaled("Chômage", "unemployment", d.unemployment, "Taux local : ${Formatting.percent(d.unemployment)}"),
                Indicator("Revenus", incomeLabel(d.incomeIndex), Tone.NEUTRAL, "",
                    listOf("Indice de revenu" to Formatting.amount(d.incomeIndex * PERCENT))),
                Indicator("Territoire", if (d.urbanShare > URBAN) "Plutôt urbain" else if (d.urbanShare < RURAL) "Plutôt rural" else "Mixte",
                    Tone.NEUTRAL, "", listOf("Part urbaine" to Formatting.percent(d.urbanShare), "Part des 65 ans et +" to Formatting.percent(d.seniorShare))),
            ),
            people = listOfNotNull(
                person(d.presidentId, titles.departmentPresident),
                person(ctx.state.territory.regions[d.region]?.prefectId, titles.prefect),
            ),
            problems = problems(d.unemployment, d.approval),
            projects = projectsAt(territoryDef.cities.filter { it.department == code }.map { it.id } + code),
        )
    }

    fun region(code: String): Sheet {
        val r = ctx.state.territory.regions.getValue(code)
        val def = territoryDef.regions.first { it.code == code }
        val depts = ctx.state.territory.departments.values.filter { it.region == code }
        val population = depts.sumOf { it.population }
        val unemployment = depts.sumOf { it.unemployment * it.population } / population
        val titles = ctx.playerData.government!!.localTitles
        return Sheet(
            title = def.name,
            subtitle = "${depts.size} départements · " + Formatting.population(population),
            indicators = listOf(opinion(r.approval), scaled("Chômage", "unemployment", unemployment, "Moyenne régionale")),
            people = listOfNotNull(person(r.presidentId, titles.regionPresident), person(r.prefectId, titles.prefect)),
            problems = problems(unemployment, r.approval),
            projects = projectsAt(depts.map { it.code } + territoryDef.cities.filter { c -> depts.any { it.code == c.department } }.map { it.id }),
        )
    }

    fun city(id: String): Sheet {
        val c = ctx.state.territory.cities.getValue(id)
        val def = territoryDef.cities.first { it.id == id }
        val dept = ctx.state.territory.departments.getValue(c.department)
        return Sheet(
            title = def.name,
            subtitle = Formatting.population(def.population) + " · aire urbaine " + Formatting.population(def.urbanAreaPopulation),
            indicators = listOf(
                scaled("Satisfaction", "approval", c.satisfaction, "Humeur des habitants envers l'État"),
                scaled("Chômage (département)", "unemployment", dept.unemployment, ""),
            ),
            people = listOfNotNull(person(c.mayorId, ctx.playerData.government!!.localTitles.mayor)),
            problems = problems(dept.unemployment, c.satisfaction),
            projects = projectsAt(listOf(id)),
        )
    }

    fun infrastructure(id: String): Sheet {
        val infra = ctx.state.infrastructure.getValue(id)
        val def = ctx.catalog.item(id)!!
        val type = ctx.db.infrastructureTypes.getValue(def.type)
        val status = when {
            infra.closed -> "Fermée"
            !infra.isOperational(ctx.now) -> "À l'arrêt"
            else -> "En service"
        }
        val details = mutableListOf(
            "Type" to type.label,
            "Mise en service" to def.commissionedYear.toString(),
            "Employés" to Formatting.integer(def.employees.toLong()),
            "Coût d'entretien" to Formatting.billions(def.maintenanceCostMillions / THOUSAND * infra.maintenanceLevel) + "/an",
            "Incidents" to infra.incidents.toString(),
        )
        if (def.capacityMW > 0) {
            details.add(1, "Puissance" to Formatting.integer(def.capacityMW) + " MW")
            details.add(2, "Production estimée" to Formatting.integer(def.capacityMW * def.capacityFactor * HOURS_PER_YEAR / MWH_PER_TWH) + " TWh/an")
        }
        return Sheet(
            title = def.name,
            subtitle = type.label + " · " + status,
            indicators = listOf(
                Indicator("État", scales.describe("condition", infra.condition).label, scales.describe("condition", infra.condition).tone,
                    def.description, details),
                Indicator("Entretien", maintenanceLabel(infra.maintenanceLevel), Tone.NEUTRAL,
                    "Un entretien insuffisant augmente le risque d'incident."),
            ),
            projects = projectsAt(listOf(id)),
        )
    }

    fun base(id: String): Sheet {
        val def = ctx.catalog.bases.getValue(id)
        val units = ctx.state.military.units.values.filter { it.baseId == id }
        val unitDefs = ctx.playerData.military!!.units.associateBy { it.id }
        return Sheet(
            title = def.name,
            subtitle = "${def.branch} · ${units.size} unité(s) stationnée(s)",
            indicators = units.map { u ->
                val r = scales.describe("readiness", u.readiness)
                Indicator(unitDefs[u.id]?.name ?: u.id, "Disponibilité : ${Formatting.percent(u.readiness)}", r.tone,
                    "Moral : ${scales.describe("morale", u.morale).label} · Munitions : ${scales.describe("stock", u.ammunition).label} · " +
                        "Carburant : ${scales.describe("stock", u.fuel).label} · Fatigue : ${scales.describe("fatigue", u.fatigue).label}",
                    listOf("Effectifs" to Formatting.integer(u.personnel.toLong())) + u.equipment.map { (k, v) -> k to v.toString() })
            },
        )
    }

    private fun opinion(value: Double): Indicator {
        val s = scales.describe("approval", value)
        return Indicator("Opinion", s.label, s.tone, "Soutien local au président", listOf("Approbation estimée" to Formatting.percent(value)))
    }

    private fun scaled(label: String, scale: String, value: Double, explanation: String): Indicator {
        val s = scales.describe(scale, value)
        return Indicator(label, s.label, s.tone, explanation)
    }

    private fun person(id: String?, title: String): Pair<String, String>? {
        val c = id?.let { ctx.state.characters[it] } ?: return null
        return title to "${c.fullName} — ${CharacterReadout(ctx).relationLabel(c.relationWithPlayer)}"
    }

    private fun problems(unemployment: Double, approval: Double): List<String> {
        val national = ctx.state.playerCountry.economy.unemployment
        return listOfNotNull(
            "Chômage nettement supérieur à la moyenne nationale".takeIf { unemployment > national + LOCAL_GAP },
            "Fort mécontentement envers l'exécutif".takeIf { approval < LOW_APPROVAL },
        )
    }

    private fun projectsAt(locations: List<String>): List<String> = ctx.state.projects
        .filter { it.status == ProjectStatus.IN_PROGRESS && it.locationId in locations }
        .map { "${it.name} — ${Formatting.percent(it.progress(ctx.now))}" }

    private fun incomeLabel(index: Double) = when {
        index > HIGH_INCOME -> "Élevés"
        index < LOW_INCOME -> "Modestes"
        else -> "Moyens"
    }

    private fun maintenanceLabel(level: Double) = when {
        level < REDUCED -> "Réduit"
        level > REINFORCED -> "Renforcé"
        else -> "Normal"
    }

    private companion object {
        const val PERCENT = 100.0
        const val THOUSAND = 1000.0
        const val URBAN = 0.7
        const val RURAL = 0.35
        const val LOCAL_GAP = 0.015
        const val LOW_APPROVAL = 0.33
        const val HIGH_INCOME = 1.1
        const val LOW_INCOME = 0.92
        const val REDUCED = 0.9
        const val REINFORCED = 1.1
        const val HOURS_PER_YEAR = 8766.0
        const val MWH_PER_TWH = 1_000_000.0
    }
}
