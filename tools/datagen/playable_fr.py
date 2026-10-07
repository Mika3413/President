#!/usr/bin/env python3
"""Pays jouables autres que la France : Allemagne, Royaume-Uni, Italie, Espagne, États-Unis.

À partir des contours (tools/datagen/playable_geo_cache.json, produit par playable_geo.py) et
de tableaux d'ordres de grandeur, génère pour chaque pays :
- countries/<ISO3>/ : country.json (simulation complète), territory.json, government.json,
  elections.json ;
- economy/<ISO3>_FULL_2026_10.json : budget détaillé (mêmes lignes que la France, montants et
  taux du pays) ;
- military/<ISO3>_2026_10.json, infrastructure/<ISO3>_energy.json et <ISO3>_transport.json.

Les contenus communs (actions, lois, mesures, réformes, acteurs...) reprennent ceux de la
France : le lexique du pays (« la France » → « l'Allemagne », « l'Élysée » → « la
Chancellerie »...) les adapte au chargement et à l'affichage.

À lancer après countries_world.py (qui écrit les versions allégées de ces pays).
"""
import json, math, os, random, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "..", "assets", "data")
CACHE = json.load(open(os.path.join(HERE, "playable_geo_cache.json")))
FRA_ECO = json.load(open(os.path.join(DATA, "economy", "FRA_2026_10.json")))
FRA_ELEC = json.load(open(os.path.join(DATA, "countries", "FRA", "elections.json")))
FRA_GOV = json.load(open(os.path.join(DATA, "countries", "FRA", "government.json")))
ZONES = json.load(open(os.path.join(DATA, "geo", "zones.json")))["zones"]


def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    return "".join(c if c.isalnum() else "_" for c in s).strip("_")


def article(name):
    if not name: return ""
    if name[0].lower() in "aeiouyéèêâîôûœh": return "l'"
    if name.endswith("s") and not name.endswith("ss"): return "les" if name.lower().endswith(("es", "is")) and len(name) > 8 else "le"
    return "la" if name.endswith("e") else "le"


# ------------------------------------------------------------------ Pays

def party(fid, name, eco, soc, base, color, first, last, year, female=False):
    f = {"id": fid, "name": name, "economicPosition": eco, "socialPosition": soc, "baseStrength": base, "color": color,
         "figure": {"firstName": first, "lastName": last, "birthYear": year}}
    if female: f["figure"]["female"] = True
    return f


