"""Conséquences en chaîne à seuils -> assets/data/config/consequences.json.

Chaque règle surveille une variable de la simulation (qualité d'un service, crédits d'un poste,
RSA rapporté au SMIC, poids des impôts, déficit, libertés...). Quand elle franchit son seuil, le
pays réagit chaque mois, d'autant plus fort que l'écart est grand (gravité 0 à 3) :
  - effets mensuels (opinion, économie, services, acteurs, relations...), certains plafonnés et
    qui se résorbent lentement une fois la cause disparue ;
  - effets sur la carte (criminalité, accès aux soins, chômage, pollution, popularité locale),
    pondérés par département (villes, campagnes, zones pauvres, zones à fort chômage) ;
  - événements plus fréquents (émeutes, grèves, faillites, attentats...) ;
  - mouvements de contestation attisés.
Chaque règle explique son mécanisme (« Pourquoi ») et la sortie possible.
"""
import glob, json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, cap=None):
    d = {"target": target, "amount": amount}
    if cap is not None: d["cap"] = cap
    return d


def g(group, amount): return e(G + group, amount)
def a(actor, amount): return e("actor." + actor, amount)
def d(field, amount, weight="all"): return {"field": field, "amount": amount, "weight": weight}


RULES = []


def rule(id, label, icon, category, variable, why, onset, fix, scale, above=None, below=None, severe="", monthly=(), departments=(), events=None, unrest=None, max_severity=3.0):
    r = {"id": id, "label": label, "icon": icon, "category": category, "variable": variable, "scale": scale,
         "why": why, "onset": onset, "fix": fix}
    if above is not None: r["above"] = above
    if below is not None: r["below"] = below
    if severe: r["severe"] = severe
    if max_severity != 3.0: r["maxSeverity"] = max_severity
    if monthly: r["monthly"] = list(monthly)
    if departments: r["departments"] = list(departments)
    if events: r["events"] = events
    if unrest: r["unrest"] = unrest
    RULES.append(r)


# ---------------------------------------------------------------------------------------------
# Services publics à bout de souffle (qualité sous un seuil)
# ---------------------------------------------------------------------------------------------
rule("security_collapse", "L'insécurité explose", "⚠", "services", "quality.security",
     "Moins de policiers et de gendarmes sur le terrain : les délits ne sont plus élucidés, les trafics s'installent, les quartiers difficiles échappent au contrôle.",
     "Cambriolages, agressions et trafics augmentent partout ; les maires réclament des renforts.",
     "remonter les crédits de la police, recruter des policiers et des gendarmes.",
     0.08, below=0.38, severe="Des zones de non-droit se multiplient",
     monthly=[g("urban", -0.004), g("seniors", -0.004), g("rural", -0.002), e("president.popularity", -0.003),
              e("economy.consumerConfidence", -0.002), e("sector.tourism", -0.002), e("sector.retail", -0.001)],
     departments=[d("crime", 0.015, "urban"), d("crime", 0.01, "poor"), d("approval", -0.002, "crime")],
     events={"urban_riots": 0.6, "police_incident": 0.4, "prison_escape": 0.3, "stadium_violence": 0.4, "local_violence_school": 0.3},
     unrest={"police": 0.04, "anger": 0.02})

rule("health_collapse", "Hôpitaux à bout de souffle", "✚", "services", "quality.health",
     "Lits fermés, urgences saturées, soignants épuisés qui démissionnent : chaque départ alourdit la charge des autres.",
     "Les urgences ferment la nuit dans plusieurs villes ; des malades attendent des heures sur des brancards.",
     "augmenter les crédits de santé, recruter ou mieux payer les soignants.",
     0.08, below=0.38, severe="Le système de santé craque",
     monthly=[g("seniors", -0.005), g("retirees", -0.004), g("rural", -0.003), g("low_income", -0.003), e("president.popularity", -0.003),
              e("economy.output", -0.0003), a("union_public", -0.05)],
     departments=[d("healthAccess", -0.01, "rural"), d("healthAccess", -0.005), d("approval", -0.002, "rural")],
     events={"hospital_overload": 0.8, "medical_desert": 0.6, "ehpad_scandal": 0.4, "doctor_strike": 0.5, "drug_shortage": 0.3, "hospital_maternity": 0.4})

rule("education_collapse", "L'école décroche", "✎", "services", "quality.education",
     "Classes sans professeur, remplaçants introuvables, élèves qui décrochent : le niveau baisse et, à terme, la croissance avec lui.",
     "Des milliers d'heures de cours ne sont plus assurées ; les parents s'inquiètent.",
     "augmenter les crédits de l'éducation, recruter des enseignants, revaloriser leur salaire.",
     0.08, below=0.40, severe="Une génération sacrifiée",
     monthly=[g("adults", -0.004), g("young", -0.003), g("middle_income", -0.002), e("economy.potentialGrowth", -0.00005, cap=0.002),
              a("union_public", -0.05)],
     departments=[d("approval", -0.001, "poor")],
     events={"teacher_shortage": 0.8, "teachers_strike": 0.5, "local_violence_school": 0.4, "student_protest": 0.3},
     unrest={"students": 0.03, "public_services": 0.02})

rule("justice_collapse", "La justice ne suit plus", "⚖", "services", "quality.justice",
     "Tribunaux engorgés, prisons surpeuplées : les procès traînent des années, les peines ne sont plus exécutées, les entreprises n'osent plus se fier aux contrats.",
     "Des affaires sont classées faute de moyens ; des détenus sont libérés faute de place.",
     "renforcer les crédits de la justice, construire des places de prison, recruter des magistrats.",
     0.07, below=0.32, severe="Le sentiment d'impunité s'installe",
     monthly=[g("seniors", -0.003), g("middle_income", -0.002), e("economy.businessConfidence", -0.002), e("quality.security", -0.002)],
     departments=[d("crime", 0.008)],
     events={"court_backlog": 0.8, "prison_overcrowding": 0.6, "prison_escape": 0.5})

