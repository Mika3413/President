# Décisions de conception

Décisions secondaires prises pendant le développement, documentées comme demandé.

| # | Sujet | Décision | Raison |
|---|---|---|---|
| 1 | Modules | `engine` (Kotlin pur) séparé de `core` (libGDX) plutôt que de simples packages | Garantit à la compilation que la simulation ne dépend ni d'Android ni de libGDX ; permet le worker Android sans contexte graphique |
| 2 | Module Android conditionnel | Inclus seulement si un SDK est détecté | Le moteur et ses tests restent compilables partout (CI, PC sans SDK) |
| 3 | Sérialisation | kotlinx-serialization (seule dépendance du moteur) | Sauvegardes et données JSON robustes, multiplateforme, sans réflexion |
| 4 | Pas de temps | Heure du monde ; systèmes quotidiens/mensuels aux changements de jour/mois | Rattrapage rapide et identique quel que soit le découpage |
| 5 | Survenue des événements | Tirés une fois par jour, survenant à une heure aléatoire (6h-23h) | Naturel pour le joueur, sans multiplier le coût de calcul par 24 |
| 6 | Rattrapage maximal | 3 650 jours (configurable) | Évite un calcul démesuré après une très longue absence |
| 7 | Unité de temps | `WorldTime` en secondes depuis l'époque Unix (UTC), calendrier réel | Dates lisibles, saisons, durées en jours/mois |
| 8 | Projection | Équirectangulaire au 46,5°N | Simple, rapide, fidèle pour la France et l'Europe |
| 9 | Géométrie | Données IGN (via france-geojson, Licence Ouverte) et Natural Earth (domaine public), simplifiées hors ligne | Légal, léger (≈650 Ko), reproductible par script |
| 10 | Outre-mer | Non cartographié en V1 (population incluse dans le total national) | Priorité à la métropole pour la tranche verticale |
| 11 | Pays IA | Allemagne, Espagne, Italie, Royaume-Uni, Belgique en simulation allégée | Une interaction diplomatique crédible avec plusieurs partenaires ; extensible par données |
| 12 | Personnages | Générateur à partir de réserves de prénoms/noms courants par pays | Personnes toujours fictives ; noms de responsables connus exclus des réserves |
| 13 | Opinion locale | Orientation politique dominante par territoire (approximation sur un axe gauche-droite) | Donne des cartes d'opinion contrastées et crédibles ; un deuxième axe pourra être ajouté |
| 14 | Parlement | Un indicateur de soutien et un vote probabiliste par mesure, adoption sans vote possible | Réalisme des conséquences sans reproduire la procédure parlementaire |
| 15 | Dialogues | Modèles écrits en JSON générés par un script Python versionné | Facile à enrichir ; évite les erreurs de syntaxe JSON à la main |
| 16 | Unicité des messages | Signature FNV-1a normalisée ; au-delà de 32 tentatives, ajout d'une mention datée | Garantit l'unicité même si un modèle manque de variantes |
| 17 | Lisibilité | Échelles qualitatives dans `ui/readouts.json` | Ajustables sans code ; jamais de score brut affiché par défaut |
| 18 | Orientation écran | Paysage (`sensorLandscape`) | Carte et panneaux latéraux, comme les jeux de stratégie de référence |
| 19 | Polices | DejaVu Sans (licence libre), générées à la densité de l'écran | Accents français complets, rendu net sur mobile |
| 20 | Notifications | Au premier plan : bandeaux en jeu ; en arrière-plan : notifications système filtrées par catégorie | Pas de doublons, réglage fin par le joueur |
| 21 | Fin de partie | Défaite électorale = écran de fin et nouvelle partie | Conforme au cahier des charges (pas de rôle d'opposant) |
| 22 | Valeurs de départ | Ordres de grandeur publics arrondis (fin 2025 / prévisions 2026) | Snapshot crédible ; à affiner avec des sources officielles datées |
| 23 | Théâtre militaire | Grille régulière de zones de 1° plutôt que des provinces dessinées | Couvre tous les pays sans données supplémentaires ; lisible ; reproductible par script |
| 24 | Combat | Résolution horaire à pertes proportionnelles, sans tirage par bataille | Fronts stables et compréhensibles ; aucune microgestion nécessaire |
| 25 | Dissuasion | L'IA n'attaque jamais une puissance nucléaire ni ses alliés défensifs | Réalisme stratégique ; le territoire français n'est exposé que si le joueur provoque |
| 26 | Budgets | Les dépenses sont revalorisées avec l'inflation par défaut | Évite une austérité cachée irréaliste ; toute amélioration exige des moyens réels |
| 27 | Prime au sortant | Bonus électoral de notoriété pour le président sortant | Calibrage : un bilan moyen donne une élection disputée, un mauvais bilan une défaite |
| 28 | Portraits | Générés par programme depuis la graine du personnage | Aucune image de personne réelle ; visages stables d'une session à l'autre |
| 29 | Android | APK construit par la CI GitHub (dépôt Maven de Google inaccessible depuis l'environnement de développement) | Validation réelle de la compilation Android à chaque push |
