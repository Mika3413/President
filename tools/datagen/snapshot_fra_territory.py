"""Génère une partie des données du snapshot FRANCE 2026-10 (valeurs arrondies, sources publiques).
Exécuter depuis n'importe où : écrit dans assets/data/."""
# Article défini de chaque département (« le Finistère », « la Gironde », « l'Ain », « les Landes »).
ARTICLES = {"Ain": "l'", "Aisne": "l'", "Allier": "l'", "Alpes-de-Haute-Provence": "les", "Hautes-Alpes": "les", "Alpes-Maritimes": "les", "Ardèche": "l'", "Ardennes": "les", "Ariège": "l'", "Aube": "l'", "Aude": "l'", "Aveyron": "l'", "Bouches-du-Rhône": "les", "Calvados": "le", "Cantal": "le", "Charente": "la", "Charente-Maritime": "la", "Cher": "le", "Corrèze": "la", "Corse-du-Sud": "la", "Haute-Corse": "la", "Côte-d'Or": "la", "Côtes-d'Armor": "les", "Creuse": "la", "Dordogne": "la", "Doubs": "le", "Drôme": "la", "Eure": "l'", "Eure-et-Loir": "l'", "Finistère": "le", "Gard": "le", "Haute-Garonne": "la", "Gers": "le", "Gironde": "la", "Hérault": "l'", "Ille-et-Vilaine": "l'", "Indre": "l'", "Indre-et-Loire": "l'", "Isère": "l'", "Jura": "le", "Landes": "les", "Loir-et-Cher": "le", "Loire": "la", "Haute-Loire": "la", "Loire-Atlantique": "la", "Loiret": "le", "Lot": "le", "Lot-et-Garonne": "le", "Lozère": "la", "Maine-et-Loire": "le", "Manche": "la", "Marne": "la", "Haute-Marne": "la", "Mayenne": "la", "Meurthe-et-Moselle": "la", "Meuse": "la", "Morbihan": "le", "Moselle": "la", "Nièvre": "la", "Nord": "le", "Oise": "l'", "Orne": "l'", "Pas-de-Calais": "le", "Puy-de-Dôme": "le", "Pyrénées-Atlantiques": "les", "Hautes-Pyrénées": "les", "Pyrénées-Orientales": "les", "Bas-Rhin": "le", "Haut-Rhin": "le", "Rhône": "le", "Haute-Saône": "la", "Saône-et-Loire": "la", "Sarthe": "la", "Savoie": "la", "Haute-Savoie": "la", "Paris": "", "Seine-Maritime": "la", "Seine-et-Marne": "la", "Yvelines": "les", "Deux-Sèvres": "les", "Somme": "la", "Tarn": "le", "Tarn-et-Garonne": "le", "Var": "le", "Vaucluse": "le", "Vendée": "la", "Vienne": "la", "Haute-Vienne": "la", "Vosges": "les", "Yonne": "l'", "Territoire de Belfort": "le", "Essonne": "l'", "Hauts-de-Seine": "les", "Seine-Saint-Denis": "la", "Val-de-Marne": "le", "Val-d'Oise": "le"}
import os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data"))
import json
regions = [
 ("11","Île-de-France","paris",dict(unemployment=0.071,incomeIndex=1.08,urbanShare=0.85,seniorShare=0.16,politicalLeaning=-0.05)),
 ("24","Centre-Val de Loire","orleans",dict(unemployment=0.070,incomeIndex=0.97,urbanShare=0.45,seniorShare=0.24,politicalLeaning=0.15)),
 ("27","Bourgogne-Franche-Comté","dijon",dict(unemployment=0.064,incomeIndex=0.97,urbanShare=0.40,seniorShare=0.25,politicalLeaning=0.1)),
 ("28","Normandie","rouen",dict(unemployment=0.072,incomeIndex=0.96,urbanShare=0.45,seniorShare=0.24,politicalLeaning=0.05)),
 ("32","Hauts-de-France","lille",dict(unemployment=0.089,incomeIndex=0.90,urbanShare=0.55,seniorShare=0.19,politicalLeaning=0.2)),
 ("44","Grand Est","strasbourg",dict(unemployment=0.073,incomeIndex=0.98,urbanShare=0.50,seniorShare=0.22,politicalLeaning=0.25)),
 ("52","Pays de la Loire","nantes",dict(unemployment=0.059,incomeIndex=0.98,urbanShare=0.48,seniorShare=0.22,politicalLeaning=0.0)),
 ("53","Bretagne","rennes",dict(unemployment=0.059,incomeIndex=0.98,urbanShare=0.45,seniorShare=0.24,politicalLeaning=-0.15)),
 ("75","Nouvelle-Aquitaine","bordeaux",dict(unemployment=0.069,incomeIndex=0.96,urbanShare=0.42,seniorShare=0.26,politicalLeaning=-0.1)),
 ("76","Occitanie","toulouse",dict(unemployment=0.087,incomeIndex=0.94,urbanShare=0.48,seniorShare=0.24,politicalLeaning=-0.15)),
 ("84","Auvergne-Rhône-Alpes","lyon",dict(unemployment=0.065,incomeIndex=1.02,urbanShare=0.55,seniorShare=0.21,politicalLeaning=0.1)),
 ("93","Provence-Alpes-Côte d'Azur","marseille",dict(unemployment=0.080,incomeIndex=0.97,urbanShare=0.70,seniorShare=0.25,politicalLeaning=0.35)),
 ("94","Corse","ajaccio",dict(unemployment=0.063,incomeIndex=0.93,urbanShare=0.40,seniorShare=0.26,politicalLeaning=0.2)),
]
R={}
for r,ds in {"11":"75 77 78 91 92 93 94 95","24":"18 28 36 37 41 45","27":"21 25 39 58 70 71 89 90","28":"14 27 50 61 76","32":"02 59 60 62 80","44":"08 10 51 52 54 55 57 67 68 88","52":"44 49 53 72 85","53":"22 29 35 56","75":"16 17 19 23 24 33 40 47 64 79 86 87","76":"09 11 12 30 31 32 34 46 48 65 66 81 82","84":"01 03 07 15 26 38 42 43 63 69 73 74","93":"04 05 06 13 83 84","94":"2A 2B"}.items():
  for d in ds.split(): R[d]=r
