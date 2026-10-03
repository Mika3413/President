package fr.president.engine.events

import fr.president.engine.politics.Character
import fr.president.engine.simulation.SimulationContext

/** Détermine qui écrit au président pour un événement donné, et sous quel titre. */
class SenderResolver(private val ctx: SimulationContext) {

    data class Sender(val character: Character?, val title: String, val label: String)

    fun resolve(role: SenderRole, ministry: String?, scope: ScopeRef?): Sender {
        val titles = ctx.playerData.government!!.localTitles
        val territory = ctx.state.territory
        val deptCode = departmentOf(scope)
        val dept = deptCode?.let { territory.departments[it] }
        return when (role) {
            SenderRole.MAYOR -> {
                val city = scope?.id?.let { territory.cities[it] }
                sender(city?.mayorId, "${titles.mayor} de ${cityName(city?.id)}")
            }
            SenderRole.PREFECT -> sender(dept?.let { territory.regions[it.region]?.prefectId }, "${titles.prefect} (${regionName(dept?.region)})")
            SenderRole.REGION_PRESIDENT -> sender(dept?.let { territory.regions[it.region]?.presidentId }, "${titles.regionPresident} (${regionName(dept?.region)})")
            SenderRole.DEPARTMENT_PRESIDENT -> sender(dept?.presidentId, "${titles.departmentPresident} (${departmentName(deptCode)})")
            SenderRole.MINISTER -> {
                val m = ctx.playerData.government!!.ministries.first { it.id == ministry }
                sender(ctx.state.government.ministers[m.id], m.title)
            }
            SenderRole.PRIME_MINISTER -> sender(ctx.state.government.primeMinisterId, ctx.playerData.definition.institutions.headOfGovernmentTitle)
            SenderRole.SUBJECT -> sender(scope?.id, ministryTitleOf(scope?.id))
            SenderRole.FOREIGN_LEADER -> {
                val country = scope?.id?.let { ctx.state.countries[it] }
                val def = scope?.id?.let { ctx.db.country(it).definition }
                sender(country?.leaderId, "${def?.institutions?.headOfGovernmentTitle ?: ""} (${def?.name ?: ""})")
            }
            SenderRole.NONE -> Sender(null, "", "Cabinet présidentiel")
        }
    }

    private fun sender(id: String?, title: String): Sender {
        val c = id?.let { ctx.state.characters[it] }
        val label = c?.let { "${it.fullName}, $title" } ?: title
        return Sender(c, title, label)
    }

    fun departmentOf(scope: ScopeRef?): String? {
        val id = scope?.id ?: return null
        return when (scope.type) {
            EventScope.DEPARTMENT -> id
            EventScope.CITY -> ctx.state.territory.cities[id]?.department
            EventScope.INFRASTRUCTURE -> ctx.catalog.departmentOf(id)
            else -> null
        }
    }

    fun cityName(id: String?): String = ctx.playerData.territory!!.cities.firstOrNull { it.id == id }?.name ?: ""
    fun departmentName(code: String?): String = ctx.playerData.territory!!.departments.firstOrNull { it.code == code }?.name ?: ""
    fun regionName(code: String?): String = ctx.playerData.territory!!.regions.firstOrNull { it.code == code }?.name ?: ""

    private fun ministryTitleOf(characterId: String?): String {
        val ministry = ctx.state.government.ministers.entries.firstOrNull { it.value == characterId }?.key
        return ctx.playerData.government!!.ministries.firstOrNull { it.id == ministry }?.title ?: ""
    }
}
