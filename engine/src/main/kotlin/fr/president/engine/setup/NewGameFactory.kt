package fr.president.engine.setup

import fr.president.engine.data.CountryData
import fr.president.engine.data.DetailLevel
import fr.president.engine.data.GameDatabase
import fr.president.engine.diplomacy.DiplomaticMemory
import fr.president.engine.economy.ServiceQualitySystem
import fr.president.engine.elections.ElectionService
import fr.president.engine.elections.ElectionState
import fr.president.engine.energy.EnergyState
import fr.president.engine.energy.EnergySystem
import fr.president.engine.infrastructure.InfrastructureState
import fr.president.engine.military.UnitState
import fr.president.engine.opinion.GroupOpinion
import fr.president.engine.opinion.OpinionSystem
import fr.president.engine.politics.CharacterRole
import fr.president.engine.politics.CharacterSpec
import fr.president.engine.politics.CharacterGenerator
import fr.president.engine.simulation.SimulationContext
import fr.president.engine.territory.CityState
import fr.president.engine.territory.DepartmentState
import fr.president.engine.territory.RegionState
import fr.president.engine.time.WorldClock
import fr.president.engine.time.WorldTime
import fr.president.engine.util.GameRandom
import fr.president.engine.world.CountryState
import fr.president.engine.world.PlayerState
import fr.president.engine.world.WorldMeta
import fr.president.engine.world.WorldState

/**
 * Crée une partie à partir d'un snapshot de données réelles.
 * Après création, l'état est autonome : mettre à jour les données n'affecte plus la sauvegarde.
 */
class NewGameFactory(private val db: GameDatabase) {

    fun create(options: NewGameOptions): WorldState {
        val countryId = options.countryId ?: db.snapshot.playableCountries.first()
        require(countryId in db.snapshot.playableCountries) { "$countryId n'est pas jouable dans ce snapshot" }
        val country = db.country(countryId)
        val pace = db.config.pace(options.paceId)
        val start = WorldTime.parse(db.snapshot.startDate)
        val rng = GameRandom(options.seed)

        val president = CharacterGenerator(db).generate(
            PRESIDENT_ID,
            CharacterSpec(countryId, CharacterRole.PRESIDENT, null, start.toDateTime().year,
                ageRange = PRESIDENT_AGES, economicLeaning = options.economicLeaning, leaningSpread = 0.0, female = options.presidentFemale,
                socialLeaning = options.socialLeaning),
            rng,
        ).let { generated ->
            if (options.presidentFirstName.isNullOrBlank() && options.presidentLastName.isNullOrBlank()) generated
            else fr.president.engine.politics.Character(
                generated.id, options.presidentFirstName?.trim()?.ifBlank { null } ?: generated.firstName,
                options.presidentLastName?.trim()?.ifBlank { null } ?: generated.lastName,
                generated.female, generated.birthYear, countryId, CharacterRole.PRESIDENT,
                traits = generated.traits, competence = generated.competence, management = generated.management,
                experience = generated.experience, popularity = generated.popularity, loyalty = 1.0, relationWithPlayer = 1.0,
                economicLeaning = generated.economicLeaning, socialLeaning = generated.socialLeaning, portraitSeed = generated.portraitSeed,
            )
        }
        val termYears = country.elections?.termYears ?: DEFAULT_TERM_YEARS
        val state = WorldState(
            meta = WorldMeta(
                db.snapshot.id, db.snapshot.dataVersion, options.seed,
                WorldClock(pace.id, pace.worldHoursPerRealHour, options.nowRealUtcMillis, start),
                options.nowRealUtcMillis, start,
            ),
            time = start,
            rng = rng,
            player = PlayerState(countryId, president.id, start),
            energy = EnergyState(country.energy?.electricityDemandTWh ?: 0.0),
            elections = ElectionState(start.plusYears(termYears)),
        )
        state.characters[president.id] = president.also { it.knownTraits += it.traits.keys }

        val ctxCountries = db.countries.values
        ctxCountries.forEach { state.countries[it.id] = createCountry(it, state, rng) }
        // Le dirigeant du pays joueur est le président ; on retire le dirigeant générique.
        state.countries.getValue(countryId).let { player ->
            state.characters.remove(player.leaderId)
            player.leaderId = president.id
        }

        val ctx = SimulationContext(state, db)
        createTerritory(country, state)
        createInfrastructure(country, state)
        createMilitary(country, state)
        PoliticalSetup(ctx).apply {
            createGovernment(country, president)
            createLocalActors(country)
        }
        fr.president.engine.military.MilitarySetup(ctx).ensure()
        initOpinion(ctx, country)
        val maxPromises = country.promises?.maxPromises ?: 0
        state.player.promises += options.promises.filter { id -> country.promises?.promises?.any { it.id == id } == true }.distinct().take(maxPromises)
        fr.president.engine.elections.PromiseEvaluator(ctx).setBaselines()
        initRelations(state)
        EnergySystem().run(ctx)
        state.opinion.startValues[EnergySystem.REFERENCE_MARGIN_KEY] = state.energy.margin
        EnergySystem().run(ctx)
        OpinionSystem().run(ctx)
        ElectionService(ctx).apply {
            prepareCandidates()
            schedule()
            poll()
        }
        fr.president.engine.government.ParliamentService(ctx).ensure()
        fr.president.engine.elections.LocalElectionService(ctx).ensure()
        fr.president.engine.government.SenateService(ctx).ensure()
        WelcomeMessage(ctx).send()
        WelcomeMessage(ctx).scheduleTutorial()
        return state
    }