pops = """01 Ain 657
02 Aisne 529
03 Allier 335
04 Alpes-de-Haute-Provence 166
05 Hautes-Alpes 141
06 Alpes-Maritimes 1098
07 Ardèche 330
08 Ardennes 270
09 Ariège 155
10 Aube 310
11 Aude 377
12 Aveyron 279
13 Bouches-du-Rhône 2056
14 Calvados 699
15 Cantal 144
16 Charente 352
17 Charente-Maritime 655
18 Cher 300
19 Corrèze 240
2A Corse-du-Sud 160
2B Haute-Corse 182
21 Côte-d'Or 535
22 Côtes-d'Armor 604
23 Creuse 115
24 Dordogne 413
25 Doubs 545
26 Drôme 520
27 Eure 600
28 Eure-et-Loir 431
29 Finistère 918
30 Gard 750
31 Haute-Garonne 1435
32 Gers 192
33 Gironde 1654
34 Hérault 1201
35 Ille-et-Vilaine 1100
36 Indre 217
37 Indre-et-Loire 612
38 Isère 1277
39 Jura 259
40 Landes 419
41 Loir-et-Cher 329
42 Loire 766
43 Haute-Loire 228
44 Loire-Atlantique 1460
45 Loiret 683
46 Lot 175
47 Lot-et-Garonne 332
48 Lozère 77
49 Maine-et-Loire 822
50 Manche 495
51 Marne 566
52 Haute-Marne 172
53 Mayenne 307
54 Meurthe-et-Moselle 732
55 Meuse 183
56 Morbihan 763
57 Moselle 1046
58 Nièvre 202
59 Nord 2611
60 Oise 830
61 Orne 278
62 Pas-de-Calais 1462
63 Puy-de-Dôme 668
64 Pyrénées-Atlantiques 688
65 Hautes-Pyrénées 229
66 Pyrénées-Orientales 482
67 Bas-Rhin 1150
68 Haut-Rhin 767
69 Rhône 1891
70 Haute-Saône 233
71 Saône-et-Loire 551
72 Sarthe 566
73 Savoie 439
74 Haute-Savoie 834
75 Paris 2133
76 Seine-Maritime 1255
77 Seine-et-Marne 1427
78 Yvelines 1450
79 Deux-Sèvres 375
80 Somme 570
81 Tarn 390
82 Tarn-et-Garonne 262
83 Var 1090
84 Vaucluse 562
85 Vendée 690
86 Vienne 439
87 Haute-Vienne 372
88 Vosges 362
89 Yonne 335
90 Territoire de Belfort 141
91 Essonne 1306
92 Hauts-de-Seine 1625
93 Seine-Saint-Denis 1655
94 Val-de-Marne 1407
95 Val-d'Oise 1250"""
unemp={"93":0.110,"66":0.115,"11":0.110,"30":0.105,"02":0.105,"59":0.095,"62":0.090,"34":0.096,"13":0.088,"75":0.062,"92":0.059,"78":0.060,"74":0.055,"15":0.045,"48":0.045,"53":0.050,"85":0.054,"08":0.093,"76":0.080,"2B":0.070,"95":0.082,"94":0.075,"91":0.066,"77":0.065,"01":0.055,"39":0.052,"35":0.055,"44":0.060,"12":0.050,"10":0.090,"80":0.088,"60":0.079}
income={"75":1.35,"92":1.35,"78":1.30,"91":1.10,"94":1.05,"77":1.05,"95":1.00,"93":0.75,"74":1.25,"01":1.10,"69":1.10,"67":1.05,"31":1.05,"44":1.03,"35":1.02,"33":1.02,"06":1.00,"13":0.95,"59":0.88,"62":0.85,"02":0.87,"11":0.85,"66":0.85,"23":0.85,"08":0.87,"30":0.90,"34":0.92,"38":1.05,"68":1.03,"2B":0.88,"48":0.92}
urban={"75":1.0,"92":1.0,"93":1.0,"94":1.0,"95":0.85,"91":0.8,"78":0.8,"77":0.65,"69":0.85,"13":0.9,"59":0.85,"06":0.85,"31":0.75,"33":0.7,"67":0.7,"44":0.65,"34":0.7,"38":0.6,"83":0.7,"62":0.6,"57":0.6,"76":0.6,"23":0.15,"48":0.15,"15":0.2,"32":0.2,"46":0.2,"12":0.25,"43":0.25,"04":0.3,"52":0.3,"55":0.3,"36":0.3,"58":0.3,"19":0.3,"09":0.3,"2B":0.4,"2A":0.45,"90":0.6,"68":0.6,"35":0.6}
senior={"23":0.31,"46":0.30,"24":0.29,"58":0.29,"03":0.28,"15":0.28,"19":0.28,"36":0.28,"32":0.28,"04":0.28,"12":0.27,"87":0.25,"75":0.17,"93":0.13,"95":0.14,"92":0.16,"91":0.15,"77":0.15,"78":0.16,"94":0.15,"31":0.17,"69":0.17,"35":0.18,"44":0.19,"59":0.17,"67":0.18,"74":0.18,"01":0.18,"38":0.19,"06":0.27,"83":0.28,"17":0.29,"66":0.27}
leaning={"75":-0.3,"93":-0.6,"94":-0.35,"92":0.2,"78":0.25,"13":0.2,"06":0.45,"83":0.5,"84":0.4,"30":0.2,"66":0.35,"11":0.25,"31":-0.35,"33":-0.2,"35":-0.25,"29":-0.25,"44":-0.15,"69":-0.05,"38":-0.15,"34":-0.1,"59":0.0,"62":0.3,"02":0.4,"60":0.25,"80":0.2,"08":0.35,"57":0.3,"67":0.1,"68":0.2,"87":-0.3,"19":-0.2,"23":-0.1,"2A":0.3,"2B":0.3,"09":-0.3,"65":-0.25,"81":-0.1,"82":0.1,"24":-0.05,"74":0.2,"85":0.2,"53":0.1}
HEALTH={"75":1.6,"13":1.25,"06":1.3,"34":1.25,"31":1.2,"69":1.2,"33":1.15,"67":1.15,"23":0.6,"36":0.6,"58":0.65,"18":0.65,"03":0.7,"27":0.6,"28":0.6,"02":0.7,"61":0.65,"52":0.65,"93":0.7,"95":0.75,"77":0.7,"89":0.65,"55":0.65,"72":0.75,"53":0.7,"01":0.75,"60":0.75,"45":0.8}
CRIME={"93":2.2,"13":1.7,"75":1.9,"94":1.5,"95":1.4,"69":1.4,"59":1.3,"31":1.25,"34":1.3,"06":1.3,"30":1.25,"91":1.2,"92":1.2,"77":1.1,"66":1.2,"2B":1.2,"2A":1.1,"48":0.4,"15":0.45,"23":0.5,"12":0.55,"43":0.55,"46":0.6,"32":0.6,"19":0.6,"53":0.6,"85":0.65,"05":0.7,"04":0.75}
INDUSTRY={"59":0.17,"62":0.18,"57":0.17,"68":0.22,"67":0.17,"69":0.15,"38":0.18,"76":0.17,"13":0.1,"44":0.16,"25":0.25,"90":0.27,"39":0.24,"01":0.25,"74":0.22,"42":0.2,"08":0.22,"88":0.21,"70":0.23,"71":0.19,"85":0.2,"49":0.18,"53":0.21,"61":0.2,"27":0.2,"60":0.17,"02":0.18,"80":0.16,"75":0.04,"92":0.07,"93":0.08,"06":0.06,"83":0.07,"2A":0.04,"2B":0.04,"34":0.06,"66":0.06,"30":0.09,"31":0.12,"33":0.1}
AGRI={"32":0.1,"47":0.09,"12":0.1,"15":0.11,"23":0.1,"51":0.09,"80":0.06,"02":0.06,"28":0.05,"45":0.04,"53":0.08,"85":0.07,"61":0.08,"14":0.05,"50":0.08,"22":0.08,"29":0.06,"56":0.07,"35":0.05,"10":0.07,"52":0.07,"55":0.07,"43":0.08,"48":0.11,"46":0.08,"24":0.07,"79":0.08,"86":0.06,"16":0.07,"17":0.06,"36":0.08,"18":0.06,"58":0.07,"89":0.06,"21":0.05,"71":0.06,"03":0.06,"63":0.04,"19":0.07,"87":0.05,"40":0.06,"64":0.05,"65":0.05,"09":0.07,"81":0.06,"82":0.08,"11":0.08,"84":0.06,"26":0.06,"07":0.06,"75":0.0,"92":0.0,"93":0.0,"94":0.0,"91":0.01,"95":0.01,"78":0.01,"77":0.02,"69":0.01,"13":0.01,"06":0.01,"59":0.01,"67":0.01,"31":0.02,"33":0.03}
POLL={"75":1.8,"92":1.7,"93":1.7,"94":1.6,"95":1.4,"91":1.3,"78":1.2,"77":1.1,"13":1.6,"69":1.5,"59":1.4,"62":1.2,"76":1.3,"57":1.2,"67":1.3,"68":1.1,"06":1.3,"38":1.2,"31":1.1,"44":1.1,"33":1.1,"23":0.5,"48":0.45,"15":0.5,"12":0.6,"46":0.6,"32":0.6,"19":0.6,"29":0.6,"22":0.6,"05":0.6,"04":0.65,"2A":0.6,"2B":0.6}
depts=[]
for line in pops.splitlines():
  parts=line.split(" ")
  code=parts[0]; pop=int(parts[-1])*1000; name=" ".join(parts[1:-1])
  prof={}
  if code in unemp: prof["unemployment"]=unemp[code]
  if code in income: prof["incomeIndex"]=income[code]
  if code in urban: prof["urbanShare"]=urban[code]
  if code in senior: prof["seniorShare"]=senior[code]
  if code in leaning: prof["politicalLeaning"]=leaning[code]
  for key,table in (("healthAccess",HEALTH),("crime",CRIME),("industryShare",INDUSTRY),("agricultureShare",AGRI),("pollution",POLL)):
    if code in table: prof[key]=table[code]
  d={"code":code,"name":name,"article":ARTICLES[name],"region":R[code],"population":pop}
  if prof: d["profile"]=prof
  depts.append(d)