rule("transport_collapse", "Routes et trains à l'abandon", "⇄", "services", "quality.transport",
     "Sans entretien, les ponts se fissurent, les lignes ferment, les trains sont en retard : les campagnes s'isolent et l'économie ralentit.",
     "Des ponts sont fermés à la circulation, des petites lignes de train supprimées.",
     "remonter les crédits des transports et l'entretien des infrastructures.",
     0.08, below=0.42, severe="Le pays se fragmente",
     monthly=[g("rural", -0.004), g("private_employees", -0.002), e("economy.output", -0.0003), e("sector.transport", -0.002), e("sector.tourism", -0.001)],
     departments=[d("approval", -0.003, "rural"), d("unemployment", 0.0002, "rural")],
     events={"bridge_repair": 0.7, "rail_line": 0.5, "rail_strike": 0.3, "public_transport_strike_local": 0.3})

rule("environment_collapse", "L'environnement se dégrade", "❀", "services", "quality.environment",
     "Moins de contrôles et d'investissements : pollution de l'air et des rivières, sécheresses mal gérées, forêts non entretenues.",
     "Pics de pollution à répétition ; Bruxelles rappelle la France à l'ordre.",
     "remonter les crédits de l'écologie, la taxe carbone ou les normes.",
     0.08, below=0.36, severe="La France condamnée pour inaction climatique",
     monthly=[g("young", -0.004), g("urban", -0.002), e("alliance.EU.DISAGREEMENT", -0.01), a("ngo_environment", -0.08), e("quality.health", -0.001)],
     departments=[d("pollution", 0.01, "urban"), d("healthAccess", -0.002, "urban")],
     events={"wildfire": 0.4, "drought": 0.3, "water_crisis": 0.4, "heatwave": 0.2},
     unrest={"ecology": 0.04})

rule("agriculture_collapse", "Les fermes ferment", "❦", "services", "quality.agriculture",
     "Sans soutien, les exploitations les plus fragiles disparaissent ; la France importe davantage et les campagnes se vident.",
     "Des centaines d'exploitations font faillite ; la colère monte dans les campagnes.",
     "rétablir les aides agricoles, les prix minimums ou les protections commerciales.",
     0.08, below=0.36, severe="La souveraineté alimentaire menacée",
     monthly=[g("rural", -0.005), g("self_employed", -0.002), a("farmers_union", -0.1), e("sector.agrifood", -0.003)],
     departments=[d("approval", -0.003, "rural"), d("unemployment", 0.0003, "rural")],
     events={"farmers_protest": 0.7, "farmers_local": 0.6, "wine_crisis": 0.3},
     unrest={"farmers": 0.06})

rule("defense_collapse", "Une armée échantillonnaire", "⚔", "services", "quality.defense",
     "Matériel non entretenu, munitions qui manquent, militaires qui partent : l'armée ne peut plus tenir ses engagements, les alliés doutent.",
     "Des avions restent cloués au sol faute de pièces ; l'OTAN s'inquiète.",
     "remonter le budget des armées, acheter du matériel, reconstituer les stocks.",
     0.07, below=0.45, severe="La France n'a plus les moyens de se défendre",
     monthly=[e("military.readiness", -0.004), e("alliance.NATO.DISAGREEMENT", -0.01), e("unrest.armyLoyalty", -0.004), a("defense_lobby", -0.08), g("seniors", -0.002)],
     events={"military_accident": 0.5, "defense_leak": 0.3, "hybrid_sabotage": 0.3})

rule("social_collapse", "La pauvreté gagne", "◐", "services", "quality.social",
     "Aides réduites, accompagnement absent : les plus fragiles basculent dans la pauvreté, les quartiers et les campagnes pauvres décrochent.",
     "Les associations caritatives sont débordées ; les files d'attente s'allongent aux distributions alimentaires.",
     "remonter les crédits de solidarité, les minima sociaux et les aides au logement.",
     0.08, below=0.45, severe="Explosion de la grande pauvreté",
     monthly=[g("low_income", -0.006), g("inactive", -0.004), e("economy.consumerConfidence", -0.002), a("church", -0.05), a("ngo_rights", -0.05), e("quality.health", -0.001)],
     departments=[d("crime", 0.008, "poor"), d("approval", -0.003, "poor")],
     events={"urban_riots": 0.4, "housing_shortage": 0.4, "child_protection": 0.3},
     unrest={"cost_of_living": 0.05, "anger": 0.02})