COUNTRIES = {
    "DEU": {
        "demonym": ("Allemands", "Allemandes", "allemand", "allemande", "allemands", "allemandes"),
        "seat": ("la Chancellerie", "à la Chancellerie", "de la Chancellerie", "Chancellerie"),
        "leaderTitle": ("chancelier", "Chancelier", "chancelière"),
        "assembly": "Bundestag", "senate": "Bundesrat", "court": "Cour constitutionnelle fédérale", "bank": "Bundesbank",
        "rail": "Deutsche Bahn", "power": "les énergéticiens allemands", "index": "DAX", "jobs": "l'Agence fédérale pour l'emploi",
        "minIncome": "le Bürgergeld", "minWage": "le salaire minimum", "team": "la Mannschaft", "rugby": "le XV d'Allemagne", "capital": "Berlin",
        "termYears": 4, "seats": 630, "electionLabel": "Élections fédérales",
        "families": [
            party("radical_left", "Gauche radicale (Die Linke)", -0.8, -0.45, -0.05, "b23a48", "Heidi", "Reichnek", 1988, True),
            party("left", "Sociaux-démocrates (SPD)", -0.4, -0.3, 0.0, "e0607e", "Lars", "Klingbaum", 1978),
            party("greens", "Verts (Grünen)", -0.35, -0.6, 0.0, "5aa95a", "Robert", "Habeckt", 1969),
            party("centre", "Libéraux (FDP)", 0.5, -0.2, -0.12, "e8b23a", "Christian", "Lindmann", 1979),
            party("right", "Chrétiens-démocrates (CDU/CSU)", 0.45, 0.25, 0.08, "3a3a3a", "Jens", "Spahnke", 1980),
            party("nationalist", "Droite nationaliste (AfD)", 0.3, 0.85, 0.08, "34407a", "Alice", "Weidl", 1979, True),
        ],
        "eco": {"gdp": 4450, "growth": 0.004, "inflation": 0.021, "unemployment": 0.036, "debt": 0.64, "rate": 0.022, "wage": 2900, "trade": 220, "revenue": 0.47, "spending": 0.49,
                "vat": 19, "corporate": 30, "csgLabel": "Impôt de solidarité (Soli)", "csgRate": 5.5},
        "localTitles": ("Délégué du gouvernement fédéral", "Président de la conférence régionale", "Ministre-président du Land", "Bourgmestre"),
        "local": [("land", "Élections des Länder", "DEPARTMENT", 5, "2028-09-17"), ("municipal", "Municipales", "CITY", 5, "2029-05-26")],
        "forces": (9, 6, 3), "nuclear": [],
        "energy": {"demand": 500, "mix": [("other_hydro", "Hydraulique", 5500, 0.38), ("wind", "Éolien", 72000, 0.24), ("solar", "Solaire", 95000, 0.11),
                                           ("bioenergy", "Bioénergies", 9000, 0.55), ("other_thermal", "Charbon, lignite et gaz", 70000, 0.38)],
                   "plants": [("neurath", "GAS_PLANT", "Centrale au lignite de Neurath", 6.6, 51.03, 4400), ("janschwalde", "GAS_PLANT", "Centrale au lignite de Jänschwalde", 14.46, 51.83, 3000),
                              ("irsching", "GAS_PLANT", "Centrale à gaz d'Irsching", 11.58, 48.77, 1400), ("schwedt", "REFINERY", "Raffinerie de Schwedt", 14.28, 53.06, 0)]},
        "ports": [("hamburg_port", "Port de Hambourg", 9.97, 53.54), ("bremerhaven", "Port de Bremerhaven", 8.58, 53.55)],
        "airports": [("fra_airport", "Aéroport de Francfort", 8.57, 50.04), ("muc_airport", "Aéroport de Munich", 11.79, 48.35), ("ber_airport", "Aéroport de Berlin-Brandebourg", 13.5, 52.37)],
        "lines": [("ice_berlin_munich", "RAIL_HIGH_SPEED", "ICE Berlin – Munich", ["Berlin", "Leipzig", "Nuremberg", "Munich"]),
                  ("ice_cologne_frankfurt", "RAIL_HIGH_SPEED", "ICE Cologne – Francfort", ["Cologne", "Francfort-sur-le-Main", "Francfort", "Stuttgart"]),
                  ("a7", "MOTORWAY", "Autobahn A7", ["Hambourg", "Hanovre", "Cassel", "Wurtzbourg"])],
        "units": [("Panzerbrigade 21", "ARMORED_BRIGADE"), ("Panzergrenadierbrigade 37", "MECHANIZED_BRIGADE"), ("Panzerbrigade 45 (Lituanie)", "ARMORED_BRIGADE"),
                  ("Gebirgsjägerbrigade 23", "MOUNTAIN_BRIGADE"), ("Luftlandebrigade 1", "AIRBORNE_BRIGADE"), ("Jägerbrigade 9", "INFANTRY_BRIGADE"),
                  ("Artilleriebataillon 345", "ARTILLERY_REGIMENT"), ("Flugabwehrraketengeschwader 1", "AIR_DEFENSE_REGIMENT"), ("Logistikbrigade", "SUPPORT_BRIGADE"),
                  ("Taktisches Luftwaffengeschwader 31", "FIGHTER_WING"), ("Taktisches Luftwaffengeschwader 74", "FIGHTER_WING"), ("Lufttransportgeschwader 62", "TRANSPORT_WING"),
                  ("Flotte de la Baltique (frégates)", "SURFACE_GROUP"), ("Flotte de la mer du Nord (frégates)", "SURFACE_GROUP")],
        "demography": {"birthRate": 0.0088, "deathRate": 0.0122, "netMigrationRate": 0.004, "agingPerYear": 0.003},
        "services": {"health": 0.62, "education": 0.6, "security": 0.6, "justice": 0.58, "transport": 0.5, "environment": 0.58, "agriculture": 0.55, "defense": 0.5, "social": 0.65, "pensions": 0.58},
    },
    "GBR": {
        "demonym": ("Britanniques", "Britanniques", "britannique", "britannique", "britanniques", "britanniques"),
        "seat": ("Downing Street", "à Downing Street", "de Downing Street", "Downing Street"),
        "leaderTitle": ("Premier ministre", "Premier ministre", "Première ministre"),
        "assembly": "Chambre des communes", "senate": "Chambre des lords", "court": "Cour suprême", "bank": "Banque d'Angleterre",
        "rail": "Network Rail", "power": "les fournisseurs d'électricité", "index": "FTSE 100", "jobs": "Jobcentre Plus",
        "minIncome": "l'Universal Credit", "minWage": "le National Living Wage", "team": "les Three Lions", "rugby": "le XV de la Rose", "capital": "Londres",
        "termYears": 5, "seats": 650, "electionLabel": "Élections générales",
        "families": [
            party("radical_left", "Gauche radicale", -0.85, -0.45, -0.12, "b23a48", "Jeremy", "Korbyn", 1949),
            party("left", "Travaillistes (Labour)", -0.35, -0.3, 0.05, "d50000", "Angela", "Raynour", 1980, True),
            party("greens", "Verts", -0.45, -0.65, -0.05, "5aa95a", "Zack", "Polanskee", 1982),
            party("centre", "Libéraux-démocrates", 0.0, -0.35, 0.0, "f5a623", "Ed", "Davies-Payne", 1975),
            party("right", "Conservateurs (Tories)", 0.55, 0.3, 0.0, "1f4fa3", "Kemi", "Badenach", 1980, True),
            party("nationalist", "Reform UK", 0.4, 0.8, 0.12, "12b6cf", "Nigel", "Farrich", 1964),
        ],
        "eco": {"gdp": 3250, "growth": 0.011, "inflation": 0.032, "unemployment": 0.05, "debt": 0.96, "rate": 0.04, "wage": 2700, "trade": -40, "revenue": 0.41, "spending": 0.45,
                "vat": 20, "corporate": 25, "csgLabel": "National Insurance", "csgRate": 8.0},
        "localTitles": ("Délégué du gouvernement", "Maire métropolitain", "Président du conseil de comté", "Maire"),
        "local": [("county", "Élections locales", "DEPARTMENT", 4, "2027-05-06"), ("devolved", "Parlements régionaux", "REGION", 5, "2031-05-08")],
        "forces": (6, 6, 4), "nuclear": [("heysham", "Centrale nucléaire de Heysham", -2.92, 54.03, 2400), ("torness", "Centrale nucléaire de Torness", -2.41, 55.97, 1200),
                                          ("sizewell", "Centrale nucléaire de Sizewell B", 1.62, 52.21, 1200), ("hartlepool", "Centrale nucléaire de Hartlepool", -1.18, 54.63, 1200)],
        "energy": {"demand": 280, "mix": [("other_hydro", "Hydraulique", 4700, 0.35), ("wind", "Éolien (dont en mer)", 31000, 0.32), ("solar", "Solaire", 18000, 0.1),
                                           ("bioenergy", "Bioénergies (Drax)", 6000, 0.6), ("other_thermal", "Centrales à gaz", 35000, 0.35)],
                   "plants": [("pembroke", "GAS_PLANT", "Centrale à gaz de Pembroke", -4.99, 51.68, 2200), ("fawley", "REFINERY", "Raffinerie de Fawley", -1.34, 50.84, 0)]},
        "ports": [("felixstowe", "Port de Felixstowe", 1.33, 51.95), ("southampton_port", "Port de Southampton", -1.4, 50.9)],
        "airports": [("lhr", "Aéroport d'Heathrow", -0.45, 51.47), ("man_airport", "Aéroport de Manchester", -2.27, 53.36)],
        "lines": [("hs1", "RAIL_HIGH_SPEED", "High Speed 1", ["Londres", "Canterbury"]), ("wcml", "RAIL_HIGH_SPEED", "West Coast Main Line", ["Londres", "Birmingham", "Manchester", "Glasgow"]),
                  ("m1", "MOTORWAY", "Autoroute M1", ["Londres", "Leicester", "Sheffield", "Leeds"])],
        "units": [("1st Armoured Infantry Brigade", "ARMORED_BRIGADE"), ("12th Armoured Infantry Brigade", "MECHANIZED_BRIGADE"), ("16 Air Assault Brigade", "AIRBORNE_BRIGADE"),
                  ("3 Commando Brigade", "MARINE_BRIGADE"), ("4th Light Brigade", "LIGHT_ARMORED_BRIGADE"), ("7th Light Mechanised Brigade", "INFANTRY_BRIGADE"),
                  ("1st Artillery Brigade", "ARTILLERY_REGIMENT"), ("7th Air Defence Group", "AIR_DEFENSE_REGIMENT"), ("102 Logistic Brigade", "SUPPORT_BRIGADE"),
                  ("RAF Lossiemouth (Typhoon)", "FIGHTER_WING"), ("RAF Coningsby (Typhoon)", "FIGHTER_WING"), ("RAF Marham (F-35)", "FIGHTER_WING"), ("RAF Brize Norton", "TRANSPORT_WING"),
                  ("Groupe aéronaval (HMS Queen Elizabeth)", "CARRIER_GROUP"), ("Flotte de Portsmouth", "SURFACE_GROUP"), ("Flotte de Devonport", "SURFACE_GROUP"),
                  ("Force de dissuasion (Vanguard)", "SSBN_FORCE")],
        "demography": {"birthRate": 0.0098, "deathRate": 0.0092, "netMigrationRate": 0.006, "agingPerYear": 0.0022},
        "services": {"health": 0.45, "education": 0.58, "security": 0.52, "justice": 0.45, "transport": 0.48, "environment": 0.55, "agriculture": 0.5, "defense": 0.62, "social": 0.5, "pensions": 0.55},
    },
    "ITA": {
        "demonym": ("Italiens", "Italiennes", "italien", "italienne", "italiens", "italiennes"),
        "seat": ("le palais Chigi", "au palais Chigi", "du palais Chigi", "Palais Chigi"),
        "leaderTitle": ("président du Conseil", "Président du Conseil", "présidente du Conseil"),
        "assembly": "Chambre des députés", "senate": "Sénat", "court": "Cour constitutionnelle", "bank": "Banque d'Italie",
        "rail": "Trenitalia", "power": "Enel", "index": "FTSE MIB", "jobs": "l'Agence nationale pour l'emploi",
        "minIncome": "l'allocation d'inclusion", "minWage": "les minima conventionnels", "team": "la Squadra Azzurra", "rugby": "le XV d'Italie", "capital": "Rome",
        "termYears": 5, "seats": 400, "electionLabel": "Élections générales",
        "families": [
            party("radical_left", "Gauche (AVS et M5S)", -0.7, -0.35, 0.02, "b23a48", "Giuseppe", "Contessa", 1964),
            party("left", "Parti démocrate", -0.35, -0.4, 0.02, "e0607e", "Elly", "Schlain", 1985, True),
            party("greens", "Verts", -0.4, -0.6, -0.12, "5aa95a", "Angelo", "Bonello", 1962),
            party("centre", "Centre libéral (Azione)", 0.3, -0.2, -0.1, "e8b23a", "Carlo", "Calendo", 1973),
            party("right", "Forza Italia et Ligue", 0.5, 0.45, 0.05, "3f72c4", "Antonio", "Tajano", 1953),
            party("nationalist", "Frères d'Italie", 0.25, 0.75, 0.12, "34407a", "Giorgia", "Melona", 1977, True),
        ],
        "eco": {"gdp": 2250, "growth": 0.006, "inflation": 0.016, "unemployment": 0.061, "debt": 1.37, "rate": 0.035, "wage": 1900, "trade": 50, "revenue": 0.48, "spending": 0.51,
                "vat": 22, "corporate": 24, "csgLabel": "IRAP (taxe régionale)", "csgRate": 3.9},
        "localTitles": ("Préfet", "Président de région", "Président de province", "Maire"),
        "local": [("regional", "Régionales", "REGION", 5, "2030-05-26"), ("municipal", "Municipales", "CITY", 5, "2027-06-06")],
        "forces": (7, 4, 3), "nuclear": [],
        "energy": {"demand": 300, "mix": [("other_hydro", "Hydraulique", 22000, 0.25), ("wind", "Éolien", 13000, 0.22), ("solar", "Solaire", 37000, 0.14),
                                           ("bioenergy", "Bioénergies et géothermie", 5000, 0.6), ("other_thermal", "Centrales à gaz", 50000, 0.4)],
                   "plants": [("brindisi", "GAS_PLANT", "Centrale de Brindisi", 18.04, 40.56, 2600), ("civitavecchia", "GAS_PLANT", "Centrale de Civitavecchia", 11.78, 42.13, 1980),
                              ("sarroch", "REFINERY", "Raffinerie de Sarroch", 9.02, 39.08, 0)]},
        "ports": [("genova_port", "Port de Gênes", 8.91, 44.4), ("trieste_port", "Port de Trieste", 13.75, 45.65)],
        "airports": [("fco", "Aéroport de Rome-Fiumicino", 12.25, 41.8), ("mxp", "Aéroport de Milan-Malpensa", 8.72, 45.63)],
        "lines": [("av_milan_naples", "RAIL_HIGH_SPEED", "Alta Velocità Milan – Naples", ["Milan", "Bologne", "Florence", "Rome", "Naples"]),
                  ("a1", "MOTORWAY", "Autoroute du Soleil (A1)", ["Milan", "Bologne", "Florence", "Rome", "Naples"])],
        "units": [("Brigata corazzata Ariete", "ARMORED_BRIGADE"), ("Brigata meccanizzata Pinerolo", "MECHANIZED_BRIGADE"), ("Brigata alpina Taurinense", "MOUNTAIN_BRIGADE"),
                  ("Brigata alpina Julia", "MOUNTAIN_BRIGADE"), ("Brigata paracadutisti Folgore", "AIRBORNE_BRIGADE"), ("Brigata San Marco", "MARINE_BRIGADE"),
                  ("Brigata Sassari", "INFANTRY_BRIGADE"), ("Reggimento artiglieria", "ARTILLERY_REGIMENT"), ("Reggimento antiaerei", "AIR_DEFENSE_REGIMENT"),
                  ("4° Stormo (Eurofighter)", "FIGHTER_WING"), ("32° Stormo (F-35)", "FIGHTER_WING"), ("46ª Brigata aerea", "TRANSPORT_WING"),
                  ("Groupe aéronaval (Cavour)", "CARRIER_GROUP"), ("Flotte de Tarente", "SURFACE_GROUP")],
        "demography": {"birthRate": 0.0065, "deathRate": 0.0118, "netMigrationRate": 0.003, "agingPerYear": 0.0032},
        "services": {"health": 0.55, "education": 0.5, "security": 0.5, "justice": 0.35, "transport": 0.52, "environment": 0.48, "agriculture": 0.55, "defense": 0.52, "social": 0.5, "pensions": 0.55},
    },
    "ESP": {
        "demonym": ("Espagnols", "Espagnoles", "espagnol", "espagnole", "espagnols", "espagnoles"),
        "seat": ("la Moncloa", "à la Moncloa", "de la Moncloa", "Moncloa"),
        "leaderTitle": ("président du gouvernement", "Président du gouvernement", "présidente du gouvernement"),
        "assembly": "Congrès des députés", "senate": "Sénat", "court": "Tribunal constitutionnel", "bank": "Banque d'Espagne",
        "rail": "Renfe", "power": "Iberdrola", "index": "IBEX 35", "jobs": "le Service public de l'emploi",
        "minIncome": "le revenu minimum vital", "minWage": "le salaire minimum", "team": "la Roja", "rugby": "le XV d'Espagne", "capital": "Madrid",
        "termYears": 4, "seats": 350, "electionLabel": "Élections générales",
        "families": [
            party("radical_left", "Gauche radicale (Sumar, Podemos)", -0.75, -0.55, -0.02, "b23a48", "Yolanda", "Diez", 1971, True),
            party("left", "Socialistes (PSOE)", -0.35, -0.4, 0.05, "e0607e", "María Jesús", "Monteiro", 1966, True),
            party("greens", "Écologistes", -0.4, -0.6, -0.15, "5aa95a", "Íñigo", "Errejono", 1983),
            party("centre", "Centristes", 0.2, -0.1, -0.15, "e8b23a", "Inés", "Arrimadia", 1981, True),
            party("right", "Parti populaire", 0.5, 0.35, 0.06, "3f72c4", "Alberto", "Feijoa", 1961),
            party("nationalist", "Vox", 0.35, 0.85, 0.05, "5fb336", "Santiago", "Abascol", 1976),
        ],
        "eco": {"gdp": 1650, "growth": 0.022, "inflation": 0.024, "unemployment": 0.105, "debt": 1.01, "rate": 0.031, "wage": 1800, "trade": 10, "revenue": 0.42, "spending": 0.45,
                "vat": 21, "corporate": 25, "csgLabel": "Cotisations de solidarité", "csgRate": 4.5},
        "localTitles": ("Délégué du gouvernement", "Président de communauté autonome", "Président de la députation provinciale", "Maire"),
        "local": [("autonomic", "Élections autonomiques", "REGION", 4, "2027-05-23"), ("municipal", "Municipales", "CITY", 4, "2027-05-23")],
        "forces": (5, 3, 2), "nuclear": [("almaraz", "Centrale nucléaire d'Almaraz", -5.7, 39.81, 2000), ("asco", "Centrale nucléaire d'Ascó", 0.57, 41.2, 2000),
                                          ("cofrentes", "Centrale nucléaire de Cofrentes", -1.05, 39.21, 1090), ("vandellos", "Centrale nucléaire de Vandellós", 0.87, 40.95, 1080),
                                          ("trillo", "Centrale nucléaire de Trillo", -2.62, 40.7, 1000)],
        "energy": {"demand": 245, "mix": [("other_hydro", "Hydraulique", 17000, 0.2), ("wind", "Éolien", 31000, 0.25), ("solar", "Solaire", 32000, 0.2),
                                           ("bioenergy", "Bioénergies", 1100, 0.5), ("other_thermal", "Cycles combinés au gaz", 26000, 0.2)],
                   "plants": [("cartagena", "REFINERY", "Raffinerie de Carthagène", -0.98, 37.58, 0)]},
        "ports": [("algeciras", "Port d'Algésiras", -5.44, 36.13), ("valencia_port", "Port de Valence", -0.32, 39.45)],
        "airports": [("mad", "Aéroport de Madrid-Barajas", -3.57, 40.49), ("bcn", "Aéroport de Barcelone", 2.08, 41.3)],
        "lines": [("ave_mad_bcn", "RAIL_HIGH_SPEED", "AVE Madrid – Barcelone", ["Madrid", "Saragosse", "Barcelone"]),
                  ("ave_mad_sev", "RAIL_HIGH_SPEED", "AVE Madrid – Séville", ["Madrid", "Cordoue", "Séville"])],
        "units": [("Brigada Guadarrama XII", "ARMORED_BRIGADE"), ("Brigada Extremadura XI", "MECHANIZED_BRIGADE"), ("Brigada Paracaidista", "AIRBORNE_BRIGADE"),
                  ("Brigada de Infantería de Marina", "MARINE_BRIGADE"), ("Brigada Rey Alfonso XIII (Légion)", "LIGHT_ARMORED_BRIGADE"), ("Regimiento de Artillería", "ARTILLERY_REGIMENT"),
                  ("Ala 11 (Eurofighter)", "FIGHTER_WING"), ("Ala 15 (F-18)", "FIGHTER_WING"), ("Ala 31 (A400M)", "TRANSPORT_WING"),
                  ("Groupe naval de Rota", "SURFACE_GROUP"), ("Flotte de Carthagène", "SURFACE_GROUP")],
        "demography": {"birthRate": 0.0068, "deathRate": 0.0092, "netMigrationRate": 0.009, "agingPerYear": 0.0028},
        "services": {"health": 0.6, "education": 0.52, "security": 0.55, "justice": 0.45, "transport": 0.65, "environment": 0.5, "agriculture": 0.55, "defense": 0.5, "social": 0.5, "pensions": 0.62},
    },
    "USA": {
        "demonym": ("Américains", "Américaines", "américain", "américaine", "américains", "américaines"),
        "seat": ("la Maison-Blanche", "à la Maison-Blanche", "de la Maison-Blanche", "Maison-Blanche"),
        "leaderTitle": ("président", "Président", "présidente"),
        "assembly": "Chambre des représentants", "senate": "Sénat", "court": "Cour suprême", "bank": "Réserve fédérale",
        "rail": "Amtrak", "power": "les compagnies d'électricité", "index": "Dow Jones", "jobs": "les agences pour l'emploi",
        "minIncome": "les bons alimentaires", "minWage": "le salaire minimum fédéral", "team": "l'équipe des États-Unis", "rugby": "les Eagles", "capital": "Washington",
        "termYears": 4, "seats": 435, "electionLabel": "Présidentielle", "secondRound": False,
        "families": [
            party("radical_left", "Démocrates progressistes", -0.75, -0.6, -0.1, "b23a48", "Alexandria", "Ocasio-Cortel", 1989, True),
            party("left", "Démocrates", -0.25, -0.45, 0.1, "2f6fcc", "Gavin", "Newsum", 1967),
            party("greens", "Parti vert", -0.5, -0.7, -0.35, "5aa95a", "Jill", "Stine", 1950, True),
            party("centre", "Indépendants", 0.15, -0.05, -0.3, "e8b23a", "Mark", "Cuban-Smith", 1958),
            party("right", "Républicains", 0.55, 0.55, 0.12, "d12c2c", "Marco", "Rubiot", 1971),
            party("nationalist", "Républicains MAGA", 0.35, 0.85, 0.05, "8b1a1a", "J. D.", "Vanse", 1984),
        ],
        "eco": {"gdp": 28000, "growth": 0.019, "inflation": 0.028, "unemployment": 0.042, "debt": 1.22, "rate": 0.042, "wage": 4200, "trade": -900, "revenue": 0.31, "spending": 0.37,
                "vat": 7, "corporate": 21, "csgLabel": "Taxe sur les salaires (FICA)", "csgRate": 7.65},
        "localTitles": ("Administrateur fédéral régional", "Coordinateur régional", "Gouverneur", "Maire"),
        "local": [("governor", "Élections des gouverneurs", "DEPARTMENT", 4, "2026-11-03"), ("municipal", "Municipales", "CITY", 4, "2029-11-06")],
        "forces": (12, 10, 6), "nuclear": [("palo_verde", "Centrale nucléaire de Palo Verde", -112.86, 33.39, 3900), ("vogtle", "Centrale nucléaire de Vogtle", -81.76, 33.14, 4500),
                                            ("browns_ferry", "Centrale nucléaire de Browns Ferry", -87.12, 34.7, 3800), ("south_texas", "Centrale nucléaire de South Texas", -96.05, 28.8, 2600),
                                            ("diablo_canyon", "Centrale nucléaire de Diablo Canyon", -120.85, 35.21, 2200), ("peach_bottom", "Centrale nucléaire de Peach Bottom", -76.27, 39.76, 2700),
                                            ("braidwood", "Centrale nucléaire de Braidwood", -88.23, 41.24, 2400), ("oconee", "Centrale nucléaire d'Oconee", -82.9, 34.79, 2600)],
        "energy": {"demand": 4100, "mix": [("other_hydro", "Hydraulique", 80000, 0.37), ("wind", "Éolien", 155000, 0.34), ("solar", "Solaire", 180000, 0.22),
                                            ("bioenergy", "Bioénergies", 12000, 0.55), ("other_thermal", "Gaz et charbon", 700000, 0.42)],
                   "plants": [("hoover", "HYDRO_DAM", "Barrage Hoover", -114.74, 36.02, 2080), ("grand_coulee", "HYDRO_DAM", "Barrage de Grand Coulee", -118.98, 47.96, 6800),
                              ("port_arthur", "REFINERY", "Raffinerie de Port Arthur", -93.93, 29.89, 0)]},
        "ports": [("la_port", "Port de Los Angeles", -118.27, 33.73), ("ny_port", "Port de New York", -74.15, 40.67), ("houston_port", "Port de Houston", -95.1, 29.73)],
        "airports": [("atl", "Aéroport d'Atlanta", -84.43, 33.64), ("lax", "Aéroport de Los Angeles", -118.41, 33.94), ("jfk", "Aéroport JFK", -73.78, 40.64)],
        "lines": [("acela", "RAIL_HIGH_SPEED", "Acela (corridor Nord-Est)", ["Boston", "New York", "Philadelphie", "Washington"]),
                  ("i95", "MOTORWAY", "Interstate 95", ["Boston", "New York", "Washington", "Miami"]),
                  ("i10", "MOTORWAY", "Interstate 10", ["Los Angeles", "Phoenix", "Houston"])],
        "units": [("1st Armored Division", "ARMORED_BRIGADE"), ("1st Cavalry Division", "ARMORED_BRIGADE"), ("3rd Infantry Division", "MECHANIZED_BRIGADE"),
                  ("4th Infantry Division", "MECHANIZED_BRIGADE"), ("82nd Airborne Division", "AIRBORNE_BRIGADE"), ("101st Airborne Division", "AIRBORNE_BRIGADE"),
                  ("10th Mountain Division", "MOUNTAIN_BRIGADE"), ("1st Marine Division", "MARINE_BRIGADE"), ("2nd Marine Division", "MARINE_BRIGADE"),
                  ("25th Infantry Division", "INFANTRY_BRIGADE"), ("III Corps Artillery", "ARTILLERY_REGIMENT"), ("32nd Air Defense Command", "AIR_DEFENSE_REGIMENT"),
                  ("1st Fighter Wing", "FIGHTER_WING"), ("4th Fighter Wing", "FIGHTER_WING"), ("33rd Fighter Wing", "FIGHTER_WING"), ("388th Fighter Wing", "FIGHTER_WING"),
                  ("305th Air Mobility Wing", "TANKER_WING"), ("436th Airlift Wing", "TRANSPORT_WING"),
                  ("Carrier Strike Group 2 (Atlantique)", "CARRIER_GROUP"), ("Carrier Strike Group 3 (Pacifique)", "CARRIER_GROUP"), ("Second Fleet", "SURFACE_GROUP"), ("Third Fleet", "SURFACE_GROUP"),
                  ("Force de dissuasion (Ohio)", "SSBN_FORCE")],
        "demography": {"birthRate": 0.011, "deathRate": 0.0095, "netMigrationRate": 0.003, "agingPerYear": 0.002},
        "services": {"health": 0.5, "education": 0.55, "security": 0.48, "justice": 0.5, "transport": 0.52, "environment": 0.45, "agriculture": 0.6, "defense": 0.85, "social": 0.4, "pensions": 0.55},
    },
}

