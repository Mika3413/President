"""Génère une partie des données du snapshot FRANCE 2026-10 (valeurs arrondies, sources publiques).
Exécuter depuis n'importe où : écrit dans assets/data/."""
import os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data"))
import json
def S(**kw): return kw
groups=[
 ("young","18-29 ans","age",0.18,0.46,-0.2,-0.4,0.58,S(unemployment=0.08,prices=0.04,purchasing_power=0.06,education=0.05,environment=0.06,health=0.02,security=0.02,household_taxes=0.03,transport=0.02),"nonSeniorShare",1.0),
 ("adults","30-64 ans","age",0.55,0.45,0.0,0.0,0.75,S(unemployment=0.07,prices=0.05,purchasing_power=0.06,household_taxes=0.05,health=0.04,education=0.04,security=0.04,debt=0.02,energy_prices=0.03,growth=0.02),"nonSeniorShare",1.0),
 ("seniors","65 ans et plus","age",0.27,0.47,0.2,0.3,0.86,S(pensions=0.09,health=0.07,security=0.06,prices=0.05,household_taxes=0.03,debt=0.02,energy_prices=0.03),"seniorShare",1.0),
 ("private_employees","Salariés du privé","status",0.38,0.45,0.0,0.0,0.72,S(unemployment=0.09,purchasing_power=0.08,household_taxes=0.05,prices=0.04,growth=0.03,transport=0.02,energy_prices=0.03),None,1.0),
 ("civil_servants","Fonctionnaires","status",0.12,0.44,-0.35,-0.2,0.8,S(education=0.06,health=0.06,security=0.03,purchasing_power=0.05,social=0.04,transport=0.02),None,1.0),
 ("self_employed","Indépendants et chefs d'entreprise","status",0.08,0.44,0.5,0.2,0.8,S(business_taxes=0.09,growth=0.07,household_taxes=0.04,debt=0.04,energy_prices=0.04,security=0.02),None,1.0),
 ("retirees","Retraités","status",0.27,0.47,0.2,0.3,0.86,S(pensions=0.1,health=0.07,prices=0.05,security=0.05,energy_prices=0.03),"seniorShare",1.0),
 ("inactive","Étudiants et sans emploi","status",0.15,0.44,-0.3,0.0,0.55,S(unemployment=0.1,social=0.07,education=0.04,prices=0.04,purchasing_power=0.04),None,1.0),
 ("low_income","Revenus modestes","income",0.3,0.43,-0.35,0.15,0.6,S(prices=0.07,purchasing_power=0.08,social=0.07,unemployment=0.06,energy_prices=0.05,health=0.03),"incomeIndex",-2.0),
 ("middle_income","Classes moyennes","income",0.5,0.46,0.0,0.0,0.76,S(household_taxes=0.07,purchasing_power=0.06,unemployment=0.05,education=0.04,health=0.04,security=0.04,prices=0.03),None,1.0),
 ("high_income","Hauts revenus","income",0.2,0.47,0.4,-0.15,0.86,S(household_taxes=0.08,business_taxes=0.05,growth=0.05,debt=0.04,security=0.03),"incomeIndex",3.0),
 ("urban","Urbains","habitat",0.6,0.46,-0.15,-0.3,0.72,S(transport=0.05,environment=0.05,security=0.05,purchasing_power=0.05,unemployment=0.05,health=0.03,prices=0.03),"urbanShare",1.0),
 ("rural","Ruraux","habitat",0.4,0.45,0.2,0.35,0.79,S(energy_prices=0.07,health=0.06,transport=0.05,purchasing_power=0.06,prices=0.04,security=0.03,household_taxes=0.03),"ruralShare",1.0),
]
out_groups=[]
for gid,label,part,share,base,ec,so,turn,sens,attr,el in groups:
  sens=dict(sens); sens["war"]=0.07 if gid in ("seniors","retirees","young") else 0.05
  g={"id":gid,"label":label,"partition":part,"populationShare":share,"baseApproval":base,"economicLeaning":ec,"socialLeaning":so,"baseTurnout":turn,"sensitivities":sens}
  if attr: g["localAttribute"]=attr; g["localElasticity"]=el
  out_groups.append(g)