# ---------------------------------------------------------------------------------------------
# Coupes budgétaires : réaction immédiate, avant même que les services ne se dégradent
# ---------------------------------------------------------------------------------------------
CUTS = [
    # poste, libellé, icône, seuil, échelle, effets, départements, événements, contestation, pourquoi, annonce, sortie
    ("police", "Commissariats en sous-effectif", "⚠", 0.85, 0.15,
     [g("seniors", -0.003), g("urban", -0.002), e("quality.security", -0.004), e("unrest.armyLoyalty", -0.001)],
     [d("crime", 0.01, "crime"), d("crime", 0.006)], {"police_incident": 0.5, "urban_riots": 0.3, "prison_escape": 0.2}, {"police": 0.08},
     "Des commissariats ferment la nuit, les patrouilles se raréfient ; les syndicats de police dénoncent l'abandon.",
     "Les policiers manifestent devant les préfectures ; des commissariats réduisent leurs horaires.",
     "rétablir les crédits de la police."),
    ("health", "L'hôpital en grève", "✚", 0.9, 0.1,
     [g("seniors", -0.004), g("retirees", -0.003), e("quality.health", -0.004), a("union_public", -0.08), a("pharma_lobby", -0.05)],
     [d("healthAccess", -0.006, "rural")], {"doctor_strike": 0.6, "hospital_overload": 0.5, "drug_shortage": 0.3}, {"public_services": 0.06},
     "Moins d'argent pour l'hôpital : postes gelés, lits fermés, soignants en colère.",
     "Grève dans les hôpitaux ; les urgences ne fonctionnent plus qu'en service minimum.",
     "rétablir les crédits de la santé."),
    ("education", "Profs dans la rue", "✎", 0.9, 0.1,
     [g("adults", -0.004), g("civil_servants", -0.004), e("quality.education", -0.004), a("union_public", -0.1)],
     [], {"teachers_strike": 0.7, "teacher_shortage": 0.5, "student_protest": 0.3}, {"public_services": 0.06, "students": 0.03},
     "Classes fermées, postes supprimés : enseignants et parents se mobilisent ensemble.",
     "Grève massive dans l'Éducation nationale ; des écoles occupées par les parents.",
     "rétablir les crédits de l'éducation."),
    ("justice", "Tribunaux paralysés", "⚖", 0.85, 0.15,
     [g("seniors", -0.002), e("quality.justice", -0.004), e("economy.businessConfidence", -0.001)],
     [d("crime", 0.004)], {"court_backlog": 0.7, "prison_overcrowding": 0.5}, None,
     "Greffiers et magistrats en sous-nombre : les audiences sont renvoyées, les prisons débordent.",
     "Les avocats et les magistrats protestent ; des audiences sont annulées faute de greffiers.",
     "rétablir les crédits de la justice."),
    ("defense", "Colère dans les casernes", "⚔", 0.85, 0.15,
     [e("military.readiness", -0.004), e("unrest.armyLoyalty", -0.006), e("alliance.NATO.DISAGREEMENT", -0.015), a("defense_lobby", -0.12), g("seniors", -0.002)],
     [d("unemployment", 0.0002, "rural")], {"military_accident": 0.3, "defense_leak": 0.2}, None,
     "Programmes d'armement annulés, régiments dissous : les militaires se sentent trahis, les industriels licencient, les alliés s'inquiètent.",
     "Des généraux expriment publiquement leur inquiétude ; l'OTAN demande des explications.",
     "rétablir le budget des armées."),
    ("pensions", "Les retraités appauvris", "◷", 0.95, 0.05,
     [g("retirees", -0.008), g("seniors", -0.006), e("economy.consumerConfidence", -0.002), e("president.popularity", -0.003)],
     [d("approval", -0.003, "rural")], {"pension_deficit": 0.2}, {"pensions": 0.1},
     "Des pensions qui baissent, c'est une promesse rompue : les retraités votent, et ils votent beaucoup.",
     "Les retraités descendent dans la rue ; les associations crient à la trahison.",
     "rétablir les pensions."),
    ("unemployment_benefits", "Chômeurs sans filet", "◐", 0.8, 0.15,
     [g("low_income", -0.004), g("young", -0.002), e("quality.social", -0.003), a("union_militant", -0.1), a("union_reformist", -0.06)],
     [d("crime", 0.005, "unemployed"), d("approval", -0.002, "unemployed")], {"urban_riots": 0.2}, {"labour": 0.06},
     "Moins d'indemnités : certains reprennent un emploi plus vite, d'autres basculent dans la pauvreté.",
     "Les syndicats dénoncent une « chasse aux chômeurs ».",
     "rétablir l'assurance chômage."),
    ("local_authorities", "Les maires étranglés", "⌂", 0.85, 0.15,
     [g("rural", -0.004), e("quality.transport", -0.002), e("quality.social", -0.002), e("sector.construction", -0.003)],
     [d("approval", -0.003, "rural"), d("approval", -0.001)], {"post_office_closure": 0.5, "school_renovation": 0.3, "bridge_repair": 0.3}, {"public_services": 0.04},
     "Moins de dotations : les communes ferment des écoles, des piscines, repoussent les travaux ; les entreprises du bâtiment perdent des chantiers.",
     "Les maires menacent de rendre leur écharpe ; les chantiers communaux s'arrêtent.",
     "rétablir les dotations aux collectivités."),
    ("state_operations", "L'administration à l'arrêt", "⚙", 0.8, 0.15,
     [g("civil_servants", -0.005), e("economy.businessConfidence", -0.002), e("quality.justice", -0.002), a("union_public", -0.1)],
     [], {"national_strike": 0.2, "court_backlog": 0.2}, {"public_services": 0.05},
     "Moins d'agents dans les préfectures et les services fiscaux : titres de séjour, permis, contrôles fiscaux, tout prend des mois ; la fraude progresse.",
     "Files d'attente dans les préfectures ; les entreprises attendent leurs autorisations.",
     "rétablir les moyens de l'administration."),
    ("agriculture", "Les paysans abandonnés", "❦", 0.8, 0.15,
     [g("rural", -0.004), a("farmers_union", -0.15), e("quality.agriculture", -0.004), e("sector.agrifood", -0.002)],
     [d("approval", -0.003, "rural")], {"farmers_protest": 0.7}, {"farmers": 0.1},
     "Les aides agricoles représentent souvent tout le revenu d'une exploitation : les couper, c'est condamner les plus petites.",
     "Les tracteurs bloquent les préfectures.",
     "rétablir les aides agricoles."),
    ("ecology", "La transition à l'arrêt", "❀", 0.75, 0.15,
     [g("young", -0.004), e("quality.environment", -0.004), e("alliance.EU.DISAGREEMENT", -0.01), a("ngo_environment", -0.15)],
     [d("pollution", 0.004, "urban")], {"drought": 0.1}, {"ecology": 0.06},
     "Rénovation des logements, transports propres, protection de la nature : tout ralentit, et la France manquera ses objectifs européens.",
     "Les ONG et la jeunesse dénoncent un renoncement climatique.",
     "rétablir les crédits de l'écologie."),
    ("transport", "Petites lignes fermées", "⇄", 0.85, 0.15,
     [g("rural", -0.004), e("quality.transport", -0.004), e("sector.transport", -0.002)],
     [d("approval", -0.003, "rural")], {"rail_strike": 0.4, "bridge_repair": 0.3}, {"public_services": 0.03},
     "Moins d'investissement : trains supprimés, routes non entretenues, les campagnes se sentent abandonnées.",
     "Les cheminots se mettent en grève ; les élus ruraux protestent.",
     "rétablir les crédits des transports."),
    ("solidarity", "Minima sociaux rognés", "◐", 0.85, 0.15,
     [g("low_income", -0.006), g("inactive", -0.005), e("quality.social", -0.004), a("church", -0.08), a("ngo_rights", -0.1)],
     [d("crime", 0.006, "poor"), d("approval", -0.003, "poor")], {"urban_riots": 0.3, "child_protection": 0.2}, {"cost_of_living": 0.06},
     "Les plus pauvres n'ont aucune réserve : chaque euro en moins se voit dans l'assiette, le loyer impayé, l'enfant mal soigné.",
     "Les associations caritatives tirent la sonnette d'alarme.",
     "rétablir les crédits de solidarité."),
]
for item, label, icon, threshold, scale, monthly, depts, events, unrest, why, onset, fix in CUTS:
    rule(f"cut_{item}", label, icon, "budget", f"spending.{item}", why, onset, fix, scale, below=threshold,
         severe=f"{label} : la situation devient intenable", monthly=monthly, departments=depts, events=events, unrest=unrest)