# Populations de référence (millions) des États et Länder ; ailleurs, réparties d'après les villes et la surface.
POP = {
    "DEU": {"BW": 11.3, "BY": 13.4, "BE": 3.9, "BB": 2.6, "HB": 0.7, "HH": 1.9, "HE": 6.4, "MV": 1.6, "NI": 8.1, "NW": 18.1, "RP": 4.2, "SL": 1.0, "SN": 4.1, "ST": 2.2, "SH": 3.0, "TH": 2.1},
    "USA": {"AL": 5.1, "AK": 0.7, "AZ": 7.4, "AR": 3.1, "CA": 39.0, "CO": 5.9, "CT": 3.6, "DE": 1.0, "DC": 0.7, "FL": 22.6, "GA": 11.0, "HI": 1.4, "ID": 2.0, "IL": 12.5,
            "IN": 6.9, "IA": 3.2, "KS": 2.9, "KY": 4.5, "LA": 4.6, "ME": 1.4, "MD": 6.2, "MA": 7.0, "MI": 10.0, "MN": 5.7, "MS": 2.9, "MO": 6.2, "MT": 1.1, "NE": 2.0,
            "NV": 3.2, "NH": 1.4, "NJ": 9.3, "NM": 2.1, "NY": 19.6, "NC": 10.8, "ND": 0.8, "OH": 11.8, "OK": 4.1, "OR": 4.2, "PA": 13.0, "RI": 1.1, "SC": 5.4, "SD": 0.9,
            "TN": 7.1, "TX": 30.5, "UT": 3.4, "VT": 0.6, "VA": 8.7, "WA": 7.8, "WV": 1.8, "WI": 5.9, "WY": 0.6},
}
NATIONAL_POP = {"DEU": 83.5, "GBR": 69.3, "ITA": 58.9, "ESP": 48.6, "USA": 340.0}
LEANING = {"DEU": {"E": 0.3, "S": 0.25, "N": -0.05, "W": -0.05}, "USA": {"NE": -0.45, "MA": -0.3, "ENC": 0.05, "WNC": 0.3, "SA": 0.15, "ESC": 0.5, "WSC": 0.45, "MTN": 0.3, "PAC": -0.4}}
REGIONAL_LANGUAGE = {"GBR": {"WLS", "SCT"}, "ESP": {"CATALO", "PAYS_B", "GALICE"}, "ITA": {"TRENTI", "SARDAI", "VALLEE"}}


