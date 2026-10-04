# Président

Jeu de simulation géopolitique moderne pour Android (Kotlin + libGDX).
Le joueur incarne le président fraîchement élu d'un pays réel — la **France** dans cette version —
et le dirige tant qu'il est réélu. Une élection perdue met fin à la partie.

> **Simulation extrêmement profonde derrière, interface extrêmement lisible devant.**

Inspirations : *Supremacy 1914* pour la carte et la prise en main, *Geo-Political Simulator*
pour la profondeur — sans leurs défauts.

## Contenu

| Domaine | Ce qui est simulé |
|---|---|
| Carte | Monde → Europe → France → région → local (LOD, culling, index spatial) ; 13 régions, 96 départements, 53 villes, centrales, barrages, raffineries, ports, aéroports, LGV, autoroutes, bases ; 14 couches (opinion, chômage, revenus, santé, sécurité, industrie, agriculture, pollution, énergie, transports, militaire, crises...) avec légende et chiffre inscrit sur chaque département |
| Temps | Monde persistant, rythme verrouillé, rattrapage déterministe après absence, simulation d'arrière-plan Android (WorkManager) |
| Économie | Modèle macro interconnecté (confiance, impulsions différées, taux, dette, énergie, commerce, sanctions), budget détaillé voté au Parlement |
| Société | Opinion de 13 groupes sociaux et de chaque territoire, services publics, démographie, immigration |
| Politique | Gouvernement (Premier ministre, 11 ministres, priorités), Assemblée nationale (577 sièges, majorité/cohabitation, dissolution, motion de censure), référendums, 18 réformes structurelles, promesses de campagne, scandales |
| Élections | Présidentielle à deux tours, législatives (à échéance ou après dissolution), référendums, élections locales (départementales, régionales, municipales), participation, 6 familles politiques, bilan jugé par chaque groupe, promesses tenues ou rompues |
| Décisions | Panneau « ★ Décider » : 45 décisions nationales en 8 rubriques (économie, social, sécurité, écologie, institutions, communication, international, défense), chacune avec coût, durée, délai et effets contrastés par groupe social, pays ou alliance ; 18 actions locales par département, classées par thème |
| Territoires | Demandes des maires, préfets, présidents de collectivités (accepter, partiellement, cofinancer, reporter, se renseigner, refuser), grands projets |
| Événements | 30 types dépendant de l'état du monde (incidents industriels, catastrophes, crises sociales, attentats, scandales, épidémies...) |
| Diplomatie | 29 pays IA, mémoire diplomatique, négociation par clauses (14 types), contre-propositions, sanctions, ultimatums, condamnations, alliances (OTAN, UE, OTSC), alternances politiques à l'étranger |
| Armée et guerre | Unités sur une grille de 5 000 zones de théâtre, ordres (déplacer, attaquer, défendre, repli, soutien aérien, patrouille), ravitaillement, stocks, production, mobilisation, renseignement imparfait, combats, occupations, capitulations, cessez-le-feu et paix, dissuasion nucléaire |
| Dialogues | Messages procéduraux sans IA générative, mémoire des échanges, jamais deux fois le même texte |
| Interface | Panneaux contextuels lisibles avec « Détails », fiches de territoire à onglets (Résumé en tuiles, Agir, Détails), conseils « À faire » menant à la bonne décision, portraits procéduraux, tutoriel progressif, aide et glossaire, notifications réglables par catégorie, mise en page téléphone |

## Installer l'APK

Chaque push déclenche la CI GitHub (onglet **Actions** → workflow *Build*) : tests du moteur puis
assemblage de l'APK de débogage, téléchargeable dans l'artefact **president-debug-apk**.

## Compiler et lancer

Prérequis : JDK 17+.

```bash
./gradlew :engine:test        # tests du moteur (déterminisme, sauvegarde, diplomatie, guerre, réformes, endurance...)
./gradlew :desktop:run        # lanceur PC de développement (même jeu que sur Android)
```

Le module `android` n'est inclus que si un SDK Android est configuré (`local.properties` avec
`sdk.dir=...` ou variable `ANDROID_HOME`) :

```bash
./gradlew :android:assembleDebug
```

> Le module Android est compilé et assemblé par la CI GitHub. Le lanceur PC utilise exactement
> les mêmes classes `core` et sert au développement.

Options de développement du lanceur PC :

```bash
# échelle d'interface (simule un écran haute densité)
./gradlew :desktop:run -Dpresident.uiScale=2
# script automatique (captures d'écran, zoom, sélection) — voir DevScriptDriver
./gradlew :desktop:run -Dpresident.script="new;wait:60;layer:OPINION;shot:/tmp/carte.png;quit"
```

## Organisation

```
engine/    Moteur de simulation Kotlin pur (aucune dépendance Android ni libGDX)
core/      Rendu de la carte, interface, écrans (libGDX)
desktop/   Lanceur PC (développement)
android/   Lanceur Android, notifications, simulation d'arrière-plan (WorkManager)
assets/    Données du jeu (snapshot du monde, pays, économie, événements, dialogues), géométrie, polices
tools/     Générateurs de données (carte depuis données ouvertes, snapshot France, événements, dialogues)
docs/      Architecture, modèles de simulation, décisions, feuille de route
```

Documentation : [Architecture](docs/ARCHITECTURE.md) · [Modèles de simulation](docs/SIMULATION.md) ·
[Décisions](docs/DECISIONS.md) · [Feuille de route](docs/ROADMAP.md) · [Format des données](assets/data/README.md) ·
[Attributions](ATTRIBUTIONS.md)