rule("state_bloat", "Dépense publique hors de contrôle", "€", "budget", "derived.servicesFunding",
     "Des crédits qui gonflent bien plus vite que les besoins : gaspillages, hausse des prix, la Cour des comptes et Bruxelles s'alarment.",
     "La Cour des comptes publie un rapport cinglant sur les dérives de la dépense.",
     "ramener les crédits vers des niveaux soutenables ou financer par l'impôt.",
     0.3, above=1.4,
     monthly=[e("economy.inflation", 0.0005, cap=0.01), e("alliance.EU.DISAGREEMENT", -0.01), g("high_income", -0.002), e("economy.businessConfidence", -0.001)],
     events={"rating_downgrade": 0.3})

# ---------------------------------------------------------------------------------------------
# Réglages sociaux poussés trop loin
# ---------------------------------------------------------------------------------------------
rule("work_does_not_pay", "Le travail ne paie plus", "⚖", "social", "derived.rsaToSmic",
     "Quand le RSA approche le SMIC, reprendre un emploi ne rapporte presque rien : une partie des bénéficiaires reste chez elle, les salariés modestes se sentent floués, les employeurs ne trouvent plus personne.",
     "Les petites entreprises peinent à recruter ; « pourquoi travailler ? » fait la une.",
     "ramener le RSA nettement sous le SMIC, ou augmenter le SMIC et la prime d'activité.",
     0.2, above=0.8, severe="Pénurie de main-d'œuvre et colère des travailleurs modestes",
     monthly=[e("economy.unemployment", 0.0006, cap=0.012), e("economy.naturalUnemployment", 0.0003, cap=0.008),
              g("private_employees", -0.004), g("middle_income", -0.003), g("self_employed", -0.004), g("low_income", 0.002),
              a("employers_small", -0.15), a("employers_big", -0.06), e("economy.businessConfidence", -0.002),
              e("sector.retail", -0.002), e("sector.construction", -0.002), e("sector.tourism", -0.002), e("demography.immigration", 0.01, cap=0.3)],
     departments=[d("unemployment", 0.0004, "poor")],
     events={"delivery_strike": 0.2})

rule("extreme_poverty", "Grande pauvreté", "◐", "social", "derived.rsaToSmic",
     "Sans revenu minimum décent, les plus fragiles basculent : expulsions, sans-abri, petite délinquance de survie, santé qui se dégrade.",
     "Les maraudes signalent une hausse brutale du nombre de sans-abri.",
     "relever le RSA au-dessus du seuil de survie.",
     0.1, below=0.3, severe="Des campements de sans-abri dans toutes les grandes villes",
     monthly=[g("low_income", -0.008), g("inactive", -0.008), g("urban", -0.002), e("quality.health", -0.002), e("quality.social", -0.004),
              e("economy.consumerConfidence", -0.002), a("church", -0.15), a("ngo_rights", -0.2)],
     departments=[d("crime", 0.012, "poor"), d("approval", -0.004, "poor"), d("healthAccess", -0.004, "poor")],
     events={"urban_riots": 0.6, "housing_shortage": 0.5, "child_protection": 0.4},
     unrest={"cost_of_living": 0.08, "anger": 0.04})

rule("smic_too_high", "Un SMIC trop cher pour les petites entreprises", "€", "social", "lever.param:smic_boost",
     "Une hausse du SMIC bien plus rapide que la productivité : les petits commerces et l'artisanat embauchent moins, le travail au noir progresse, les prix montent.",
     "Les artisans et commerçants annoncent des gels d'embauche.",
     "modérer la hausse du SMIC ou alléger les cotisations sur les bas salaires.",
     8.0, above=12.0,
     monthly=[e("economy.unemployment", 0.0005, cap=0.01), e("economy.inflation", 0.0004, cap=0.008), g("self_employed", -0.004), a("employers_small", -0.15),
              e("sector.retail", -0.002), e("sector.tourism", -0.002)],
     departments=[d("unemployment", 0.0003, "poor")],
     events={"bankruptcy": 0.4})

rule("late_retirement", "Travailler jusqu'à l'épuisement", "◷", "social", "lever.param:pension_age",
     "Un âge de départ très tardif : les métiers pénibles ne tiennent pas, les seniors au chômage attendent des années une retraite qui recule.",
     "Les syndicats dénoncent les « morts avant la retraite ».",
     "rabaisser l'âge légal ou prévoir des départs anticipés pour les métiers pénibles.",
     1.0, above=66.0,
     monthly=[g("seniors", -0.006), g("private_employees", -0.004), e("economy.unemployment", 0.0002, cap=0.004), a("union_militant", -0.1), a("union_reformist", -0.08)],
     unrest={"pensions": 0.08})

rule("early_retirement", "Le trou des retraites", "◷", "social", "lever.param:pension_age",
     "Partir tôt coûte cher : moins de cotisants, plus de pensionnés, et Bruxelles s'inquiète de la soutenabilité du système.",
     "Le Conseil d'orientation des retraites annonce un déficit qui se creuse.",
     "relever l'âge, la durée de cotisation, ou augmenter les cotisations.",
     1.0, below=62.0,
     monthly=[e("alliance.EU.DISAGREEMENT", -0.008), e("economy.businessConfidence", -0.001), g("young", -0.002)],
     events={"pension_deficit": 0.6, "rating_downgrade": 0.2})

rule("pension_freeze", "Pensions gelées", "◷", "social", "lever.param:pension_indexation",
     "Des pensions qui ne suivent plus les prix : chaque année, les retraités perdent du pouvoir d'achat.",
     "Les retraités se sentent trahis.",
     "réindexer les pensions sur l'inflation.",
     5.0, below=-3.0,
     monthly=[g("retirees", -0.006), g("seniors", -0.004), e("economy.consumerConfidence", -0.001)],
     unrest={"pensions": 0.06})