def zone_owner_near(lon, lat):
    best = min(ZONES, key=lambda z: (z["lon"] - lon) ** 2 + (z["lat"] - lat) ** 2)
    return best


def build(iso, cfg):
    rng = random.Random(iso)
    geo = CACHE[iso]
    name_fr = json.load(open(os.path.join(DATA, "countries", iso, "country.json")))
    d0 = json.load(open(os.path.join(DATA, "countries", iso, "country.json")))
    # ---------------- Villes
    cities = []
    used = set()
    for c in geo["cities"]:
        cid = f"{iso.lower()}_{slug(c['name'])}"
        if cid in used: continue
        used.add(cid)
        cities.append({"id": cid, "name": c["name"], "department": c["department"], "lon": c["lon"], "lat": c["lat"],
                       "population": int(c["population"] * 0.7), "urbanAreaPopulation": int(c["population"]), "rank": 3, "capital": c["capital"]})
    for d in geo["departments"]:
        if not any(c["department"] == d["code"] for c in cities):
            cid = f"{iso.lower()}_{slug(d['name'])}_chef_lieu"
            cities.append({"id": cid, "name": d["name"], "department": d["code"], "lon": d["lon"], "lat": d["lat"], "population": 40000, "urbanAreaPopulation": 60000, "rank": 3, "capital": False})
    cities.sort(key=lambda c: -c["urbanAreaPopulation"])
    capital = next((c for c in cities if c["capital"]), None) or next((c for c in cities if c["name"].startswith(cfg["capital"])), cities[0])
    # Rang 1 : grandes métropoles (comme Lyon ou Marseille) ; 2 : villes importantes ; 3 : les autres.
    big_n = {"USA": 14, "GBR": 6}.get(iso, 7)
    for i, c in enumerate(cities):
        c["rank"] = 1 if c is capital or i < big_n else (2 if i < big_n * 4 else 3)
    # ---------------- Départements
    total_area = sum(d["area"] for d in geo["departments"])
    city_pop = {d["code"]: sum(c["urbanAreaPopulation"] for c in cities if c["department"] == d["code"]) for d in geo["departments"]}
    total_city = sum(city_pop.values()) or 1
    deps = []
    for d in geo["departments"]:
        if iso in POP and d["code"] in POP[iso]: pop = POP[iso][d["code"]] * 1e6
        else: pop = NATIONAL_POP[iso] * 1e6 * (0.6 * city_pop[d["code"]] / total_city + 0.4 * d["area"] / total_area)
        urban = min(0.95, 0.25 + city_pop[d["code"]] / max(pop, 1) * 0.6)
        lean = LEANING.get(iso, {}).get(d["region"], 0.0) + rng.uniform(-0.25, 0.25)
        z = zone_owner_near(d["lon"], d["lat"])
        tags = []
        if any(zz.get("coastal") and zz["owner"] == iso and abs(zz["lon"] - d["lon"]) < 1.2 and abs(zz["lat"] - d["lat"]) < 1.2 for zz in ZONES): tags.append("coastal")
        if any(zz["owner"] not in ("", iso) and not zz["sea"] and abs(zz["lon"] - d["lon"]) < 1.1 and abs(zz["lat"] - d["lat"]) < 1.1 for zz in ZONES): tags.append("border")
        if d["region"] in REGIONAL_LANGUAGE.get(iso, set()): tags.append("regionalLanguage")
        deps.append({"code": d["code"], "name": d["name"], "article": article(d["name"]), "region": d["region"], "population": int(pop / 1000) * 1000,
                     "tags": tags,
                     "profile": {"unemployment": round(max(0.02, cfg["eco"]["unemployment"] * rng.uniform(0.7, 1.35)), 3), "incomeIndex": round(0.85 + urban * 0.3 + rng.uniform(-0.05, 0.05), 2),
                                 "urbanShare": round(urban, 2), "seniorShare": round(rng.uniform(0.17, 0.27), 2), "politicalLeaning": round(max(-0.9, min(0.9, lean)), 2),
                                 "healthAccess": round(0.8 + urban * 0.4, 2), "crime": round(0.7 + urban * 0.6, 2), "industryShare": round(rng.uniform(0.08, 0.28), 2),
                                 "agricultureShare": round(max(0.0, 0.06 - urban * 0.05 + rng.uniform(-0.01, 0.02)), 3), "pollution": round(0.7 + urban * 0.6, 2)}})
    # Mise à l'échelle sur la population nationale.
    scale = NATIONAL_POP[iso] * 1e6 / sum(x["population"] for x in deps)
    for x in deps: x["population"] = int(x["population"] * scale / 1000) * 1000
    regions = []
    for rc, rn in geo["regions"].items():
        members = [x for x in deps if x["region"] == rc]
        if not members: continue
        cap = max((c for c in cities if any(c["department"] == m["code"] for m in members)), key=lambda c: c["urbanAreaPopulation"])
        w = sum(m["population"] for m in members)
        avg = lambda k: round(sum(m["profile"][k] * m["population"] for m in members) / w, 3)
        regions.append({"code": rc, "name": rn, "capitalCityId": cap["id"],
                        "defaults": {"unemployment": avg("unemployment"), "incomeIndex": avg("incomeIndex"), "urbanShare": avg("urbanShare"), "seniorShare": avg("seniorShare"), "politicalLeaning": avg("politicalLeaning")}})
    for c in cities: c.pop("capital", None)
    territory = {"regions": regions, "departments": deps, "cities": cities}
    os.makedirs(os.path.join(DATA, "countries", iso), exist_ok=True)
    json.dump(territory, open(os.path.join(DATA, "countries", iso, "territory.json"), "w"), ensure_ascii=False, indent=1)

    # ---------------- Gouvernement
    gov = json.loads(json.dumps(FRA_GOV))
    titles = MINISTRY_TITLES[iso]
    for m in gov["ministries"]:
        if m["id"] in titles: m["title"], m["shortTitle"] = titles[m["id"]]
    pt, rp, dp_, mayor = cfg["localTitles"]
    gov["localTitles"] = {"prefect": pt, "regionPresident": rp, "departmentPresident": dp_, "mayor": mayor,
                          "prefectFemale": feminize(pt), "regionPresidentFemale": feminize(rp), "departmentPresidentFemale": feminize(dp_)}
    gov["initialServiceQuality"] = cfg["services"]
    json.dump(gov, open(os.path.join(DATA, "countries", iso, "government.json"), "w"), ensure_ascii=False, indent=1)

    # ---------------- Élections
    el = json.loads(json.dumps(FRA_ELEC))
    el["termYears"] = cfg["termYears"]
    el["families"] = cfg["families"]
    el["legislative"]["seats"] = cfg["seats"]
    el["legislative"]["termYears"] = cfg["termYears"]
    el["local"]["kinds"] = [{"id": k, "label": lab, "level": lvl, "termYears": t, "firstDate": f"{date}T08:00:00Z"} for k, lab, lvl, t, date in cfg["local"]]
    if "senate" in el:
        seats = el["senate"]["seats"]
        el["senate"]["initialSeats"] = {f["id"]: int(seats * share) for f, share in zip(cfg["families"], [0.06, 0.25, 0.08, 0.1, 0.36, 0.15])}
    json.dump(el, open(os.path.join(DATA, "countries", iso, "elections.json"), "w"), ensure_ascii=False, indent=1)

    # ---------------- Économie (budget détaillé à l'échelle du pays)
    e = cfg["eco"]
    eco = json.loads(json.dumps(FRA_ECO))
    fr_rev = sum(r["amountBillions"] for r in FRA_ECO["budget"]["revenues"])
    fr_sp = sum(s["amountBillions"] for s in FRA_ECO["budget"]["spending"])
    k_rev = e["gdp"] * e["revenue"] / fr_rev
    # Les dépenses du budget sont hors intérêts de la dette (calculés à part par le moteur).
    k_sp = e["gdp"] * (e["spending"] - e["debt"] * e["rate"] * 0.8) / fr_sp
    eco.update({"_source": f"Ordres de grandeur arrondis ({iso}), convertis en euros ; budget calqué sur la structure française.",
                "gdpBillions": e["gdp"], "realGrowth": e["growth"], "potentialGrowth": round(e["growth"] * 0.85 + 0.002, 4), "inflation": e["inflation"],
                "unemployment": e["unemployment"], "naturalUnemployment": round(e["unemployment"] * 0.95, 4), "publicDebtBillions": round(e["gdp"] * e["debt"]),
                "averageDebtRate": e["rate"] * 0.8, "riskFreeRate": e["rate"], "averageNetMonthlyWage": e["wage"], "tradeBalanceBillions": e["trade"]})
    for r in eco["budget"]["revenues"]:
        r["amountBillions"] = round(r["amountBillions"] * k_rev, 1)
        if r["id"] == "vat": r["rate"] = e["vat"]; r["label"] = "TVA" if iso != "USA" else "Taxes sur les ventes"
        if r["id"] == "corporate_tax": r["rate"] = e["corporate"]
        if r["id"] == "csg": r["label"] = e["csgLabel"]; r["rate"] = e["csgRate"]
    for s in eco["budget"]["spending"]:
        s["amountBillions"] = round(s["amountBillions"] * k_sp, 1)
    defense = next(s for s in eco["budget"]["spending"] if s["id"] == "defense")
    defense["amountBillions"] = d0["strategic"]["militaryBudgetBillions"]
    eco.pop("aggregateBudget", None)
    json.dump(eco, open(os.path.join(DATA, "economy", f"{iso}_FULL_2026_10.json"), "w"), ensure_ascii=False, indent=1)

    # ---------------- Armée
    bases, units = [], []
    big = [c for c in cities if c["rank"] <= 2][:8] or cities[:8]
    coastal_cities = [c for c in cities if any(x["code"] == c["department"] and "coastal" in x["tags"] for x in deps)] or big
    for i, c in enumerate(big):
        bases.append({"id": f"{iso.lower()}_base_{i}", "name": f"Garnison de {c['name']}", "branch": "Terre", "lon": c["lon"], "lat": c["lat"], "department": c["department"], "capacityUnits": 6})
    for i, c in enumerate(coastal_cities[:3]):
        bases.append({"id": f"{iso.lower()}_naval_{i}", "name": f"Base navale de {c['name']}", "branch": "Marine", "lon": c["lon"], "lat": c["lat"], "department": c["department"], "capacityUnits": 6})
    land = [b for b in bases if b["branch"] == "Terre"]; naval = [b for b in bases if b["branch"] == "Marine"] or land
    for i, (uname, utype) in enumerate(cfg["units"]):
        sea = utype in ("SURFACE_GROUP", "CARRIER_GROUP", "SSBN_FORCE")
        base = (naval if sea else land)[i % len(naval if sea else land)]
        units.append({"id": f"u_{iso.lower()}_{i}", "name": uname, "branch": "Marine" if sea else ("Air" if utype.endswith("WING") else "Terre"), "type": utype, "baseId": base["id"],
                      "personnel": 6000, "equipment": {}, "readiness": 0.7, "morale": 0.65, "ammunition": 0.55, "fuel": 0.6})
    json.dump({"budgetSpendingItem": "defense", "bases": bases, "units": units}, open(os.path.join(DATA, "military", f"{iso}_2026_10.json"), "w"), ensure_ascii=False, indent=1)

    # ---------------- Énergie et transports
    def dep_of(lon, lat):
        return min(deps, key=lambda x: (geo_dep(geo, x["code"])["lon"] - lon) ** 2 + (geo_dep(geo, x["code"])["lat"] - lat) ** 2)["code"]
    items = []
    for pid, pname, lon, lat, mw in cfg["nuclear"]:
        items.append({"id": f"{iso.lower()}_{pid}", "type": "NUCLEAR_PLANT", "name": pname, "lon": lon, "lat": lat, "department": dep_of(lon, lat), "employees": 1200,
                      "maintenanceCostMillions": int(mw / 25), "initialCondition": 0.7, "commissionedYear": 1985, "capacityMW": mw, "capacityFactor": 0.8,
                      "description": "Réacteurs nucléaires. L'état des installations conditionne leur disponibilité et le risque d'incident."})
    for pid, ptype, pname, lon, lat, mw in cfg["energy"]["plants"]:
        item = {"id": f"{iso.lower()}_{pid}", "type": ptype, "name": pname, "lon": lon, "lat": lat, "department": dep_of(lon, lat), "employees": 600,
                "maintenanceCostMillions": max(20, int(mw / 40)), "initialCondition": 0.72, "commissionedYear": 1990, "description": "Installation énergétique majeure."}
        if mw: item.update({"capacityMW": mw, "capacityFactor": 0.45 if ptype == "GAS_PLANT" else 0.4})
        items.append(item)
    gen = [{"id": i, "label": l, "capacityMW": mw, "capacityFactor": cf} for i, l, mw, cf in cfg["energy"]["mix"]]
    gen += [{"id": "new_nuclear", "label": "Nouveaux réacteurs", "capacityMW": 0, "capacityFactor": 0.82}, {"id": "new_renewables", "label": "Nouveaux parcs renouvelables", "capacityMW": 0, "capacityFactor": 0.2}]
    json.dump({"electricityDemandTWh": cfg["energy"]["demand"], "items": items, "nationalGeneration": gen}, open(os.path.join(DATA, "infrastructure", f"{iso}_energy.json"), "w"), ensure_ascii=False, indent=1)
    titems = []
    for pid, pname, lon, lat in cfg["ports"]:
        titems.append({"id": f"{iso.lower()}_{pid}", "type": "PORT", "name": pname, "lon": lon, "lat": lat, "department": dep_of(lon, lat), "employees": 3000, "maintenanceCostMillions": 60, "initialCondition": 0.75, "commissionedYear": 1960, "description": "Grand port de commerce."})
    for pid, pname, lon, lat in cfg["airports"]:
        titems.append({"id": f"{iso.lower()}_{pid}", "type": "AIRPORT", "name": pname, "lon": lon, "lat": lat, "department": dep_of(lon, lat), "employees": 5000, "maintenanceCostMillions": 80, "initialCondition": 0.75, "commissionedYear": 1970, "description": "Aéroport international."})
    by_name = {}
    for c in cities: by_name.setdefault(c["name"], c["id"])
    networks = []
    for nid, kind, nname, names in cfg["lines"]:
        ids = [by_name[n] for n in names if n in by_name]
        if len(ids) >= 2: networks.append({"id": f"{iso.lower()}_{nid}", "kind": kind, "name": nname, "cityIds": ids})
    json.dump({"items": titems, "networks": networks}, open(os.path.join(DATA, "infrastructure", f"{iso}_transport.json"), "w"), ensure_ascii=False, indent=1)

    # ---------------- Pays (simulation complète)
    d = dict(d0)
    d.update({"detail": "FULL", "capitalCityId": capital["id"], "economy": f"economy/{iso}_FULL_2026_10.json",
              "territory": f"countries/{iso}/territory.json", "government": f"countries/{iso}/government.json", "socialGroups": "countries/FRA/social_groups.json",
              "elections": f"countries/{iso}/elections.json", "energy": f"infrastructure/{iso}_energy.json", "transport": f"infrastructure/{iso}_transport.json",
              "military": f"military/{iso}_2026_10.json", "measures": "countries/FRA/measures.json", "cabinet": "countries/FRA/cabinet.json", "agenda": "countries/FRA/agenda.json",
              "sectors": "countries/FRA/sectors.json", "careers": "countries/FRA/careers.json", "laws": "countries/FRA/laws.json", "actors": "countries/FRA/actors.json",
              "fiscal": "countries/FRA/fiscal.json", "legislation": "countries/FRA/legislation.json", "reforms": "countries/FRA/reforms.json", "promises": "countries/FRA/promises.json",
              "localActions": "countries/FRA/local_actions.json", "nationalActions": "countries/FRA/national_actions.json", "demography": cfg["demography"],
              "geo": iso, "electionLabel": cfg["electionLabel"], "lexicon": lexicon(iso, cfg, d0)})
    d["strategic"] = dict(d0["strategic"]); d["strategic"]["forces"] = {"land": cfg["forces"][0], "air": cfg["forces"][1], "sea": cfg["forces"][2]}
    json.dump(d, open(os.path.join(DATA, "countries", iso, "country.json"), "w"), ensure_ascii=False, indent=1)
    print(iso, len(deps), "subdivisions,", len(regions), "régions,", len(cities), "villes,", len(units), "unités,", len(items), "installations")


