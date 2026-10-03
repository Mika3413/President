"""Génère une partie des données du snapshot FRANCE 2026-10 (valeurs arrondies, sources publiques).
Exécuter depuis n'importe où : écrit dans assets/data/."""
import os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data"))
import json
def item(id,type,name,lon,lat,dept,mw=0,cf=0,emp=0,maint=0,cond=0.7,year=1985,desc=""):
  d={"id":id,"type":type,"name":name,"lon":lon,"lat":lat,"department":dept,"employees":emp,"maintenanceCostMillions":maint,"initialCondition":cond,"commissionedYear":year}
  if mw: d["capacityMW"]=mw; d["capacityFactor"]=cf
  if desc: d["description"]=desc
  return d
N="NUCLEAR_PLANT"
nuc=[("gravelines","Centrale nucléaire de Gravelines",2.136,51.015,"59",5460,1980,0.62,2000),
("paluel","Centrale nucléaire de Paluel",0.635,49.858,"76",5320,1984,0.68,1300),
("penly","Centrale nucléaire de Penly",1.212,49.977,"76",2660,1990,0.72,800),
("flamanville","Centrale nucléaire de Flamanville",-1.882,49.537,"50",4310,1985,0.74,1600),
("chooz","Centrale nucléaire de Chooz",4.789,50.090,"08",3000,1996,0.78,800),
("cattenom","Centrale nucléaire de Cattenom",6.218,49.416,"57",5200,1986,0.7,1300),
("nogent","Centrale nucléaire de Nogent-sur-Seine",3.518,48.515,"10",2620,1987,0.74,700),
("dampierre","Centrale nucléaire de Dampierre",2.517,47.733,"45",3560,1980,0.62,1000),
("belleville","Centrale nucléaire de Belleville",2.875,47.510,"18",2620,1987,0.72,750),
("saint_laurent","Centrale nucléaire de Saint-Laurent",1.580,47.720,"41",1830,1983,0.66,650),
("chinon","Centrale nucléaire de Chinon",0.170,47.230,"37",3620,1982,0.64,1400),
("civaux","Centrale nucléaire de Civaux",0.653,46.457,"86",2990,1997,0.8,750),
("blayais","Centrale nucléaire du Blayais",-0.693,45.256,"33",3640,1981,0.6,1300),
("golfech","Centrale nucléaire de Golfech",0.845,44.106,"82",2620,1990,0.76,750),
("tricastin","Centrale nucléaire du Tricastin",4.732,44.330,"26",3660,1980,0.58,1300),
("cruas","Centrale nucléaire de Cruas",4.757,44.633,"07",3660,1983,0.65,1200),
("saint_alban","Centrale nucléaire de Saint-Alban",4.755,45.404,"38",2670,1985,0.7,800),
("bugey","Centrale nucléaire du Bugey",5.271,45.798,"01",3640,1978,0.57,1400)]
items=[]
for id,name,lon,lat,dept,mw,year,cond,emp in nuc:
  items.append(item(id,N,name,lon,lat,dept,mw,0.78,emp,round(mw*0.035),cond,year,"Réacteurs à eau pressurisée. L'état des installations conditionne leur disponibilité et le risque d'incident."))
hyd=[("serre_poncon","Barrage de Serre-Ponçon",6.27,44.47,"05",380,0.3,1960),("grand_maison","Barrage de Grand'Maison",6.12,45.21,"38",1800,0.12,1987),
("genissiat","Barrage de Génissiat",5.81,46.05,"01",420,0.4,1948),("tignes","Barrage de Tignes",6.92,45.49,"73",380,0.3,1952),
("montezic","Centrale de Montézic",2.65,44.70,"12",910,0.15,1982),("bort","Barrage de Bort-les-Orgues",2.50,45.40,"19",240,0.3,1952),("kembs","Barrage de Kembs",7.51,47.69,"68",160,0.6,1932)]
for id,name,lon,lat,dept,mw,cf,year in hyd:
  items.append(item(id,"HYDRO_DAM",name,lon,lat,dept,mw,cf,120,round(mw*0.02),0.72,year,"Production hydraulique pilotable, sensible aux sécheresses."))