rule("housing_crisis", "Crise du logement", "⌂", "social", "lever.param:housing_aid",
     "Sans aides au logement, les loyers deviennent inaccessibles aux étudiants et aux familles modestes : impayés, expulsions, colocations forcées.",
     "Les expulsions locatives atteignent un record.",
     "rétablir les aides au logement.",
     20.0, below=60.0,
     monthly=[g("young", -0.005), g("low_income", -0.005), e("quality.social", -0.002), e("sector.construction", -0.001)],
     departments=[d("approval", -0.002, "urban"), d("crime", 0.003, "poor")],
     events={"housing_shortage": 0.6},
     unrest={"students": 0.04, "cost_of_living": 0.04})

rule("family_squeeze", "Les familles se serrent la ceinture", "◐", "social", "lever.param:family_allowance",
     "Moins d'allocations familiales : les familles nombreuses et modestes perdent beaucoup, la natalité recule.",
     "Les associations familiales dénoncent une politique « anti-familles ».",
     "rétablir les allocations familiales.",
     20.0, below=60.0,
     monthly=[g("adults", -0.004), g("low_income", -0.003), a("church", -0.08), e("economy.consumerConfidence", -0.001)])

rule("civil_service_squeeze", "Fonctionnaires déclassés", "⚙", "social", "lever.param:civil_service_index",
     "Un point d'indice qui recule : les fonctionnaires perdent du pouvoir d'achat, les vocations s'effondrent, les concours ne font plus le plein.",
     "Les syndicats de fonctionnaires appellent à la grève.",
     "revaloriser le point d'indice.",
     4.0, below=-3.0,
     monthly=[g("civil_servants", -0.006), a("union_public", -0.15), e("quality.education", -0.002), e("quality.health", -0.002), e("quality.security", -0.001)],
     events={"teachers_strike": 0.5, "national_strike": 0.3, "teacher_shortage": 0.4},
     unrest={"public_services": 0.08})

# ---------------------------------------------------------------------------------------------
# Impôts, finances publiques, économie
# ---------------------------------------------------------------------------------------------
rule("tax_exodus", "Ras-le-bol fiscal et exil", "€", "economy", "tax.households",
     "Au-delà d'un certain niveau, chaque hausse d'impôt rapporte de moins en moins : les plus aisés partent, d'autres travaillent moins ou fraudent, la classe moyenne gronde.",
     "Des contribuables aisés annoncent leur départ à l'étranger ; « trop d'impôt tue l'impôt » fait la une.",
     "baisser les prélèvements sur les ménages ou mieux cibler les hausses.",
     0.04, above=0.40, severe="Fuite des capitaux et révolte fiscale",
     monthly=[g("high_income", -0.006), g("middle_income", -0.005), g("self_employed", -0.004), e("budget.oneOff", 0.3),
              e("economy.potentialGrowth", -0.00004, cap=0.0015), e("economy.consumerConfidence", -0.002), e("sector.luxury", -0.002), e("sector.banking", -0.002)],
     events={"tax_leak": 0.3, "fuel_tax_protest": 0.4},
     unrest={"cost_of_living": 0.05, "anger": 0.03})

rule("business_flight", "Les entreprises s'en vont", "⚙", "economy", "tax.businesses",
     "Des prélèvements sur les entreprises bien au-dessus des voisins : les usines s'installent ailleurs, les sièges sociaux déménagent, l'emploi suit.",
     "Un grand groupe annonce le transfert de son siège à l'étranger.",
     "alléger l'impôt sur les sociétés ou les cotisations patronales.",
     0.03, above=0.20, severe="Vague de délocalisations",
     monthly=[e("economy.businessConfidence", -0.004), e("economy.unemployment", 0.0004, cap=0.01), e("economy.potentialGrowth", -0.00004, cap=0.0015),
              e("sector.industry", -0.003), e("sector.tech", -0.003), a("employers_big", -0.15), a("employers_small", -0.08), e("budget.oneOff", 0.2)],
     departments=[d("unemployment", 0.0004, "all"), d("industry", -0.001)],
     events={"factory_closure": 0.6, "strategic_acquisition": 0.3, "ai_exodus": 0.3})

rule("excessive_deficit", "Bruxelles hausse le ton", "€", "economy", "economy.deficitRatio",
     "Un déficit au-dessus de 3 % viole les règles européennes ; au-delà de 6 %, la Commission ouvre une procédure et les marchés exigent des taux plus élevés.",
     "La Commission européenne menace la France d'une procédure pour déficit excessif.",
     "réduire le déficit : économies, hausses d'impôts ciblées ou croissance.",
     0.02, above=0.06, severe="Procédure pour déficit excessif",
     monthly=[e("alliance.EU.DISAGREEMENT", -0.012), e("economy.businessConfidence", -0.002), e("budget.oneOff", 0.4)],
     events={"eu_deficit_procedure": 0.8, "rating_downgrade": 0.5})

rule("debt_crisis", "Crise de la dette", "€", "economy", "economy.debtRatio",
     "Une dette qui dépasse largement 130 % du PIB : les prêteurs doutent, les taux d'intérêt montent, la charge de la dette dévore le budget.",
     "Les taux d'emprunt de la France s'envolent ; les agences de notation menacent.",
     "dégager un excédent primaire, rassurer les marchés, demander l'aide européenne en dernier recours.",
     0.15, above=1.30, severe="La France au bord du défaut",
     monthly=[e("budget.oneOff", 1.2), e("economy.consumerConfidence", -0.003), e("economy.businessConfidence", -0.004), e("sector.banking", -0.004),
              e("alliance.EU.DISAGREEMENT", -0.01), e("president.popularity", -0.002)],
     events={"rating_downgrade": 0.8, "bank_fragility": 0.6, "bank_run": 0.3})

rule("high_inflation", "Les prix flambent", "▲", "economy", "economy.inflation",
     "Une inflation forte ronge les salaires et l'épargne : les plus modestes et les retraités trinquent, la colère monte.",
     "Les prix de l'alimentation et de l'énergie s'envolent.",
     "freiner la dépense, plafonner certains prix, laisser la BCE remonter ses taux.",
     0.03, above=0.045, severe="Spirale inflationniste",
     monthly=[g("low_income", -0.006), g("retirees", -0.005), g("middle_income", -0.003), e("economy.consumerConfidence", -0.004), e("president.popularity", -0.003)],
     events={"fuel_tax_protest": 0.5, "energy_bills_local": 0.4},
     unrest={"cost_of_living": 0.08})