def geo_dep(geo, code):
    return next(d for d in geo["departments"] if d["code"] == code)


def feminize(t):
    return (t.replace("Délégué", "Déléguée").replace("Président", "Présidente").replace("Coordinateur", "Coordinatrice").replace("Administrateur", "Administratrice")
            .replace("Gouverneur", "Gouverneure").replace("Ministre-président", "Ministre-présidente").replace("Maire métropolitain", "Maire métropolitaine"))


MINISTRY_TITLES = {
    "DEU": {"pm": ("Vice-chancelier", "Vice-chancellerie"), "economy": ("Ministre fédéral des Finances", "Finances"), "interior": ("Ministre fédéral de l'Intérieur", "Intérieur"),
            "armed_forces": ("Ministre fédéral de la Défense", "Défense"), "foreign": ("Ministre fédéral des Affaires étrangères", "Affaires étrangères"),
            "health": ("Ministre fédéral de la Santé", "Santé"), "education": ("Ministre fédéral de l'Éducation", "Éducation"), "labour": ("Ministre fédéral du Travail", "Travail"),
            "justice": ("Ministre fédéral de la Justice", "Justice"), "ecology": ("Ministre fédéral du Climat", "Climat"), "transport": ("Ministre fédéral des Transports", "Transports"),
            "agriculture": ("Ministre fédéral de l'Agriculture", "Agriculture")},
    "GBR": {"pm": ("Vice-Premier ministre", "Cabinet Office"), "economy": ("Chancelier de l'Échiquier", "Trésor"), "interior": ("Ministre de l'Intérieur (Home Secretary)", "Home Office"),
            "armed_forces": ("Ministre de la Défense", "Défense"), "foreign": ("Ministre des Affaires étrangères", "Foreign Office"), "health": ("Ministre de la Santé (NHS)", "Santé"),
            "education": ("Ministre de l'Éducation", "Éducation"), "labour": ("Ministre du Travail et des Pensions", "Travail"), "justice": ("Lord Chancelier, ministre de la Justice", "Justice"),
            "ecology": ("Ministre de l'Énergie et de la Neutralité carbone", "Énergie"), "transport": ("Ministre des Transports", "Transports"), "agriculture": ("Ministre de l'Environnement et de l'Agriculture", "Agriculture")},
    "ITA": {"pm": ("Vice-président du Conseil", "Vice-présidence"), "economy": ("Ministre de l'Économie et des Finances", "Économie"), "interior": ("Ministre de l'Intérieur", "Viminale"),
            "armed_forces": ("Ministre de la Défense", "Défense"), "foreign": ("Ministre des Affaires étrangères", "Farnesina"), "health": ("Ministre de la Santé", "Santé"),
            "education": ("Ministre de l'Éducation et du Mérite", "Éducation"), "labour": ("Ministre du Travail", "Travail"), "justice": ("Ministre de la Justice", "Justice"),
            "ecology": ("Ministre de l'Environnement et de la Sécurité énergétique", "Environnement"), "transport": ("Ministre des Infrastructures et des Transports", "Transports"),
            "agriculture": ("Ministre de l'Agriculture et de la Souveraineté alimentaire", "Agriculture")},
    "ESP": {"pm": ("Vice-président du gouvernement", "Vice-présidence"), "economy": ("Ministre de l'Économie et des Finances", "Économie"), "interior": ("Ministre de l'Intérieur", "Intérieur"),
            "armed_forces": ("Ministre de la Défense", "Défense"), "foreign": ("Ministre des Affaires étrangères", "Affaires étrangères"), "health": ("Ministre de la Santé", "Santé"),
            "education": ("Ministre de l'Éducation", "Éducation"), "labour": ("Ministre du Travail", "Travail"), "justice": ("Ministre de la Justice", "Justice"),
            "ecology": ("Ministre de la Transition écologique", "Écologie"), "transport": ("Ministre des Transports", "Transports"), "agriculture": ("Ministre de l'Agriculture", "Agriculture")},
    "USA": {"pm": ("Vice-président", "Vice-présidence"), "economy": ("Secrétaire au Trésor", "Trésor"), "interior": ("Secrétaire à la Sécurité intérieure", "Sécurité intérieure"),
            "armed_forces": ("Secrétaire à la Défense", "Pentagone"), "foreign": ("Secrétaire d'État", "Département d'État"), "health": ("Secrétaire à la Santé", "Santé"),
            "education": ("Secrétaire à l'Éducation", "Éducation"), "labour": ("Secrétaire au Travail", "Travail"), "justice": ("Procureur général", "Justice"),
            "ecology": ("Secrétaire à l'Énergie", "Énergie"), "transport": ("Secrétaire aux Transports", "Transports"), "agriculture": ("Secrétaire à l'Agriculture", "Agriculture")},
}


