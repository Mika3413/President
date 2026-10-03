# Données du jeu

Toutes les valeurs propres à un pays, tous les textes et tous les coefficients sont ici.
Le moteur n'en contient aucun. Les données sont chargées et **validées** au démarrage
(`DataLoader`, `DataValidator`) ; une partie copie ensuite ce dont elle a besoin et devient
indépendante (mettre à jour ces fichiers ne modifie pas une sauvegarde existante).

```
config/game_config.json        Rythmes de jeu, réglages de simulation, liste des fichiers communs
world_snapshots/               Snapshots du monde (date de départ, pays, relations initiales)
countries/<ISO3>/country.json  Définition d'un pays (institutions, profil stratégique, dirigeant)
countries/FRA/                 Territoire, gouvernement, groupes sociaux, élections (pays jouable)
economy/                       Snapshots économiques par pays + paramètres du modèle macro
infrastructure/                Types d'équipements, énergie et transports (France)
military/                      Bases et unités (France)
events/                        Événements dynamiques
dialogue/fr/                   Modèles de messages procéduraux + lexique de synonymes
diplomacy/clauses.json         Clauses négociables, types de souvenirs, paramètres de l'IA
names/                         Réserves de prénoms/noms par pays (personnages fictifs)
ui/readouts.json               Échelles qualitatives affichées au joueur
geo/                           Géométrie (générée par tools/mapgen)
```

Plusieurs fichiers sont produits par les scripts de `tools/datagen/` (modifiez le script puis
relancez-le, plutôt que le JSON). Ordre pour les événements et dialogues :
`dialogue_fr.py`, `events_fr.py`, `events_crises_fr.py`, `events_local_fr.py`, `events_national_fr.py`,
puis **`finalize_dialogue.py` en dernier** (formulations tirées des options, JSON compact).

### Dialogues

Syntaxe des textes : `{variable}`, `[[mot]]` (synonyme du lexique), `{{a|b|c}}` (alternative).
Étiquettes utiles : `voice:formal|direct|warm|technical|lyrical|blunt` (style propre à chaque
personnage), `sender:elected|official|foreign`, `relation:*`, `history:*` (avec `{lastTopic}`,
`{lastDate}`), `news:recent` (avec `{recentNews}`), `mood:*` et `topic:*` (entretiens).
Le moteur ne réemploie jamais une phrase déjà écrite par le même personnage, ni une phrase lue dans
les 40 derniers messages. Variables de lieu accordées : `{departmentIn}` (« dans le Finistère »),
`{departmentOf}`, `{departmentThe}`, `{infrastructureAt}`, `{infrastructureOf}`, `{foreignIn}`
(« en Italie »), `{foreignOf}`, `{foreignThe}`, `{ForeignThe}`, `{foreignTo}`.

## Ajouter un pays IA

1. `countries/XXX/country.json` avec `detail: "LIGHT"` (voir `DEU`).
2. `economy/XXX_2026_10.json` avec `aggregateBudget`.
3. Une réserve de noms dans `names/` et dans `config/game_config.json`.
4. L'ajouter à la liste `countries` du snapshot (et éventuellement des `initialRelations`).

## Rendre un pays jouable

`detail: "FULL"` et les fichiers `territory`, `government`, `socialGroups`, `elections`
(+ `energy`, `transport`, `military`), un budget détaillé, puis l'ajouter à `playableCountries`.
Il faudra aussi sa géométrie (`tools/mapgen`).

## Événements (`events/*.json`)

| Champ | Rôle |
|---|---|
| `scope` | `NATIONAL`, `DEPARTMENT`, `CITY`, `INFRASTRUCTURE`, `MINISTER`, `FOREIGN_COUNTRY` |
| `baseDailyProbability` | probabilité quotidienne **par cible** (96 départements → ×96 au niveau national) |
| `modifiers` | multiplicateur interpolé entre `factorAtFrom` et `factorAtTo` selon une variable |
| `conditions` | bornes `min`/`max` ou liste `oneOf` |
| `cooldownDays` / `scopeCooldownDays` | délai minimal global / par cible |
| `params` | valeurs tirées (montants...), éventuellement multipliées par une variable (`scaleBy`) |
| `immediateEffects` | effets au déclenchement |
| `message` | modèle de dialogue, expéditeur, délai de réponse, options, option par défaut |

Une option peut provoquer la suite d'une histoire : effet `chain.<id>` avec `amount` = probabilité
(0 à 1) et `delayDays`. L'épisode suivant a en général `baseDailyProbability: 0`.

### Variables (`variable`, `scaleBy`)

`economy.unemployment|inflation|growth|consumerConfidence|businessConfidence|debtRatio|deficitRatio|energyPriceIndex|purchasingPower`,
`energy.margin|priceIndex`, `opinion.national`, `opinion.group.<id>`, `quality.<domaine>`,
`tax.households|businesses`, `government.parliamentSupport`, `military.readiness|warWeariness|atWar|nearbyWar|ammunitionStock`,
`president.<trait>|scandals`, `season.month`,
et pour la cible : `scope.approval|unemployment|incomeIndex|urbanShare|seniorShare|populationMillions|mediterranean|healthAccess|crime|industryShare|agricultureShare|pollution|satisfaction|condition|maintenance|capacityGW|integrity|loyalty|competence|popularity|relation|electricityBalance`.

### Effets (`target`)

`economy.output|consumerConfidence|businessConfidence|inflation|unemployment`, `budget.oneOff` (Md€),
`opinion.national`, `opinion.group.<id>`, `quality.<domaine>`, `government.parliamentSupport`,
`military.readiness|ammoStock|fuelStock`, `memory.<PAYS>.<TYPE>` (souvenir diplomatique),
`economy.potentialGrowth|naturalUnemployment`, `spending.<poste>` (multiplicateur budgétaire), `revenue.<prélèvement>` (taux),
`demography.immigration`, `energy.capacity.<parc>` (MW), `president.scandal|popularity...`,
et génériques résolus selon la cible : `scope.approval|unemployment|satisfaction|condition|maintenance|offlineDays`,
`region.approval`, `sender.relation|loyalty|popularity`, `subject.popularity|loyalty|dismiss`.
`amount` ou `param` (+ `factor`), `days` (étalement), `delayDays`.

## Dialogues (`dialogue/fr/*.json`)

Un modèle = `subject` + `sections`. Chaque variante peut exiger (`when`) ou exclure (`unless`) des
étiquettes : `trait:aggressive|pragmatic|cautious|proud|warm|nationalist|tough`,
`relation:good|neutral|bad`, `history:none|refused|refused_many|cooperated|cooperated_many|postponed`,
`season:*`, `economy:good|bad`, `president:popular|unpopular|female|male`, `sender:female|male`,
`urgency:high`, `followup:reask`, `has:city`, `response:accepted|refused|countered`.
`{variable}` est remplacée (`honorific`, `sender`, `senderTitle`, `city`, `department`, `region`,
`amountText`, `clauses`, `reasons`...), `[[mot]]` tire un synonyme du lexique.

## Armée (`military/`)

`unit_types.json` : types d'unités (domaine, attaque, défense, vitesse, rayon d'action, consommations,
coût, délai de production) et paramètres militaires (pertes, ravitaillement, lassitude, stocks,
mobilisation). `FRA_2026_10.json` : bases et unités françaises. Les armées étrangères sont générées
depuis `strategic.forces` de chaque pays. La grille des zones (`geo/zones.json`) est produite par
`tools/mapgen/build_zones.py`.

## Réformes et promesses

`countries/FRA/reforms.json` et `promises.json`, générés par `tools/datagen/reforms_fr.py`.