rule("deflation", "Les prix baissent", "▼", "economy", "economy.inflation",
     "Quand les prix baissent, on attend pour acheter, les entreprises réduisent leurs marges et l'investissement, et la dette pèse plus lourd.",
     "Les commerces bradent sans parvenir à relancer les ventes.",
     "relancer la demande (dépense, baisse d'impôts ciblée).",
     0.01, below=-0.005,
     monthly=[e("economy.businessConfidence", -0.003), e("economy.output", -0.0004), e("sector.retail", -0.002)])

rule("mass_unemployment", "Chômage de masse", "◐", "economy", "economy.unemployment",
     "Un chômage durablement élevé appauvrit les familles, nourrit la délinquance dans les zones sinistrées, coûte en indemnités et en recettes perdues.",
     "Le chômage dépasse 10 % ; des bassins d'emploi entiers sont sinistrés.",
     "relancer l'activité, former les chômeurs, baisser le coût du travail.",
     0.03, above=0.10, severe="Le pays s'enfonce dans le chômage",
     monthly=[g("young", -0.005), g("low_income", -0.005), g("private_employees", -0.003), e("president.popularity", -0.004), e("budget.oneOff", 0.3),
              e("economy.consumerConfidence", -0.003)],
     departments=[d("crime", 0.006, "unemployed"), d("approval", -0.003, "unemployed")],
     events={"urban_riots": 0.4, "factory_closure": 0.3, "youth_movement": 0.3},
     unrest={"labour": 0.05, "anger": 0.03})

rule("recession", "Récession", "▼", "economy", "economy.growth",
     "Quand l'activité recule, les faillites se multiplient, l'emploi suit, les recettes fiscales baissent et le déficit se creuse.",
     "Le PIB recule ; les faillites d'entreprises augmentent.",
     "soutenir l'activité (investissement public, crédit, baisse de charges ciblée).",
     0.015, below=-0.015, severe="Dépression économique",
     monthly=[e("economy.businessConfidence", -0.004), e("economy.consumerConfidence", -0.003), e("economy.unemployment", 0.0006, cap=0.015),
              e("sector.construction", -0.003), e("sector.industry", -0.002), e("president.popularity", -0.003)],
     departments=[d("unemployment", 0.0004, "unemployed")],
     events={"bankruptcy": 0.6, "factory_closure": 0.5})

rule("energy_shock", "Factures d'énergie insupportables", "⚡", "economy", "energy.priceIndex",
     "Une énergie très chère étrangle les ménages modestes et les industries gourmandes en énergie, qui ralentissent ou ferment.",
     "Les factures d'électricité et de gaz explosent ; des usines s'arrêtent.",
     "bouclier tarifaire, sobriété, production nationale (nucléaire, renouvelables).",
     0.3, above=1.4, severe="Usines à l'arrêt, ménages dans le froid",
     monthly=[g("low_income", -0.005), g("rural", -0.004), e("sector.industry", -0.004), e("economy.inflation", 0.0005, cap=0.01), e("economy.consumerConfidence", -0.003)],
     departments=[d("industry", -0.001), d("approval", -0.002, "rural")],
     events={"energy_bills_local": 0.6, "fuel_tax_protest": 0.4},
     unrest={"cost_of_living": 0.06})

# ---------------------------------------------------------------------------------------------
# Libertés, institutions, rapport au pouvoir
# ---------------------------------------------------------------------------------------------
rule("authoritarian_drift", "Dérive autoritaire", "⊘", "institutions", "laws.liberty",
     "Des libertés qui reculent fortement : la jeunesse et les villes se mobilisent, les partenaires européens condamnent, les ONG saisissent les tribunaux.",
     "Les ONG et l'opposition dénoncent une « dérive autoritaire ».",
     "rétablir les libertés publiques, abroger les lois les plus restrictives.",
     10.0, below=60.0, severe="La France pointée du doigt par l'Europe",
     monthly=[g("young", -0.005), g("urban", -0.003), a("ngo_rights", -0.2), e("alliance.EU.CONDEMNATION", -0.015), e("economy.businessConfidence", -0.001)],
     events={"youth_movement": 0.5, "student_protest": 0.4, "campus_occupation": 0.3},
     unrest={"society": 0.06, "students": 0.04})

rule("press_muzzled", "Presse muselée", "⊘", "institutions", "laws.press",
     "Quand la presse n'est plus libre, les scandales éclatent plus tard et plus fort, la confiance s'effondre, l'étranger s'inquiète.",
     "Reporters sans frontières fait chuter la France dans son classement.",
     "rétablir la liberté de la presse.",
     10.0, below=55.0,
     monthly=[g("urban", -0.003), g("high_income", -0.002), a("ngo_rights", -0.15), e("alliance.EU.DISAGREEMENT", -0.01)],
     events={"president_scandal": 0.3, "deepfake_president": 0.2})

rule("full_powers", "Pleins pouvoirs (article 16)", "⚠", "institutions", "derived.article16",
     "Gouverner sans Parlement est légal en cas de péril grave, mais chaque jour passé sous l'article 16 inquiète : l'opposition parle de coup d'État, l'Europe s'alarme.",
     "Le président gouverne seul ; l'opposition saisit le Conseil constitutionnel pour faire constater la fin du péril.",
     "mettre fin à la crise pour que l'article 16 s'achève.",
     1.0, above=0.5, max_severity=1.0,
     monthly=[e("president.popularity", -0.01), e("alliance.EU.DISAGREEMENT", -0.02), g("young", -0.006), g("urban", -0.004), a("ngo_rights", -0.3),
              e("government.parliamentSupport", -0.01)],
     unrest={"society": 0.3})

rule("no_majority", "Majorité introuvable", "⚖", "institutions", "government.parliamentSupport",
     "Sans majorité solide, chaque texte devient une bataille, les frondeurs se multiplient et le gouvernement vit sous la menace d'une censure.",
     "Des députés de la majorité votent contre le gouvernement.",
     "négocier avec les groupes, remanier, dissoudre (risqué).",
     0.08, below=0.40,
     monthly=[e("economy.businessConfidence", -0.001), e("president.popularity", -0.002)],
     events={"majority_rebels": 0.8, "group_split": 0.5})

