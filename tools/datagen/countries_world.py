"""Génère les pays IA (assets/data/countries/<ISO3>/country.json + economy/<ISO3>_2026_10.json).
Ordres de grandeur arrondis, en euros. Simulation allégée (detail LIGHT)."""
import json, os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data"))

# id, nom, adjectif, langue, réserve de noms, population (M), titre du chef de gouvernement, honorifiques H/F,
# PIB, croissance, inflation, chômage, dette/PIB, recettes/PIB, dépenses/PIB, solde élec (TWh), interconnecté,
# commerce avec la France, alliances, renseignement, budget militaire, nucléaire, forces (terre, air, mer),
# capitale (nom, lon, lat), tempérament (agressivité, nationalisme, ouverture, exigence, prudence, militarisme)
C = [
 ("DEU","Allemagne","allemand","de","de",83.5,"Chancelier fédéral","Monsieur le Chancelier","Madame la Chancelière",4450,0.004,0.021,0.036,0.64,0.47,0.49,-28,True,170,["EU","NATO"],0.8,75,False,(8,6,2),("Berlin",13.40,52.52),(0.3,0.35,0.55,0.65,0.7,0.4)),
 ("ESP","Espagne","espagnol","es","es",48.6,"Président du gouvernement","Monsieur le Président","Madame la Présidente",1650,0.022,0.024,0.105,1.01,0.42,0.45,8,True,90,["EU","NATO"],0.65,18,False,(5,3,2),("Madrid",-3.70,40.42),(0.35,0.4,0.65,0.5,0.55,0.35)),
 ("ITA","Italie","italien","it","it",58.9,"Président du Conseil","Monsieur le Président du Conseil","Madame la Présidente du Conseil",2250,0.006,0.016,0.061,1.37,0.48,0.51,-48,True,95,["EU","NATO"],0.65,33,False,(6,4,3),("Rome",12.50,41.90),(0.45,0.6,0.45,0.65,0.5,0.45)),
 ("GBR","Royaume-Uni","britannique","en","en",69.3,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",3250,0.011,0.032,0.05,0.96,0.41,0.45,-25,True,65,["NATO"],0.85,72,True,(5,6,4),("Londres",-0.13,51.51),(0.45,0.55,0.5,0.7,0.6,0.6)),
 ("BEL","Belgique","belge","fr","be",11.9,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",640,0.01,0.022,0.058,1.06,0.5,0.54,-6,True,110,["EU","NATO"],0.6,8,False,(2,1,1),("Bruxelles",4.35,50.85),(0.2,0.25,0.7,0.5,0.65,0.3)),
 ("NLD","Pays-Bas","néerlandais","nl","nl",18.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",1150,0.012,0.025,0.038,0.44,0.43,0.45,-5,True,70,["EU","NATO"],0.7,21,False,(3,2,2),("Amsterdam",4.90,52.37),(0.3,0.35,0.65,0.7,0.6,0.4)),
 ("CHE","Suisse","suisse","de","de",9.0,"Président de la Confédération","Monsieur le Président","Madame la Présidente",850,0.012,0.006,0.028,0.37,0.32,0.33,-3,True,50,[],0.7,6,False,(2,1,0),("Berne",7.45,46.95),(0.1,0.5,0.6,0.7,0.85,0.2)),
 ("PRT","Portugal","portugais","pt","pt",10.6,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",290,0.019,0.022,0.064,0.95,0.43,0.43,2,False,18,["EU","NATO"],0.55,4.5,False,(2,1,1),("Lisbonne",-9.14,38.72),(0.2,0.35,0.7,0.45,0.55,0.3)),
 ("AUT","Autriche","autrichien","de","de",9.2,"Chancelier fédéral","Monsieur le Chancelier","Madame la Chancelière",500,0.008,0.028,0.055,0.8,0.5,0.53,-5,False,15,["EU"],0.6,4.5,False,(2,1,0),("Vienne",16.37,48.21),(0.25,0.55,0.5,0.55,0.65,0.3)),
 ("POL","Pologne","polonais","pl","pl",37.5,"Président du Conseil des ministres","Monsieur le Président du Conseil","Madame la Présidente du Conseil",900,0.032,0.035,0.03,0.58,0.41,0.47,-8,False,30,["EU","NATO"],0.7,38,False,(10,4,1),("Varsovie",21.01,52.23),(0.5,0.7,0.45,0.65,0.45,0.75)),
 ("SWE","Suède","suédois","sv","sv",10.6,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",580,0.018,0.018,0.085,0.33,0.48,0.49,30,False,15,["EU","NATO"],0.75,12,False,(3,3,1),("Stockholm",18.07,59.33),(0.3,0.35,0.6,0.6,0.6,0.5)),
 ("NOR","Norvège","norvégien","sv","sv",5.6,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",480,0.012,0.028,0.04,0.42,0.55,0.48,20,False,12,["NATO"],0.75,9,False,(2,2,2),("Oslo",10.75,59.91),(0.25,0.4,0.6,0.6,0.65,0.45)),
 ("GRC","Grèce","grec","el","el",10.3,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",240,0.021,0.026,0.09,1.47,0.48,0.49,-3,False,6,["EU","NATO"],0.6,8,False,(4,2,2),("Athènes",23.73,37.98),(0.45,0.7,0.5,0.6,0.5,0.6)),
 ("ROU","Roumanie","roumain","ro","ro",19.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",360,0.015,0.05,0.056,0.57,0.33,0.39,0,False,10,["EU","NATO"],0.55,9,False,(4,2,1),("Bucarest",26.10,44.43),(0.35,0.55,0.55,0.55,0.55,0.5)),
 ("UKR","Ukraine","ukrainien","uk","uk",37.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",190,0.02,0.09,0.11,0.95,0.4,0.6,-10,False,5,[],0.7,40,False,(14,2,0),("Kyiv",30.52,50.45),(0.5,0.8,0.55,0.7,0.4,0.85)),
 ("RUS","Russie","russe","ru","ru",144.0,"Président de la Fédération","Monsieur le Président","Madame la Présidente",2000,0.01,0.07,0.025,0.2,0.35,0.38,15,False,3,["CSTO"],0.75,120,True,(28,8,4),("Moscou",37.62,55.75),(0.85,0.9,0.2,0.85,0.3,0.9)),
 ("BLR","Biélorussie","biélorusse","ru","ru",9.2,"Président","Monsieur le Président","Madame la Présidente",70,0.02,0.06,0.04,0.4,0.38,0.4,0,False,1,["CSTO"],0.5,1.2,False,(3,1,0),("Minsk",27.56,53.90),(0.6,0.8,0.2,0.7,0.4,0.7)),
 ("TUR","Turquie","turc","tr","tr",86.0,"Président de la République","Monsieur le Président","Madame la Présidente",1200,0.03,0.3,0.087,0.3,0.31,0.35,0,False,18,["NATO"],0.7,25,False,(12,4,2),("Ankara",32.86,39.93),(0.65,0.85,0.35,0.8,0.4,0.75)),
 ("DZA","Algérie","algérien","ar","ar",46.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",250,0.032,0.05,0.11,0.5,0.3,0.36,0,False,10,[],0.55,20,False,(8,3,1),("Alger",3.06,36.75),(0.45,0.85,0.35,0.75,0.5,0.6)),
 ("MAR","Maroc","marocain","ar","ar",37.5,"Chef du gouvernement","Monsieur le Chef du gouvernement","Madame la Cheffe du gouvernement",150,0.035,0.022,0.13,0.7,0.27,0.31,0,False,12,[],0.6,6,False,(5,2,1),("Rabat",-6.84,34.02),(0.3,0.6,0.6,0.6,0.55,0.4)),
 ("TUN","Tunisie","tunisien","ar","ar",12.3,"Chef du gouvernement","Monsieur le Chef du gouvernement","Madame la Cheffe du gouvernement",50,0.015,0.065,0.16,0.82,0.29,0.34,0,False,8,[],0.45,1.2,False,(2,1,0),("Tunis",10.18,36.81),(0.25,0.6,0.5,0.5,0.6,0.3)),
 ("EGY","Égypte","égyptien","ar","ar",114.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",380,0.04,0.15,0.07,0.9,0.2,0.27,0,False,5,[],0.6,6,False,(10,4,2),("Le Caire",31.24,30.04),(0.4,0.7,0.4,0.7,0.55,0.6)),
 ("SAU","Arabie saoudite","saoudien","ar","ar",36.0,"Président du Conseil des ministres","Votre Altesse","Votre Altesse",1050,0.03,0.02,0.05,0.27,0.31,0.34,0,False,10,[],0.65,70,False,(6,5,1),("Riyad",46.72,24.71),(0.55,0.7,0.35,0.8,0.45,0.65)),
 ("USA","États-Unis","américain","en","en",340.0,"Président des États-Unis","Monsieur le Président","Madame la Présidente",28000,0.019,0.028,0.042,1.22,0.31,0.37,0,False,90,["NATO"],0.95,850,True,(0,0,0),("Washington",-77.04,38.90),(0.6,0.7,0.4,0.85,0.45,0.7)),
 ("CAN","Canada","canadien","en","en",41.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",2100,0.014,0.022,0.065,1.07,0.41,0.42,0,False,8,["NATO"],0.75,30,False,(0,0,0),("Ottawa",-75.70,45.42),(0.2,0.4,0.7,0.55,0.6,0.35)),
 ("CHN","Chine","chinois","zh","zh",1410.0,"Premier ministre du Conseil des affaires de l'État","Monsieur le Premier ministre","Madame la Première ministre",18000,0.045,0.005,0.051,0.9,0.26,0.33,0,False,80,[],0.85,270,True,(0,0,0),("Pékin",116.40,39.90),(0.55,0.8,0.35,0.85,0.6,0.6)),
 ("JPN","Japon","japonais","ja","ja",124.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",3800,0.007,0.022,0.025,2.4,0.37,0.42,0,False,15,[],0.8,50,False,(0,0,0),("Tokyo",139.69,35.69),(0.2,0.5,0.55,0.65,0.75,0.4)),
 ("IND","Inde","indien","hi","hi",1440.0,"Premier ministre","Monsieur le Premier ministre","Madame la Première ministre",3900,0.065,0.045,0.075,0.82,0.19,0.27,0,False,13,[],0.7,75,True,(0,0,0),("New Delhi",77.21,28.61),(0.45,0.75,0.45,0.75,0.5,0.6)),
 ("BRA","Brésil","brésilien","pt","pt",212.0,"Président de la République","Monsieur le Président","Madame la Présidente",2100,0.021,0.045,0.068,0.88,0.38,0.45,0,False,8,[],0.6,22,False,(0,0,0),("Brasilia",-47.88,-15.79),(0.3,0.6,0.6,0.6,0.5,0.4)),
]
def feminine_title(t):
    """Titre accordé au féminin (« Première ministre », « Chancelière fédérale », « Présidente... »)."""
    import re
    t = t.replace("Chancelier fédéral", "Chancelière fédérale").replace("Premier ministre", "Première ministre").replace("Chef du gouvernement", "Cheffe du gouvernement")
    return re.sub(r"^Président\b", "Présidente", t)

# Voisins avec lesquels les zones de pêche sont partagées (conflits de pêche possibles).
FISHERY_NEIGHBORS = {"GBR", "NOR", "ESP", "PRT", "BEL", "NLD", "ITA", "MAR", "DZA", "TUN"}

# Article défini, pour accorder le nom du pays dans les textes (« de l'Italie », « au Brésil »).
ARTICLES = {"AUT": "l'", "BEL": "la", "BLR": "la", "BRA": "le", "CAN": "le", "CHE": "la", "CHN": "la", "DEU": "l'", "DZA": "l'", "EGY": "l'", "ESP": "l'", "GBR": "le", "GRC": "la", "IND": "l'", "ITA": "l'", "JPN": "le", "MAR": "le", "NLD": "les", "NOR": "la", "POL": "la", "PRT": "le", "ROU": "la", "RUS": "la", "SAU": "l'", "SWE": "la", "TUN": "la", "TUR": "la", "UKR": "l'", "USA": "les"}
countries = []
for (cid,name,adj,lang,pool,pop,hog,hm,hf,gdp,g,inf,u,debt,rev,spend,elec,inter,trade,alli,intel,mil,nuc,forces,cap,temper) in C:
    a,n,o,t,c,m = temper
    rng = lambda v: [round(max(0,v-0.15),2), round(min(1,v+0.15),2)]
    e = {"_note": "Valeurs arrondies converties en euros ; simulation allégée (pays IA).",
         "currency": "EUR", "gdpBillions": gdp, "realGrowth": g, "potentialGrowth": round(g*0.85+0.002,4), "inflation": inf, "inflationTarget": 0.02 if inf < 0.08 else round(inf*0.7,3),
         "unemployment": u, "naturalUnemployment": round(u*0.95,4), "publicDebtBillions": round(gdp*debt), "averageDebtRate": 0.025 if debt < 2 else 0.008,
         "riskFreeRate": 0.024 if inf < 0.08 else round(inf, 3), "consumerConfidence": 0.47, "businessConfidence": 0.48, "averageNetMonthlyWage": 2000, "tradeBalanceBillions": 0,
         "aggregateBudget": {"revenueRatio": rev, "spendingRatio": spend}}
    json.dump(e, open(f"economy/{cid}_2026_10.json", "w"), ensure_ascii=False, indent=1)
    os.makedirs(f"countries/{cid}", exist_ok=True)
    d = {"id": cid, "name": name, "article": ARTICLES[cid], "adjective": adj, "language": lang, "namePool": pool, "detail": "LIGHT", "population": int(pop*1e6),
         "economy": f"economy/{cid}_2026_10.json",
         "institutions": {"headOfStateTitle": "Chef de l'État", "headOfGovernmentTitle": hog, "headOfGovernmentTitleFemale": feminine_title(hog), "honorificMale": hm, "honorificFemale": hf},
         "leader": {"traitRanges": {"aggressiveness": rng(a), "nationalism": rng(n), "openness": rng(o), "toughness": rng(t), "caution": rng(c), "militarism": rng(m)},
                    "ageRange": [44, 70], "economicLeaningRange": [-0.3, 0.4]},
         "strategic": {"electricityBalanceTWh": elec, "electricityInterconnected": inter, "tradeWithPartnersBillions": {"FRA": trade}, "alliances": alli,
                       "priorities": [], "intelligenceQuality": intel, "militaryBudgetBillions": mil, "nuclear": nuc,
                       "forces": {"land": forces[0], "air": forces[1], "sea": forces[2]},
                       "capital": {"name": cap[0], "lon": cap[1], "lat": cap[2]}}}
    if cid in FISHERY_NEIGHBORS: d["strategic"]["fisheryNeighbor"] = True
    json.dump(d, open(f"countries/{cid}/country.json", "w"), ensure_ascii=False, indent=1)
    countries.append(cid)

snap = json.load(open("world_snapshots/WORLD_SNAPSHOT_2026_10.json"))
snap["countries"] = ["countries/FRA/country.json"] + [f"countries/{c}/country.json" for c in countries]
R = []
def rel(a, b, kind, w, label, both=True):
    R.append({"a": a, "b": b, "kind": kind, "weight": w, "label": label})
EU = ["FRA","DEU","ESP","ITA","BEL","NLD","PRT","AUT","POL","SWE","GRC","ROU"]
for i, x in enumerate(EU):
    for y in EU[i+1:]:
        rel(x, y, "EU_PARTNERSHIP", 0.1, "Union européenne")
NATO = ["FRA","DEU","ESP","ITA","GBR","BEL","NLD","PRT","POL","SWE","NOR","GRC","ROU","TUR","USA","CAN"]
for i, x in enumerate(NATO):
    for y in NATO[i+1:]:
        rel(x, y, "ALLIANCE", 0.06, "alliés au sein de l'OTAN")
for x, y, k, w, l in [
 ("FRA","DEU","TRADE_PARTNER",0.05,"premier partenaire commercial"),("FRA","DEU","DISAGREEMENT",-0.05,"politique énergétique et nucléaire"),
 ("FRA","BEL","TRADE_PARTNER",0.05,"proximité économique"),("FRA","ITA","DISAGREEMENT",-0.04,"politique migratoire"),
 ("FRA","GBR","DISAGREEMENT",-0.05,"traversées de la Manche"),("FRA","USA","DISAGREEMENT",-0.04,"différends commerciaux"),
 ("FRA","CHN","TRADE_PARTNER",0.03,"échanges commerciaux"),("FRA","CHN","DISAGREEMENT",-0.08,"rivalité stratégique et droits humains"),
 ("FRA","RUS","SANCTION",-0.18,"sanctions européennes"),("FRA","RUS","THREAT",-0.08,"menaces et désinformation"),
 ("FRA","BLR","SANCTION",-0.12,"sanctions européennes"),("FRA","UKR","CRISIS_SOLIDARITY",0.12,"soutien face à l'agression"),
 ("FRA","UKR","AID",0.06,"aide militaire et financière"),("FRA","TUR","DISAGREEMENT",-0.06,"Méditerranée orientale"),
 ("FRA","DZA","DISAGREEMENT",-0.1,"contentieux mémoriel et migratoire"),("FRA","MAR","TRADE_PARTNER",0.06,"partenariat ancien"),
 ("FRA","TUN","TRADE_PARTNER",0.04,"liens humains et économiques"),("FRA","EGY","TRADE_PARTNER",0.04,"contrats d'armement"),
 ("FRA","SAU","TRADE_PARTNER",0.03,"contrats d'armement"),("FRA","IND","TRADE_PARTNER",0.06,"partenariat stratégique"),
 ("FRA","JPN","TRADE_PARTNER",0.04,"partenariat indo-pacifique"),("FRA","CHE","TRADE_PARTNER",0.06,"travailleurs frontaliers"),
 ("FRA","BRA","TRADE_PARTNER",0.03,"partenariat stratégique"),("FRA","CAN","TRADE_PARTNER",0.05,"francophonie"),
 ("RUS","UKR","THREAT",-0.35,"conflit armé et occupation"),("RUS","UKR","DISAGREEMENT",-0.15,"souveraineté territoriale"),
 ("RUS","POL","THREAT",-0.12,"tensions frontalières"),("RUS","USA","DISAGREEMENT",-0.15,"rivalité stratégique"),
 ("RUS","BLR","ALLIANCE",0.2,"union d'États"),("DZA","MAR","DISAGREEMENT",-0.2,"Sahara occidental"),
 ("GRC","TUR","DISAGREEMENT",-0.15,"mer Égée"),("USA","CHN","DISAGREEMENT",-0.15,"rivalité commerciale"),
 ("UKR","POL","CRISIS_SOLIDARITY",0.1,"accueil des réfugiés"),("SAU","EGY","ALLIANCE",0.08,"coopération régionale"),
]:
    rel(x, y, k, w, l)
snap["initialRelations"] = R
snap["alliances"] = [
 {"id": "NATO", "name": "OTAN", "defensive": True, "members": NATO},
 {"id": "EU", "name": "Union européenne", "defensive": True, "members": EU},
 {"id": "CSTO", "name": "OTSC", "defensive": True, "members": ["RUS", "BLR"]},
]
json.dump(snap, open("world_snapshots/WORLD_SNAPSHOT_2026_10.json", "w"), ensure_ascii=False, indent=1)
fr = json.load(open("countries/FRA/country.json"))
fr["strategic"]["tradeWithPartnersBillions"] = {c[0]: c[18] for c in C}
fr["strategic"].update({"nuclear": True, "capital": {"name": "Paris", "lon": 2.35, "lat": 48.86}})
json.dump(fr, open("countries/FRA/country.json", "w"), ensure_ascii=False, indent=1)
print(len(countries), "pays IA")
