"""Une carte de France plus profonde : villes, autoroutes, LGV et fleuves.

- Ajoute à assets/data/countries/FRA/territory.json les préfectures qui manquaient et des
  villes importantes (rang 3 : ville moyenne, rang 4 : petite ville visible au zoom local).
- Réécrit les réseaux de assets/data/infrastructure/FRA_transport.json : autoroutes et voies
  rapides détaillées (étape par étape), LGV, grands fleuves (tracés de ville en ville).
Idempotent : on peut le relancer, les villes existantes ne sont pas dupliquées.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")

# id | nom | département | lon | lat | habitants | aire urbaine | rang
CITIES = """
bourg_en_bresse|Bourg-en-Bresse|01|5.226|46.205|41000|125000|3
saint_quentin|Saint-Quentin|02|3.287|49.848|53000|110000|3
laon|Laon|02|3.624|49.564|25000|50000|4
montlucon|Montluçon|03|2.603|46.340|34000|80000|3
moulins|Moulins|03|3.333|46.566|19000|60000|4
vichy|Vichy|03|3.426|46.128|25000|80000|4
digne|Digne-les-Bains|04|6.236|44.092|16000|30000|4
manosque|Manosque|04|5.786|43.829|22000|50000|4
gap|Gap|05|6.079|44.559|40000|60000|3
briancon|Briançon|05|6.643|44.897|11000|20000|4
privas|Privas|07|4.599|44.735|8000|20000|4
annonay|Annonay|07|4.671|45.240|16000|30000|4
charleville|Charleville-Mézières|08|4.721|49.773|46000|100000|3
sedan|Sedan|08|4.938|49.702|16000|30000|4
foix|Foix|09|1.607|42.965|9000|20000|4
pamiers|Pamiers|09|1.611|43.116|15000|25000|4
rodez|Rodez|12|2.575|44.350|25000|85000|3
millau|Millau|12|3.078|44.098|22000|30000|4
aurillac|Aurillac|15|2.445|44.926|26000|60000|3
angouleme|Angoulême|16|0.156|45.648|42000|180000|3
cognac|Cognac|16|-0.329|45.696|18000|40000|4
saint_brieuc|Saint-Brieuc|22|-2.765|48.514|44000|150000|3
lannion|Lannion|22|-3.459|48.732|20000|40000|4
gueret|Guéret|23|1.871|46.171|13000|25000|4
perigueux|Périgueux|24|0.722|45.184|30000|100000|3
bergerac|Bergerac|24|0.483|44.853|27000|55000|4
sarlat|Sarlat-la-Canéda|24|1.216|44.890|9000|15000|4
evreux|Évreux|27|1.151|49.027|47000|110000|3
vernon|Vernon|27|1.484|49.093|24000|40000|4
chartres|Chartres|28|1.489|48.446|39000|140000|3
dreux|Dreux|28|1.366|48.737|31000|60000|4
auch|Auch|32|0.585|43.646|22000|40000|4
chateauroux|Châteauroux|36|1.691|46.811|43000|90000|3
lons|Lons-le-Saunier|39|5.554|46.675|17000|40000|4
dole|Dole|39|5.490|47.092|24000|50000|4
mont_de_marsan|Mont-de-Marsan|40|-0.500|43.893|30000|55000|3
dax|Dax|40|-1.053|43.710|21000|55000|4
blois|Blois|41|1.335|47.586|46000|120000|3
le_puy|Le Puy-en-Velay|43|3.885|45.043|19000|60000|4
cahors|Cahors|46|1.441|44.448|20000|40000|4
figeac|Figeac|46|2.031|44.609|10000|20000|4
agen|Agen|47|0.616|44.203|33000|110000|3
marmande|Marmande|47|0.165|44.500|18000|30000|4
mende|Mende|48|3.500|44.518|12000|20000|4
chaumont|Chaumont|52|5.139|48.111|22000|40000|4
saint_dizier|Saint-Dizier|52|4.950|48.638|23000|40000|4
laval|Laval|53|-0.770|48.073|50000|110000|3
bar_le_duc|Bar-le-Duc|55|5.160|48.772|15000|30000|4
verdun|Verdun|55|5.383|49.160|17000|30000|4
nevers|Nevers|58|3.159|46.990|33000|100000|3
beauvais|Beauvais|60|2.081|49.430|56000|110000|3
compiegne|Compiègne|60|2.826|49.418|40000|90000|3
creil|Creil|60|2.476|49.259|36000|100000|4
alencon|Alençon|61|0.093|48.432|26000|60000|4
flers|Flers|61|-0.570|48.749|15000|30000|4
tarbes|Tarbes|65|0.078|43.233|42000|115000|3
lourdes|Lourdes|65|-0.047|43.095|13000|20000|4
vesoul|Vesoul|70|6.155|47.622|15000|35000|4
macon|Mâcon|71|4.832|46.307|34000|90000|3
chalon|Chalon-sur-Saône|71|4.853|46.781|45000|130000|3
le_creusot|Le Creusot|71|4.424|46.807|21000|50000|4
melun|Melun|77|2.655|48.540|41000|130000|3
meaux|Meaux|77|2.878|48.960|55000|90000|3
fontainebleau|Fontainebleau|77|2.701|48.405|15000|40000|4
versailles|Versailles|78|2.130|48.805|85000|400000|3
mantes|Mantes-la-Jolie|78|1.717|48.991|44000|90000|4
niort|Niort|79|-0.465|46.324|59000|150000|3
albi|Albi|81|2.148|43.928|49000|100000|3
castres|Castres|81|2.240|43.606|42000|60000|4
montauban|Montauban|82|1.355|44.018|61000|110000|3
la_roche_sur_yon|La Roche-sur-Yon|85|-1.427|46.670|55000|115000|3
les_sables|Les Sables-d'Olonne|85|-1.783|46.497|45000|60000|4
epinal|Épinal|88|6.450|48.173|32000|80000|3
saint_die|Saint-Dié-des-Vosges|88|6.950|48.285|20000|40000|4
auxerre|Auxerre|89|3.567|47.798|35000|90000|3
sens|Sens|89|3.284|48.197|27000|60000|4
belfort|Belfort|90|6.863|47.640|47000|100000|3
evry|Évry-Courcouronnes|91|2.441|48.629|67000|200000|3
massy|Massy|91|2.274|48.730|50000|100000|4
nanterre|Nanterre|92|2.206|48.892|96000|300000|3
boulogne_billancourt|Boulogne-Billancourt|92|2.240|48.835|120000|300000|3
saint_denis|Saint-Denis|93|2.358|48.936|113000|300000|3
montreuil|Montreuil|93|2.441|48.861|111000|300000|3
bobigny|Bobigny|93|2.440|48.910|55000|300000|4
creteil|Créteil|94|2.455|48.790|92000|300000|3
vitry|Vitry-sur-Seine|94|2.392|48.787|95000|300000|4
cergy|Cergy|95|2.066|49.036|66000|200000|3
argenteuil|Argenteuil|95|2.247|48.947|111000|300000|3
arras|Arras|62|2.781|50.291|41000|125000|3
lens|Lens|62|2.832|50.432|31000|500000|3
boulogne_sur_mer|Boulogne-sur-Mer|62|1.614|50.726|41000|90000|4
valenciennes|Valenciennes|59|3.523|50.358|43000|350000|3
roubaix|Roubaix|59|3.174|50.690|98000|500000|3
tourcoing|Tourcoing|59|3.161|50.723|98000|500000|4
maubeuge|Maubeuge|59|3.973|50.278|29000|100000|4
cambrai|Cambrai|59|3.235|50.176|32000|60000|4
abbeville|Abbeville|80|1.833|50.105|23000|40000|4
chalons|Châlons-en-Champagne|51|4.363|48.957|44000|80000|3
epernay|Épernay|51|3.956|49.040|22000|40000|4
thionville|Thionville|57|6.168|49.358|41000|140000|3
forbach|Forbach|57|6.900|49.188|21000|80000|4
colmar|Colmar|68|7.358|48.079|68000|130000|3
haguenau|Haguenau|67|7.790|48.816|35000|60000|4
saint_malo|Saint-Malo|35|-2.008|48.649|46000|90000|3
vannes|Vannes|56|-2.760|47.658|54000|150000|3
quimper|Quimper|29|-4.097|47.996|63000|125000|3
morlaix|Morlaix|29|-3.828|48.578|15000|30000|4
cholet|Cholet|49|-0.879|47.060|54000|80000|4
saumur|Saumur|49|-0.077|47.260|26000|40000|4
chatellerault|Châtellerault|86|0.546|46.817|31000|50000|4
rochefort|Rochefort|17|-0.959|45.937|24000|40000|4
saintes|Saintes|17|-0.633|45.746|25000|50000|4
arcachon|Arcachon|33|-1.168|44.658|11000|60000|4
libourne|Libourne|33|-0.243|44.915|25000|60000|4
biarritz|Biarritz|64|-1.559|43.483|25000|300000|4
beziers|Béziers|34|3.215|43.344|79000|170000|3
sete|Sète|34|3.697|43.403|44000|90000|4
carcassonne|Carcassonne|11|2.353|43.213|46000|100000|3
ales|Alès|30|4.081|44.126|43000|90000|4
arles|Arles|13|4.631|43.677|52000|80000|3
salon|Salon-de-Provence|13|5.097|43.640|46000|80000|4
martigues|Martigues|13|5.054|43.405|49000|80000|4
fos|Fos-sur-Mer|13|4.944|43.438|16000|80000|4
cannes|Cannes|06|7.017|43.552|74000|400000|3
antibes|Antibes|06|7.125|43.581|73000|400000|4
menton|Menton|06|7.497|43.775|30000|70000|4
frejus|Fréjus|83|6.737|43.433|54000|100000|4
draguignan|Draguignan|83|6.464|43.537|40000|60000|4
hyeres|Hyères|83|6.128|43.120|50000|600000|4
carpentras|Carpentras|84|5.048|44.055|29000|60000|4
orange|Orange|84|4.808|44.138|29000|50000|4
montelimar|Montélimar|26|4.750|44.558|40000|70000|4
vienne|Vienne|38|4.874|45.525|30000|70000|4
bourgoin|Bourgoin-Jallieu|38|5.274|45.586|29000|60000|4
villeurbanne|Villeurbanne|69|4.880|45.771|155000|2300000|3
villefranche|Villefranche-sur-Saône|69|4.718|45.990|37000|70000|4
roanne|Roanne|42|4.068|46.036|34000|100000|4
thonon|Thonon-les-Bains|74|6.479|46.371|35000|80000|4
chamonix|Chamonix-Mont-Blanc|74|6.869|45.924|9000|15000|4
albertville|Albertville|73|6.392|45.676|19000|40000|4
porto_vecchio|Porto-Vecchio|2A|9.279|41.591|12000|20000|4
corte|Corte|2B|9.150|42.306|7000|10000|4
calvi|Calvi|2B|8.757|42.567|6000|10000|4
dieppe|Dieppe|76|1.078|49.922|29000|50000|4
lisieux|Lisieux|14|0.226|49.146|20000|40000|4
saint_lo|Saint-Lô|50|-1.090|49.116|19000|40000|4
vierzon|Vierzon|18|2.068|47.222|26000|40000|4
montargis|Montargis|45|2.733|47.997|14000|60000|4
beaune|Beaune|21|4.840|47.024|20000|30000|4
thiers|Thiers|63|3.548|45.857|11000|20000|4
tulle|Tulle|19|1.770|45.267|15000|30000|4
saint_gaudens|Saint-Gaudens|31|0.723|43.108|11000|20000|4
"""

M, R, L = "MOTORWAY", "RAIL_HIGH_SPEED", "RIVER"
NETWORKS = [
    # LGV
    ("lgv_sud_est", R, "LGV Sud-Est", ["paris", "dijon", "macon", "lyon"]),
    ("lgv_mediterranee", R, "LGV Méditerranée", ["lyon", "valence", "avignon", "aix", "marseille"]),
    ("lgv_mediterranee_ouest", R, "Contournement Nîmes-Montpellier", ["avignon", "nimes", "montpellier"]),
    ("lgv_atlantique", R, "LGV Atlantique et Sud Europe Atlantique", ["paris", "tours", "poitiers", "angouleme", "bordeaux"]),
    ("lgv_bretagne", R, "LGV Bretagne-Pays de la Loire", ["paris", "le_mans", "laval", "rennes"]),
    ("lgv_nord", R, "LGV Nord", ["paris", "arras", "lille", "calais"]),
    ("lgv_est", R, "LGV Est européenne", ["paris", "reims", "metz", "strasbourg"]),
    ("lgv_rhin_rhone", R, "LGV Rhin-Rhône", ["dijon", "besancon", "belfort", "mulhouse"]),
    ("lgv_interconnexion", R, "Interconnexion Île-de-France", ["lille", "creil", "saint_denis", "meaux", "melun", "paris"]),
    # Autoroutes et voies rapides
    ("a1", M, "A1 Paris-Lille", ["paris", "saint_denis", "compiegne", "arras", "lille"]),
    ("a2", M, "A2 vers Bruxelles", ["arras", "cambrai", "valenciennes"]),
    ("a4", M, "A4 Paris-Strasbourg", ["paris", "meaux", "reims", "verdun", "metz", "strasbourg"]),
    ("a5", M, "A5 Paris-Troyes-Langres", ["paris", "melun", "troyes", "chaumont"]),
    ("a6_a7", M, "A6 et A7 (autoroute du Soleil)", ["paris", "evry", "auxerre", "beaune", "chalon", "macon", "villefranche", "lyon", "vienne", "valence", "montelimar", "orange", "avignon", "salon", "marseille"]),
    ("a8", M, "A8 La Provençale", ["aix", "frejus", "cannes", "antibes", "nice", "menton"]),
    ("a9", M, "A9 La Languedocienne", ["orange", "nimes", "montpellier", "beziers", "narbonne", "perpignan"]),
    ("a10", M, "A10 L'Aquitaine", ["paris", "orleans", "blois", "tours", "chatellerault", "poitiers", "niort", "saintes", "bordeaux"]),
    ("a11", M, "A11 L'Océane", ["paris", "chartres", "le_mans", "angers", "nantes"]),
    ("a13_a84", M, "A13 et A84 (autoroute de Normandie)", ["paris", "versailles", "mantes", "rouen", "lisieux", "caen", "rennes"]),
    ("a16", M, "A16 L'Européenne", ["paris", "beauvais", "amiens", "abbeville", "boulogne_sur_mer", "calais", "dunkerque"]),
    ("a20", M, "A20 L'Occitane", ["vierzon", "chateauroux", "limoges", "brive", "cahors", "montauban", "toulouse"]),
    ("a25", M, "A25 Lille-Dunkerque", ["lille", "dunkerque"]),
    ("a26", M, "A26 Autoroute des Anglais", ["calais", "arras", "saint_quentin", "laon", "reims", "chalons", "troyes"]),
    ("a28", M, "A28 Rouen-Tours", ["abbeville", "rouen", "alencon", "le_mans", "tours"]),
    ("a29", M, "A29 Le Havre-Saint-Quentin", ["le_havre", "amiens", "saint_quentin"]),
    ("a31", M, "A31 Beaune-Luxembourg", ["beaune", "dijon", "nancy", "metz", "thionville"]),
    ("a35", M, "A35 Alsace", ["haguenau", "strasbourg", "colmar", "mulhouse"]),
    ("a36", M, "A36 La Comtoise", ["beaune", "dole", "besancon", "belfort", "mulhouse"]),
    ("a39", M, "A39 Dijon-Bourg", ["dijon", "dole", "lons", "bourg_en_bresse"]),
    ("a40", M, "A40 Autoroute des Titans", ["macon", "bourg_en_bresse", "annecy"]),
    ("a41_a43", M, "A41 et A43 (Alpes)", ["lyon", "bourgoin", "chambery", "annecy"]),
    ("a430", M, "A430 Tarentaise", ["chambery", "albertville"]),
    ("a48_a49", M, "A48 et A49 (Grenoble)", ["lyon", "grenoble", "valence"]),
    ("a50_a57", M, "A50 et A57 (Var)", ["marseille", "toulon", "frejus"]),
    ("a51", M, "A51 Val de Durance", ["aix", "manosque", "gap"]),
    ("a54_a55", M, "A54 et A55 (Camargue, étang de Berre)", ["nimes", "arles", "salon", "martigues", "fos"]),
    ("a61_a62", M, "A61 et A62 (autoroute des Deux Mers)", ["bordeaux", "marmande", "agen", "montauban", "toulouse", "carcassonne", "narbonne"]),
    ("a63", M, "A63 Côte basque", ["bordeaux", "dax", "bayonne", "biarritz"]),
    ("a64", M, "A64 La Pyrénéenne", ["biarritz", "bayonne", "pau", "tarbes", "saint_gaudens", "toulouse"]),
    ("a65", M, "A65 Gascogne", ["bordeaux", "mont_de_marsan", "pau"]),
    ("a66", M, "A66 Ariège", ["toulouse", "pamiers", "foix"]),
    ("a68", M, "A68 Toulouse-Albi", ["toulouse", "albi"]),
    ("a71", M, "A71 L'Arverne", ["orleans", "vierzon", "bourges", "montlucon", "clermont"]),
    ("a72_a47", M, "A72 et A47 (Forez)", ["clermont", "thiers", "saint_etienne", "lyon"]),
    ("a75", M, "A75 La Méridienne", ["clermont", "millau", "beziers"]),
    ("a77", M, "A77 Paris-Nevers", ["paris", "montargis", "nevers"]),
    ("a81", M, "A81 Le Mans-Rennes", ["le_mans", "laval", "rennes"]),
    ("a83", M, "A83 Nantes-Niort", ["nantes", "niort"]),
    ("a85", M, "A85 Angers-Vierzon", ["angers", "saumur", "tours", "vierzon"]),
    ("a87", M, "A87 Angers-Les Sables", ["angers", "cholet", "la_roche_sur_yon", "les_sables"]),
    ("a89", M, "A89 La Transeuropéenne", ["bordeaux", "libourne", "perigueux", "brive", "tulle", "clermont", "thiers", "roanne", "lyon"]),
    ("n12", M, "N12 Rennes-Brest (voie express)", ["rennes", "saint_brieuc", "morlaix", "brest"]),
    ("n165", M, "N165 Nantes-Brest (voie express)", ["nantes", "vannes", "lorient", "quimper", "brest"]),
    ("n137", M, "N137 Rennes-Saint-Malo", ["rennes", "saint_malo"]),
    ("a10_a837", M, "A837 La Rochelle-Rochefort-Saintes", ["la_rochelle", "rochefort", "saintes"]),
    # Grands fleuves (tracés de ville en ville)
    ("seine", L, "La Seine", ["troyes", "melun", "evry", "paris", "argenteuil", "mantes", "vernon", "rouen", "le_havre"]),
    ("loire", L, "La Loire", ["le_puy", "roanne", "nevers", "orleans", "blois", "tours", "saumur", "angers", "nantes", "saint_nazaire"]),
    ("rhone", L, "Le Rhône", ["lyon", "vienne", "valence", "montelimar", "orange", "avignon", "arles"]),
    ("garonne", L, "La Garonne", ["saint_gaudens", "toulouse", "agen", "marmande", "bordeaux"]),
    ("rhin", L, "Le Rhin", ["mulhouse", "colmar", "strasbourg", "haguenau"]),
    ("moselle", L, "La Moselle", ["epinal", "nancy", "metz", "thionville"]),
    ("marne", L, "La Marne", ["chaumont", "saint_dizier", "chalons", "epernay", "meaux", "paris"]),
    ("dordogne", L, "La Dordogne", ["bergerac", "libourne", "bordeaux"]),
]


def dump(path, data):
    raw = open(path, encoding="utf-8").read()
    indent = 1 if raw.startswith('{\n "') else 2
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


terr_path = os.path.join(ROOT, "countries", "FRA", "territory.json")
terr = json.load(open(terr_path, encoding="utf-8"))
depts = {d["code"] for d in terr["departments"]}
have = {c["id"] for c in terr["cities"]}
added = 0
for line in CITIES.strip().splitlines():
    i, n, d, lon, lat, p, a, r = line.split("|")
    assert d in depts, (i, d)
    if i in have: continue
    terr["cities"].append({"id": i, "name": n, "department": d, "lon": float(lon), "lat": float(lat),
                           "population": int(p), "urbanAreaPopulation": int(a), "rank": int(r)})
    have.add(i); added += 1
dump(terr_path, terr)

tr_path = os.path.join(ROOT, "infrastructure", "FRA_transport.json")
tr = json.load(open(tr_path, encoding="utf-8"))
for nid, kind, name, ids in NETWORKS:
    for c in ids: assert c in have, (nid, c)
tr["networks"] = [{"id": i, "kind": k, "name": n, "cityIds": ids} for i, k, n, ids in NETWORKS]
dump(tr_path, tr)
print(f"{added} villes ajoutées ({len(terr['cities'])} au total), {len(NETWORKS)} réseaux")