cities_raw="""paris|Paris|75|2.352|48.857|2133000|13100000|1
marseille|Marseille|13|5.370|43.296|873000|1900000|1
lyon|Lyon|69|4.836|45.764|522000|2300000|1
toulouse|Toulouse|31|1.444|43.605|504000|1500000|1
nice|Nice|06|7.262|43.710|348000|620000|1
nantes|Nantes|44|-1.554|47.218|323000|1000000|1
montpellier|Montpellier|34|3.877|43.611|302000|820000|1
strasbourg|Strasbourg|67|7.752|48.573|291000|860000|1
bordeaux|Bordeaux|33|-0.579|44.838|261000|1400000|1
lille|Lille|59|3.057|50.629|236000|1500000|1
rennes|Rennes|35|-1.678|48.117|225000|770000|1
toulon|Toulon|83|5.928|43.124|180000|580000|2
reims|Reims|51|4.032|49.258|180000|330000|2
saint_etienne|Saint-Étienne|42|4.390|45.434|173000|520000|2
le_havre|Le Havre|76|0.108|49.494|166000|320000|2
grenoble|Grenoble|38|5.724|45.188|157000|720000|2
dijon|Dijon|21|5.041|47.322|159000|390000|2
angers|Angers|49|-0.563|47.478|157000|440000|2
nimes|Nîmes|30|4.360|43.837|148000|380000|2
clermont|Clermont-Ferrand|63|3.087|45.777|147000|500000|2
aix|Aix-en-Provence|13|5.447|43.529|145000|400000|3
le_mans|Le Mans|72|0.199|48.006|145000|350000|2
brest|Brest|29|-4.486|48.390|139000|320000|2
tours|Tours|37|0.684|47.394|136000|520000|2
amiens|Amiens|80|2.295|49.894|133000|300000|2
limoges|Limoges|87|1.261|45.833|130000|300000|2
annecy|Annecy|74|6.129|45.899|131000|250000|3
perpignan|Perpignan|66|2.895|42.699|120000|330000|2
besancon|Besançon|25|6.024|47.238|119000|280000|2
metz|Metz|57|6.176|49.119|118000|400000|2
orleans|Orléans|45|1.909|47.902|117000|450000|2
rouen|Rouen|76|1.099|49.443|114000|700000|2
caen|Caen|14|-0.371|49.182|108000|470000|2
mulhouse|Mulhouse|68|7.336|47.750|107000|280000|3
nancy|Nancy|54|6.184|48.692|104000|430000|2
avignon|Avignon|84|4.806|43.949|91000|530000|3
poitiers|Poitiers|86|0.340|46.580|89000|270000|3
dunkerque|Dunkerque|59|2.377|51.034|87000|250000|3
cherbourg|Cherbourg-en-Cotentin|50|-1.616|49.639|79000|120000|3
la_rochelle|La Rochelle|17|-1.151|46.160|78000|230000|3
pau|Pau|64|-0.370|43.295|76000|240000|3
ajaccio|Ajaccio|2A|8.738|41.919|72000|110000|3
saint_nazaire|Saint-Nazaire|44|-2.214|47.273|72000|220000|3
calais|Calais|62|1.858|50.951|67000|140000|3
valence|Valence|26|4.892|44.933|65000|180000|3
bourges|Bourges|18|2.399|47.081|64000|150000|3
troyes|Troyes|10|4.074|48.297|62000|200000|3
chambery|Chambéry|73|5.917|45.564|60000|250000|3
lorient|Lorient|56|-3.370|47.748|57000|200000|3
narbonne|Narbonne|11|3.004|43.184|56000|130000|3
bayonne|Bayonne|64|-1.475|43.493|52000|320000|3
bastia|Bastia|2B|9.450|42.697|48000|100000|3
brive|Brive-la-Gaillarde|19|1.533|45.159|46000|110000|3
"""
cities=[]
for l in cities_raw.strip().splitlines():
  i,n,d,lon,lat,p,a,r=l.split("|")
  cities.append({"id":i,"name":n,"department":d,"lon":float(lon),"lat":float(lat),"population":int(p),"urbanAreaPopulation":int(a),"rank":int(r)})
out={"regions":[{"code":c,"name":n,"capitalCityId":cap,"defaults":df} for c,n,cap,df in regions],"departments":depts,"cities":cities}
json.dump(out,open("countries/FRA/territory.json","w"),ensure_ascii=False,indent=1)
print(len(depts),sum(d["population"] for d in depts))