rule("mass_rejection", "Rejet massif du président", "⚑", "institutions", "opinion.national",
     "Quand trois Français sur quatre rejettent le président, la moindre étincelle peut embraser le pays ; la majorité se fissure, l'armée observe.",
     "Les sondages s'effondrent ; on parle de « crise de régime ».",
     "remanier, céder sur une réforme contestée, parler au pays.",
     0.08, below=0.25, severe="Crise de régime",
     monthly=[e("government.parliamentSupport", -0.006), e("unrest.armyLoyalty", -0.003)],
     events={"majority_rebels": 0.5, "group_split": 0.3},
     unrest={"anger": 0.08})

rule("war_weariness", "Lassitude de la guerre", "⚔", "institutions", "military.warWeariness",
     "Une guerre qui dure et qui tue use la patience du pays : on réclame la paix, les mères manifestent.",
     "Les cortèges pour la paix grossissent.",
     "négocier la paix ou obtenir une victoire rapide.",
     0.25, above=0.5,
     monthly=[e("president.popularity", -0.006), g("young", -0.004), g("adults", -0.003)],
     unrest={"war": 0.1})

rule("migration_pressure", "Tensions migratoires", "⇄", "institutions", "demography.immigration",
     "Des arrivées bien plus nombreuses que les capacités d'accueil : centres saturés, campements, tensions locales exploitées politiquement.",
     "Les centres d'accueil sont saturés ; des campements apparaissent.",
     "renforcer l'accueil et l'intégration, ou durcir les règles d'entrée.",
     0.3, above=1.4,
     monthly=[g("seniors", -0.004), g("rural", -0.003), e("quality.social", -0.002), e("sector.construction", 0.001), e("economy.potentialGrowth", 0.00002, cap=0.0008)],
     departments=[d("approval", -0.002, "poor")],
     events={"migrant_reception": 0.6, "refugee_crisis": 0.3},
     unrest={"immigration": 0.06})

rule("labour_shortage", "Pénurie de bras", "⇄", "institutions", "demography.immigration",
     "Fermer la porte à l'immigration de travail : le bâtiment, l'hôtellerie, l'agriculture et les hôpitaux ne trouvent plus de personnel.",
     "Les restaurateurs et les agriculteurs ne trouvent plus de saisonniers.",
     "rouvrir des voies d'immigration de travail ciblées.",
     0.2, below=0.6,
     monthly=[e("sector.construction", -0.003), e("sector.tourism", -0.003), e("sector.agrifood", -0.002), e("quality.health", -0.002),
              e("economy.potentialGrowth", -0.00003, cap=0.001), a("employers_small", -0.1), a("farmers_union", -0.06)])

rule("intel_blind", "Services de renseignement aveugles", "◉", "security", "intel.capacity",
     "Moins d'agents et de moyens techniques : les complots ne sont plus détectés, les ingérences étrangères prospèrent.",
     "Un rapport parlementaire alerte sur l'affaiblissement du renseignement.",
     "renforcer les services de renseignement.",
     0.1, below=0.35, severe="Le pays à découvert face aux menaces",
     monthly=[e("quality.security", -0.002)],
     events={"terror_attack": 0.6, "cyberattack": 0.5, "foreign_interference": 0.5, "cyber_espionage": 0.4, "hybrid_sabotage": 0.4})

rule("street_violence", "Le pays s'embrase", "⚠", "security", "unrest.phase",
     "Des émeutes qui durent font fuir touristes et investisseurs, ferment les commerces et usent les forces de l'ordre.",
     "Les images des émeutes font le tour du monde.",
     "apaiser : dialogue, concession, ou rétablir l'ordre sans excès.",
     1.0, above=2.5, max_severity=2.0,
     monthly=[e("sector.tourism", -0.006), e("sector.retail", -0.003), e("economy.businessConfidence", -0.003), e("quality.security", -0.003), e("budget.oneOff", 0.3)],
     departments=[d("approval", -0.002, "urban")])

rule("army_distrust", "L'armée prend ses distances", "⚔", "security", "unrest.armyLoyalty",
     "Une armée qui doute de son chef obéit moins vite ; les rumeurs de complot circulent.",
     "Des tribunes de militaires critiquent le pouvoir.",
     "rétablir la confiance : budget des armées, respect de l'institution, reconnaissance.",
     0.15, below=0.5,
     monthly=[e("military.readiness", -0.003), e("president.popularity", -0.002)])



# ---------------------------------------------------------------------------------------------
# Vie quotidienne : logement, natalité, santé, épargne, travail au noir
# ---------------------------------------------------------------------------------------------
rule("rents_unaffordable", "Loyers inabordables", "⌂", "society", "society.realRent",
     "Les loyers montent plus vite que les revenus : les jeunes ne quittent plus le foyer familial, les familles modestes s'éloignent des villes, les étudiants renoncent.",
     "Les loyers battent des records dans les grandes villes.",
     "construire davantage, plafonner les loyers dans les zones tendues, cibler les aides.",
     0.08, above=1.12, severe="Crise du logement",
     monthly=[g("young", -0.005), g("low_income", -0.004), g("urban", -0.003), g("middle_income", -0.002), e("economy.consumerConfidence", -0.002)],
     departments=[d("approval", -0.002, "urban")],
     events={"housing_shortage": 0.7},
     unrest={"students": 0.04, "cost_of_living": 0.04})

rule("housing_crash", "Krach immobilier", "▼", "society", "society.housePrices",
     "Quand les prix des logements chutent, les ménages endettés se sentent plus pauvres, les chantiers s'arrêtent, les banques s'inquiètent.",
     "Les ventes de logements s'effondrent ; les promoteurs suspendent leurs chantiers.",
     "baisser le coût du crédit, soutenir la construction, rassurer les banques.",
     0.1, below=0.85,
     monthly=[e("sector.construction", -0.005), e("sector.banking", -0.003), e("economy.consumerConfidence", -0.003), g("seniors", -0.002), g("middle_income", -0.002)],
     events={"bank_fragility": 0.4})

rule("demographic_winter", "Hiver démographique", "◐", "society", "society.fertility",
     "Moins de naissances aujourd'hui, ce sont moins d'actifs demain pour financer les retraites et faire tourner l'économie ; les écoles ferment dans les campagnes.",
     "La natalité tombe à son plus bas niveau depuis la guerre.",
     "soutenir les familles : allocations, crèches, logement abordable.",
     0.1, below=1.45,
     monthly=[e("economy.potentialGrowth", -0.00003, cap=0.001), g("seniors", -0.001), g("rural", -0.001), a("church", -0.05)],
     departments=[d("approval", -0.001, "rural")])