gas=[("martigues","Centrale à cycle combiné de Martigues",5.02,43.39,"13",930,2012),("blenod","Centrale de Blénod",6.06,48.88,"54",430,2011),("bouchain","Centrale de Bouchain",3.30,50.29,"59",575,2016),("landivisiau","Centrale de Landivisiau",-4.12,48.50,"29",446,2022)]
for id,name,lon,lat,dept,mw,year in gas:
  items.append(item(id,"GAS_PLANT",name,lon,lat,dept,mw,0.2,60,round(mw*0.025),0.85,year,"Centrale d'appoint, dépendante des importations de gaz."))
ref=[("gonfreville","Raffinerie de Gonfreville-l'Orcher",0.24,49.48,"76",2012,0.6),("donges","Raffinerie de Donges",-2.07,47.31,"44",1931,0.6),("feyzin","Raffinerie de Feyzin",4.85,45.67,"69",1964,0.62),("lavera","Raffinerie de Lavéra",5.01,43.39,"13",1933,0.58),("port_jerome","Raffinerie de Port-Jérôme",0.57,49.48,"76",1933,0.6)]
for id,name,lon,lat,dept,year,cond in ref:
  items.append(item(id,"REFINERY",name,lon,lat,dept,emp=900,maint=60,cond=cond,year=year,desc="Raffinage de produits pétroliers ; site industriel à risque."))
energy={"electricityDemandTWh":455,"items":items,"nationalGeneration":[
 {"id":"other_hydro","label":"Autres installations hydrauliques","capacityMW":21000,"capacityFactor":0.3},
 {"id":"wind","label":"Éolien","capacityMW":24000,"capacityFactor":0.23},
 {"id":"solar","label":"Solaire","capacityMW":25000,"capacityFactor":0.13},
 {"id":"bioenergy","label":"Bioénergies","capacityMW":2200,"capacityFactor":0.5},
 {"id":"other_thermal","label":"Autres centrales thermiques","capacityMW":8000,"capacityFactor":0.1}]}