T=True
factors=[
 {"id":"unemployment","label":"Chômage","variable":"economy.unemployment","neutral":0.075,"scale":0.02,"lowerIsBetter":T,"relativeToStart":T},
 {"id":"prices","label":"Prix","variable":"economy.inflation","neutral":0.02,"scale":0.03,"lowerIsBetter":T},
 {"id":"purchasing_power","label":"Pouvoir d'achat","variable":"economy.purchasingPower","neutral":1.0,"scale":0.08,"relativeToStart":T},
 {"id":"household_taxes","label":"Impôts des ménages","variable":"tax.households","neutral":0.3,"scale":0.01,"lowerIsBetter":T,"relativeToStart":T},
 {"id":"business_taxes","label":"Fiscalité des entreprises","variable":"tax.businesses","neutral":0.03,"scale":0.005,"lowerIsBetter":T,"relativeToStart":T},
 {"id":"health","label":"Système de santé","variable":"quality.health","neutral":0.5,"scale":0.2,"relativeToStart":T},
 {"id":"education","label":"École","variable":"quality.education","neutral":0.5,"scale":0.2,"relativeToStart":T},
 {"id":"security","label":"Sécurité","variable":"quality.security","neutral":0.5,"scale":0.2,"relativeToStart":T},
 {"id":"pensions","label":"Retraites","variable":"quality.pensions","neutral":0.5,"scale":0.2,"relativeToStart":T},
 {"id":"social","label":"Protection sociale","variable":"quality.social","neutral":0.5,"scale":0.2,"relativeToStart":T},
 {"id":"transport","label":"Transports","variable":"quality.transport","neutral":0.5,"scale":0.2,"relativeToStart":T},
 {"id":"environment","label":"Environnement","variable":"quality.environment","neutral":0.5,"scale":0.2,"relativeToStart":T},
 {"id":"debt","label":"Dette publique","variable":"economy.debtRatio","neutral":1.15,"scale":0.15,"lowerIsBetter":T,"relativeToStart":T},
 {"id":"energy_prices","label":"Prix de l'énergie","variable":"energy.priceIndex","neutral":1.0,"scale":0.4,"lowerIsBetter":T},
 {"id":"growth","label":"Activité économique","variable":"economy.growth","neutral":0.01,"scale":0.02},
 {"id":"war","label":"Guerre","variable":"military.warWeariness","neutral":0.0,"scale":0.5,"lowerIsBetter":T},
]
sg={"partitions":[{"id":"age","label":"Âge"},{"id":"status","label":"Statut"},{"id":"income","label":"Revenus"},{"id":"habitat","label":"Habitat"}],
 "groups":out_groups,"factors":factors,"honeymoonBonus":0.08,"honeymoonDecayMonthly":0.12,"adjustmentMonthly":0.3,"shockDecayMonthly":0.25,"localShockDecayMonthly":0.2,"localUnemploymentWeight":1.5,"localLeaningWeight":0.18}
json.dump(sg,open("countries/FRA/social_groups.json","w"),ensure_ascii=False,indent=1)
el={"termYears":5,"secondRoundGapDays":14,
 "families":[
  {"id":"radical_left","name":"Gauche radicale","economicPosition":-0.8,"socialPosition":-0.4,"baseStrength":0.0},
  {"id":"left","name":"Gauche sociale-démocrate","economicPosition":-0.45,"socialPosition":-0.35,"baseStrength":0.0},
  {"id":"greens","name":"Écologistes","economicPosition":-0.4,"socialPosition":-0.6,"baseStrength":-0.1},
  {"id":"centre","name":"Centre libéral","economicPosition":0.15,"socialPosition":-0.15,"baseStrength":0.0},
  {"id":"right","name":"Droite républicaine","economicPosition":0.55,"socialPosition":0.3,"baseStrength":0.0},
  {"id":"nationalist","name":"Droite nationaliste","economicPosition":0.2,"socialPosition":0.85,"baseStrength":0.1}],
 "incumbentRecordWeight":1.8,"affinityWeight":1.5,"choiceTemperature":0.3,"turnoutDiscontentWeight":0.3,"turnoutEnthusiasmWeight":0.2,"pollNoise":0.04,"scandalPenalty":0.1}
json.dump(el,open("countries/FRA/elections.json","w"),ensure_ascii=False,indent=1)
print("ok")