rule("life_expectancy_falls", "L'espérance de vie recule", "✚", "society", "society.lifeExpectancy",
     "Hôpital dégradé, pauvreté, pollution : pour la première fois depuis des décennies, les Français vivent moins longtemps.",
     "L'INSEE annonce un recul de l'espérance de vie.",
     "remettre de l'argent dans la santé et la solidarité, lutter contre la pollution.",
     0.5, below=82.0,
     monthly=[e("president.popularity", -0.004), g("seniors", -0.004), g("retirees", -0.004), g("low_income", -0.002)])

rule("savings_glut", "Les Français thésaurisent", "€", "society", "society.savingsRate",
     "Inquiets, les ménages mettent de côté au lieu de consommer : les commerces, les restaurants et le bâtiment voient leur chiffre d'affaires baisser.",
     "Le livret A déborde, les commerces se vident.",
     "redonner confiance : visibilité fiscale, emploi, fin des crises.",
     0.02, above=0.20,
     monthly=[e("sector.retail", -0.003), e("sector.tourism", -0.002), e("sector.construction", -0.002), e("economy.output", -0.0003)])

rule("black_market", "Le travail au noir explose", "⊘", "society", "society.informal",
     "Charges trop lourdes, RSA proche du SMIC, contrôles affaiblis : de plus en plus d'activité échappe à l'impôt et aux cotisations, les entreprises honnêtes subissent une concurrence déloyale, les travailleurs ne sont plus protégés.",
     "L'Urssaf alerte sur une hausse massive du travail dissimulé.",
     "alléger les charges sur les bas salaires, renforcer les contrôles, rendre le travail déclaré plus intéressant.",
     0.02, above=0.13, severe="Une économie parallèle",
     monthly=[a("employers_small", -0.1), g("self_employed", -0.003), g("private_employees", -0.002), e("quality.social", -0.002), e("economy.businessConfidence", -0.001)],
     departments=[d("crime", 0.003, "poor")])

# ---------------------------------------------------------------------------------------------
# Contrôles
# ---------------------------------------------------------------------------------------------
def ids_of(path, key, inner="id"):
    data = json.load(open(os.path.join(ROOT, path)))
    return {x[inner] for x in data[key]}


def event_ids():
    out = set()
    for f in glob.glob(os.path.join(ROOT, "events", "**", "*.json"), recursive=True):
        data = json.load(open(f))
        evs = data.get("events", []) if isinstance(data, dict) else data if isinstance(data, list) else []
        out |= {x["id"] for x in evs if isinstance(x, dict) and "id" in x}
    return out


GROUPS = ids_of("countries/FRA/social_groups.json", "groups")
ACTORS = ids_of("countries/FRA/actors.json", "actors")
SECTORS = ids_of("countries/FRA/sectors.json", "sectors")
CAUSES = ids_of("config/unrest.json", "causes")
EVENTS = event_ids()
ECON = json.load(open(os.path.join(ROOT, "economy", "FRA_2026_10.json")))["budget"]
SPENDING = {x["id"] for x in ECON["spending"]}
QUALITY = {x["domain"] for x in ECON["spending"] if x.get("domain")}
PARAMS = {p["id"] for p in json.load(open(os.path.join(ROOT, "countries", "FRA", "legislation.json")))["parameters"]}
ECONOMY = {"output", "potentialGrowth", "naturalUnemployment", "consumerConfidence", "businessConfidence", "inflation", "unemployment"}
DEPT_FIELDS = {"approval", "unemployment", "industry", "crime", "healthAccess", "pollution"}
WEIGHTS = {"all", "urban", "rural", "poor", "unemployed", "crime"}
VARIABLES = {"derived.rsaToSmic", "derived.servicesFunding", "derived.article16", "economy.deficitRatio", "economy.debtRatio", "economy.inflation",
             "economy.unemployment", "economy.growth", "energy.priceIndex", "tax.households", "tax.businesses", "laws.liberty", "laws.press",
             "government.parliamentSupport", "opinion.national", "military.warWeariness", "demography.immigration", "intel.capacity",
             "unrest.phase", "unrest.armyLoyalty", "society.realRent", "society.housePrices", "society.fertility",
             "society.lifeExpectancy", "society.savingsRate", "society.informal"}


def check_target(t):
    p = t.split(".")
    if p[0] == "opinion": assert p[1] == "group" and p[2] in GROUPS, t
    elif p[0] == "actor": assert p[1] in ACTORS, t
    elif p[0] == "sector": assert p[1] in SECTORS, t
    elif p[0] == "quality": assert p[1] in QUALITY, t
    elif p[0] == "economy": assert p[1] in ECONOMY, t
    elif p[0] == "alliance": assert p[1] in {"EU", "NATO"} and p[2] in {"DISAGREEMENT", "CONDEMNATION"}, t
    elif p[0] == "unrest": assert p[1] == "armyLoyalty" or p[1] in CAUSES, t
    else: assert t in {"president.popularity", "budget.oneOff", "military.readiness", "government.parliamentSupport", "demography.immigration"}, t


seen = set()
for r in RULES:
    assert r["id"] not in seen, r["id"]; seen.add(r["id"])
    v = r["variable"]
    assert v in VARIABLES or v.startswith("quality.") and v[8:] in QUALITY or v.startswith("spending.") and v[9:] in SPENDING \
        or v.startswith("lever.param:") and v[12:] in PARAMS, v
    assert ("above" in r) != ("below" in r), r["id"]
    for fx in r.get("monthly", []): check_target(fx["target"])
    for fx in r.get("departments", []): assert fx["field"] in DEPT_FIELDS and fx["weight"] in WEIGHTS, (r["id"], fx)
    for ev in r.get("events", {}): assert ev in EVENTS, (r["id"], ev)
    for c in r.get("unrest", {}): assert c in CAUSES, (r["id"], c)

out = os.path.join(ROOT, "config", "consequences.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump({"_doc": __doc__.strip().splitlines()[0], "rules": RULES}, f, ensure_ascii=False, indent=1)
    f.write("\n")
print(f"{len(RULES)} règles de conséquences")