json.dump(energy,open("infrastructure/FRA_energy.json","w"),ensure_ascii=False,indent=1)
ports=[("port_le_havre","Port du Havre",0.11,49.48,"76"),("port_marseille","Port de Marseille-Fos",4.90,43.41,"13"),("port_dunkerque","Port de Dunkerque",2.32,51.03,"59"),("port_nantes","Port de Nantes-Saint-Nazaire",-2.20,47.27,"44"),("port_rouen","Port de Rouen",1.07,49.44,"76"),("port_bordeaux","Port de Bordeaux",-0.54,44.87,"33"),("port_la_rochelle","Port de La Rochelle",-1.22,46.15,"17")]
air=[("cdg","Aéroport Paris-Charles-de-Gaulle",2.55,49.01,"95",90000),("orly","Aéroport de Paris-Orly",2.37,48.73,"94",28000),("nice_airport","Aéroport Nice-Côte d'Azur",7.21,43.66,"06",6000),("lyon_airport","Aéroport Lyon-Saint-Exupéry",5.08,45.72,"69",5000),("marseille_airport","Aéroport Marseille-Provence",5.22,43.44,"13",5000),("toulouse_airport","Aéroport Toulouse-Blagnac",1.37,43.63,"31",5000),("basel_airport","EuroAirport Bâle-Mulhouse",7.53,47.59,"68",6000),("bordeaux_airport","Aéroport de Bordeaux-Mérignac",-0.72,44.83,"33",3000),("nantes_airport","Aéroport de Nantes-Atlantique",-1.61,47.15,"44",2500)]
titems=[item(i,"PORT",n,lo,la,d,emp=3000,maint=120,cond=0.68,year=1960,desc="Porte d'entrée du commerce extérieur.") for i,n,lo,la,d in ports]
titems+=[item(i,"AIRPORT",n,lo,la,d,emp=e,maint=80,cond=0.72,year=1975,desc="Plateforme aéroportuaire internationale.") for i,n,lo,la,d,e in air]
nets=[("lgv_sud_est","RAIL_HIGH_SPEED","LGV Sud-Est",["paris","dijon","lyon"]),
("lgv_mediterranee","RAIL_HIGH_SPEED","LGV Méditerranée",["lyon","valence","avignon","aix","marseille"]),
("lgv_mediterranee_ouest","RAIL_HIGH_SPEED","Contournement Nîmes-Montpellier",["avignon","nimes","montpellier"]),
("lgv_atlantique","RAIL_HIGH_SPEED","LGV Atlantique / Sud Europe Atlantique",["paris","tours","poitiers","bordeaux"]),
("lgv_bretagne","RAIL_HIGH_SPEED","LGV Bretagne-Pays de la Loire",["paris","le_mans","rennes"]),
("lgv_nord","RAIL_HIGH_SPEED","LGV Nord",["paris","amiens","lille","calais"]),
("lgv_est","RAIL_HIGH_SPEED","LGV Est européenne",["paris","reims","metz","strasbourg"]),
("lgv_rhin_rhone","RAIL_HIGH_SPEED","LGV Rhin-Rhône",["dijon","besancon","mulhouse"]),
("a1","MOTORWAY","A1",["paris","amiens","lille"]),("a6_a7","MOTORWAY","A6 / A7",["paris","dijon","lyon","valence","avignon","aix","marseille"]),
("a8","MOTORWAY","A8",["aix","toulon","nice"]),("a9","MOTORWAY","A9",["avignon","nimes","montpellier","narbonne","perpignan"]),
("a10","MOTORWAY","A10",["paris","orleans","tours","poitiers","bordeaux"]),("a61_a62","MOTORWAY","A62 / A61",["bordeaux","toulouse","narbonne"]),
("a4","MOTORWAY","A4",["paris","reims","metz","strasbourg"]),("a11","MOTORWAY","A11",["paris","le_mans","angers","nantes"]),
("a13_a84","MOTORWAY","A13 / A84",["paris","rouen","caen","rennes"]),("a43","MOTORWAY","A43",["lyon","chambery"]),
("a75","MOTORWAY","A75",["clermont","montpellier"]),("a71","MOTORWAY","A71",["orleans","bourges","clermont"]),
("a20","MOTORWAY","A20",["orleans","limoges","brive","toulouse"]),("a31","MOTORWAY","A31",["dijon","nancy","metz"]),
("a63","MOTORWAY","A63",["bordeaux","bayonne"]),("a64","MOTORWAY","A64",["bayonne","pau","toulouse"]),("a16","MOTORWAY","A16",["paris","amiens","calais","dunkerque"]),
("a41","MOTORWAY","A41",["chambery","annecy"]),("a48","MOTORWAY","A48",["lyon","grenoble"]),("a72","MOTORWAY","A72",["lyon","saint_etienne","clermont"])]
transport={"items":titems,"networks":[{"id":i,"kind":k,"name":n,"cityIds":c} for i,k,n,c in nets]}
json.dump(transport,open("infrastructure/FRA_transport.json","w"),ensure_ascii=False,indent=1)
bases=[("toulon_base","Base navale de Toulon","Marine",5.92,43.11,"83",6),("brest_base","Base navale de Brest",  "Marine",-4.50,48.38,"29",4),
("ile_longue","Base de l'Île Longue","Forces stratégiques",-4.51,48.30,"29",2),("istres","Base aérienne d'Istres","Armée de l'air",4.92,43.52,"13",3),
("saint_dizier","Base aérienne de Saint-Dizier","Armée de l'air",4.90,48.64,"52",3),("mont_de_marsan","Base aérienne de Mont-de-Marsan","Armée de l'air",-0.50,43.91,"40",3),
("evreux","Base aérienne d'Évreux","Armée de l'air",1.22,49.03,"27",2),("mourmelon","Camp de Mourmelon","Armée de terre",4.36,49.13,"51",3),
("canjuers","Camp de Canjuers","Armée de terre",6.47,43.65,"83",3),("satory","Satory","Armée de terre",2.12,48.78,"78",2),
("strasbourg_base","Quartier de Strasbourg","Armée de terre",7.75,48.58,"67",2),("nimes_base","Quartier de Nîmes","Armée de terre",4.36,43.83,"30",2),
("carcassonne","Quartier de Carcassonne","Armée de terre",2.35,43.21,"11",2),("varces","Quartier de Varces","Armée de terre",5.68,45.09,"38",2)]
units=[("u_2bb","2e brigade blindée","Armée de terre","ARMORED_BRIGADE","strasbourg_base",7500,{"Chars Leclerc":60,"Véhicules blindés":320,"Pièces d'artillerie":24},0.7,0.68,0.55,0.6),
("u_7bb","7e brigade blindée","Armée de terre","ARMORED_BRIGADE","mourmelon",7200,{"Chars Leclerc":55,"Véhicules blindés":300,"Pièces d'artillerie":24},0.66,0.65,0.5,0.62),
("u_6blb","6e brigade légère blindée","Armée de terre","LIGHT_ARMORED_BRIGADE","nimes_base",6500,{"Véhicules blindés":260,"Pièces d'artillerie":18},0.72,0.7,0.55,0.65),
("u_9bima","9e brigade d'infanterie de marine","Armée de terre","MARINE_BRIGADE","canjuers",6800,{"Véhicules blindés":240,"Pièces d'artillerie":18},0.7,0.7,0.52,0.6),
("u_11bp","11e brigade parachutiste","Armée de terre","AIRBORNE_BRIGADE","carcassonne",7000,{"Véhicules légers":350,"Pièces d'artillerie":12},0.75,0.75,0.5,0.6),
("u_27bim","27e brigade d'infanterie de montagne","Armée de terre","MOUNTAIN_BRIGADE","varces",6000,{"Véhicules légers":300,"Pièces d'artillerie":12},0.72,0.73,0.55,0.62),
("u_gan","Groupe aéronaval","Marine","CARRIER_GROUP","toulon_base",3500,{"Porte-avions":1,"Frégates":3,"Rafale Marine":30},0.62,0.7,0.6,0.65),
("u_fregates_med","Force d'action navale — Méditerranée","Marine","SURFACE_GROUP","toulon_base",4000,{"Frégates":8,"Bâtiments de soutien":3},0.65,0.68,0.55,0.62),
("u_fregates_atl","Force d'action navale — Atlantique","Marine","SURFACE_GROUP","brest_base",3500,{"Frégates":6,"Chasseurs de mines":8},0.63,0.66,0.55,0.62),
("u_fost","Force océanique stratégique","Forces stratégiques","SSBN_FORCE","ile_longue",3000,{"Sous-marins lanceurs d'engins":4,"Sous-marins d'attaque":6},0.85,0.8,0.8,0.8),
("u_ec_st_dizier","Escadre de chasse de Saint-Dizier","Armée de l'air","FIGHTER_WING","saint_dizier",1800,{"Rafale":40},0.64,0.7,0.45,0.6),
("u_ec_mdm","Escadre de chasse de Mont-de-Marsan","Armée de l'air","FIGHTER_WING","mont_de_marsan",1800,{"Rafale":35},0.66,0.7,0.45,0.6),
("u_ravitailleurs","Escadre de ravitaillement d'Istres","Armée de l'air","TANKER_WING","istres",1500,{"A330 MRTT":12},0.7,0.7,0.6,0.65),
("u_transport","Escadre de transport d'Évreux","Armée de l'air","TRANSPORT_WING","evreux",1500,{"A400M":15,"C-130":10},0.6,0.68,0.6,0.6),
("u_soutien_idf","Brigade de soutien d'Île-de-France","Armée de terre","SUPPORT_BRIGADE","satory",5000,{"Camions logistiques":600},0.68,0.65,0.6,0.6)]
mil={"budgetSpendingItem":"defense","bases":[{"id":i,"name":n,"branch":b,"lon":lo,"lat":la,"department":d,"capacityUnits":c} for i,n,b,lo,la,d,c in bases],
"units":[{"id":i,"name":n,"branch":b,"type":t,"baseId":base,"personnel":p,"equipment":eq,"readiness":r,"morale":m,"ammunition":a,"fuel":f} for i,n,b,t,base,p,eq,r,m,a,f in units]}
json.dump(mil,open("military/FRA_2026_10.json","w"),ensure_ascii=False,indent=1)
print(sum(x["capacityMW"]*x["capacityFactor"] for x in items if "capacityMW" in x)*8766/1e6, sum(g["capacityMW"]*g["capacityFactor"] for g in energy["nationalGeneration"])*8766/1e6)