def bare(s):
    """« l'Agence… » → « Agence… », « le Bürgergeld » → « Bürgergeld »."""
    for a in ("l'", "le ", "la ", "les "):
        if s.startswith(a): return s[len(a):]
    return s


def lexicon(iso, cfg, d0):
    """Remplacements (expression régulière → texte) appliqués aux textes pensés pour la France."""
    the = {"l'": "l'", "le": "le ", "la": "la ", "les": "les "}[d0["article"]] + d0["name"]
    of = {"l'": "de l'", "le": "du ", "la": "de la ", "les": "des "}[d0["article"]] + d0["name"]
    to = {"l'": "à l'", "le": "au ", "la": "à la ", "les": "aux "}[d0["article"]] + d0["name"]
    inside = ({"le": "au ", "les": "aux "}.get(d0["article"], "en ")) + d0["name"]
    cap = lambda s: s[0].upper() + s[1:]
    people_m, people_f, adj_m, adj_f, adj_mp, adj_fp = cfg["demonym"]
    seat, at_seat, of_seat, seat_bare = cfg["seat"]
    lt, lt_cap, lt_f = cfg["leaderTitle"]
    rules = [
        # Institutions et lieux de pouvoir
        (r"\bFrance Travail\b", bare(cfg["jobs"])),
        (r"\bBanque de France\b", cfg["bank"]), (r"\bAssemblée nationale\b", cfg["assembly"]), (r"\bConseil constitutionnel\b", cfg["court"]),
        (r"\bSNCF\b", cfg["rail"]), (r"\bEDF\b", cfg["power"]), (r"\bCAC 40\b", cfg["index"]), (r"\bRSA\b", bare(cfg["minIncome"])),
        (r"\bSMIC\b", "salaire minimum"), (r"\bBercy\b", "le ministère des Finances"), (r"\bMatignon\b", "le gouvernement"),
        (r"\b(article |l'article )?49\.3\b", "procédure d'urgence"), (r"\b[Aa]rticle 16\b", "pouvoirs d'exception"),
        (r"\bà l'Élysée\b", at_seat), (r"\bde l'Élysée\b", of_seat), (r"\bl'Élysée\b", seat), (r"\bÉlysée\b", seat_bare),
        (r"\bBureau du président\b", f"Bureau du {lt}" if not lt[0].isupper() else f"Bureau du {lt}"),
        (r"\b[Pp]résident de la République\b", lt),
        (r"\bles Bleus\b", cfg["team"]), (r"\bLes Bleus\b", cap(cfg["team"])), (r"\ble XV de France\b", cfg["rugby"]),
        (r"\bMarine nationale\b", "Marine"), (r"\bArmée de l'air et de l'espace\b", "Armée de l'air"),
        (r"\bDGSE\b", "le renseignement extérieur"), (r"\bDGSI\b", "le renseignement intérieur"),
        (r"\b[àa] Paris\b", f"à {cfg['capital']}"), (r"\bde Paris\b", f"de {cfg['capital']}"),
        # Le pays et ses habitants
        (r"\bde la France\b", of), (r"\bà la France\b", to), (r"\bÀ la France\b", cap(to)), (r"\bLa France\b", cap(the)), (r"\bla France\b", the),
        (r"\ben France\b", inside), (r"\bEn France\b", cap(inside)), (r"\bFrance\b", d0["name"]),
        (r"\bFrançaises\b", people_f), (r"\bFrançais\b", people_m),
        (r"\bfrançaises\b", adj_fp), (r"\bfrançaise\b", adj_f), (r"\bfrançais\b", adj_m),
    ]
    if cfg["electionLabel"] != "Présidentielle":
        rules += [(r"\bPrésidentielle\b", cfg["electionLabel"]), (r"\bprésidentielle\b", cfg["electionLabel"].lower())]
    if lt != "président":
        not_others = r"(?! (de la Commission|du Conseil européen|du conseil|de région|de la région|de l'Assemblée|du Sénat|chinois|américain|russe|turc|ukrainien|égyptien|brésilien|algérien))"
        rules += [(r"\bLe président\b" + not_others, "Le " + lt), (r"\ble président\b" + not_others, "le " + lt),
                  (r"\bdu président\b(?! (de la Commission|du conseil|de région))", "du " + lt), (r"\bau président\b(?! (de la Commission|du conseil|de région))", "au " + lt)]
    return [[p, r] for p, r in rules]


if __name__ == "__main__":
    for iso, cfg in COUNTRIES.items():
        build(iso, cfg)
    snap_path = os.path.join(DATA, "world_snapshots", "WORLD_SNAPSHOT_2026_10.json")
    snap = json.load(open(snap_path))
    snap["playableCountries"] = ["FRA"] + list(COUNTRIES.keys())
    json.dump(snap, open(snap_path, "w"), ensure_ascii=False, indent=1)