    private fun createCountry(data: CountryData, state: WorldState, rng: GameRandom): CountryState {
        val def = data.definition
        val leader = CharacterGenerator(db).generate(
            state.newId("chr"),
            CharacterSpec(
                def.id, CharacterRole.FOREIGN_LEADER, def.id, state.time.toDateTime().year,
                ageRange = def.leader.ageRange[0]..def.leader.ageRange[1],
                economicLeaning = def.leader.economicLeaningRange.average(),
                leaningSpread = (def.leader.economicLeaningRange[1] - def.leader.economicLeaningRange[0]) / 2,
                traitRanges = def.leader.traitRanges,
            ),
            rng,
        )
        state.characters[leader.id] = leader
        val country = CountryState(
            id = def.id,
            detail = def.detail,
            leaderId = leader.id,
            economy = EconomyFactory.create(data.economy),
            population = def.population,
            electricityBalanceTWh = def.strategic.electricityBalanceTWh,
            militaryBudgetBillions = def.strategic.militaryBudgetBillions,
            nextAiDecision = state.time.plusDays(rng.nextDouble(FIRST_AI_DECISION_MIN, FIRST_AI_DECISION_MAX)),
        )
        if (def.detail == DetailLevel.FULL) data.government?.initialServiceQuality?.let { country.services.putAll(it) }
        return country
    }

    private fun createTerritory(country: CountryData, state: WorldState) {
        val territory = country.territory ?: return
        val unemployment = country.economy.unemployment
        val regions = territory.regions.associateBy { it.code }
        territory.regions.forEach { state.territory.regions[it.code] = RegionState(it.code) }
        for (d in territory.departments) {
            val defaults = regions.getValue(d.region).defaults
            val local = d.profile.unemployment ?: defaults.unemployment ?: unemployment
            state.territory.departments[d.code] = DepartmentState(
                code = d.code,
                region = d.region,
                population = d.population,
                unemploymentOffset = local - unemployment,
                unemployment = local,
                incomeIndex = d.profile.incomeIndex ?: defaults.incomeIndex ?: 1.0,
                urbanShare = d.profile.urbanShare ?: defaults.urbanShare ?: DEFAULT_URBAN,
                seniorShare = d.profile.seniorShare ?: defaults.seniorShare ?: DEFAULT_SENIOR,
                politicalLeaning = d.profile.politicalLeaning ?: defaults.politicalLeaning ?: 0.0,
            ).apply {
                healthAccess = d.profile.healthAccess ?: defaults.healthAccess ?: 1.0
                crime = d.profile.crime ?: defaults.crime ?: 1.0
                industryShare = d.profile.industryShare ?: defaults.industryShare ?: DEFAULT_INDUSTRY
                agricultureShare = d.profile.agricultureShare ?: defaults.agricultureShare ?: DEFAULT_AGRICULTURE
                pollution = d.profile.pollution ?: defaults.pollution ?: 1.0
            }
        }
        territory.cities.forEach { state.territory.cities[it.id] = CityState(it.id, it.department, it.population) }
    }

    private fun createInfrastructure(country: CountryData, state: WorldState) {
        val items = country.energy?.items.orEmpty() + country.transport?.items.orEmpty()
        items.forEach { state.infrastructure[it.id] = InfrastructureState(it.id, it.type, it.initialCondition) }
    }

    private fun createMilitary(country: CountryData, state: WorldState) {
        country.military?.units?.forEach { u ->
            state.military.units[u.id] = UnitState(
                u.id, u.branch, u.type, u.baseId, u.personnel, u.equipment.toMutableMap(),
                u.readiness, u.morale, u.ammunition, u.fuel,
            )
        }
        val units = state.military.units.values
        if (units.isNotEmpty()) {
            state.military.overallReadiness = units.sumOf { it.readiness * it.personnel } / units.sumOf { it.personnel }
        }
    }

    private fun initOpinion(ctx: SimulationContext, country: CountryData) {
        val groups = country.socialGroups ?: return
        val opinion = ctx.state.opinion
        groups.groups.forEach { opinion.groups[it.id] = GroupOpinion(it.baseApproval + groups.honeymoonBonus) }
        opinion.honeymoon = groups.honeymoonBonus
        groups.factors.filter { it.relativeToStart }.forEach { f ->
            ctx.variables.resolve(f.variable)?.let { opinion.startValues[f.variable] = it }
        }
        ctx.state.playerCountry.services.forEach { (domain, q) ->
            opinion.startValues[ServiceQualitySystem.START_PREFIX + domain] = q
        }
    }

    private fun initRelations(state: WorldState) {
        for (r in db.snapshot.initialRelations) {
            state.diplomacy.relation(r.a, r.b).memories += DiplomaticMemory(r.kind, r.weight, state.time, r.label)
            state.diplomacy.relation(r.b, r.a).memories += DiplomaticMemory(r.kind, r.weight, state.time, r.label)
        }
    }

    private companion object {
        const val PRESIDENT_ID = "chr-president"
        val PRESIDENT_AGES = 42..62
        const val DEFAULT_TERM_YEARS = 5
        const val DEFAULT_URBAN = 0.5
        const val DEFAULT_SENIOR = 0.21
        const val DEFAULT_INDUSTRY = 0.12
        const val DEFAULT_AGRICULTURE = 0.03
        const val FIRST_AI_DECISION_MIN = 4.0
        const val FIRST_AI_DECISION_MAX = 45.0
    }
}
