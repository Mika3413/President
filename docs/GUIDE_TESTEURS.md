# Guide pour faire tester le jeu

Le meilleur moyen de savoir si le jeu plaît : regarder de vraies personnes y jouer, **sans rien leur expliquer**. Ce guide tient en une page.

## Qui inviter

- 5 à 10 personnes, dont au moins 3 qui ne connaissent pas les jeux de stratégie.
- Un téléphone Android avec l'APK installé (le leur, de préférence).

## Déroulé d'une séance (30 minutes)

1. **Ne rien expliquer.** Dites seulement : « C'est un jeu où tu es président. Joue comme tu veux, et pense à voix haute. »
2. **Regardez sans aider** pendant 20 minutes. Si la personne est bloquée plus de 30 secondes, notez-le, puis aidez-la d'une phrase.
3. **Posez les 6 questions** ci-dessous (5 minutes).
4. **Laissez le jeu installé.** Une semaine plus tard, demandez : « Tu y es retourné ? Combien de fois ? Pourquoi ? »

## Ce qu'il faut noter pendant la partie

| Moment | Ce qu'on observe |
|---|---|
| Premier matin (la crue) | A-t-elle compris qu'il fallait décider ? A-t-elle lu l'écran « Votre première décision » ? |
| Tutoriel (6 étapes) | Où a-t-elle sauté ou quitté le tutoriel ? |
| 5 premières minutes | Le premier moment où elle dit « ah, d'accord » ou sourit. Le premier moment où elle soupire. |
| Écrans | Quels écrans ouvre-t-elle d'elle-même ? Lesquels n'ouvre-t-elle jamais ? |
| Alertes | Lit-elle les bandeaux, ou les ferme-t-elle sans lire ? |
| Carte | Touche-t-elle la carte ? Zoome-t-elle ? Comprend-elle les couleurs ? |
| Ennui | À quel moment regarde-t-elle ailleurs ou pose-t-elle le téléphone ? |

Notez l'heure de chaque blocage : c'est la donnée la plus précieuse.

## Les 6 questions après la partie

1. En une phrase, c'est quoi ce jeu ?
2. Qu'est-ce qui t'a donné envie de continuer ?
3. À quel moment tu ne savais plus quoi faire ?
4. Un personnage t'a-t-il marqué (ministre, dirigeant étranger) ? Lequel, et pourquoi ?
5. Qu'est-ce que tu enlèverais ?
6. Sur 10, tu le conseillerais à un ami ? (Moins de 7 : pourquoi ?)

## Ce que les réponses veulent dire

- **Plus de 2 blocages pendant les 10 premières minutes** : l'entrée dans le jeu est à retravailler en priorité.
- **« Je ne savais pas quoi faire »** : l'encart « Vos priorités » n'est pas assez visible ou pas assez clair.
- **Bandeaux fermés sans être lus** : encore trop d'alertes.
- **Aucun personnage marquant** : les ministres et dirigeants ne parlent pas encore assez.
- **Personne ne revient au bout d'une semaine** : il manque des raisons de revenir (rappels, objectifs du moment).

## Vérifications automatiques déjà en place

Elles tournent avec les tests du moteur (`./gradlew :engine:test`) :

- **Joueur débutant** (`NaivePlayerTest`) : une année entière avec le premier matin, puis toujours la première réponse. Il vérifie qu'il n'y a ni plantage ni chiffres absurdes, au plus 15 messages en attente, au plus 4 alertes par semaine en moyenne, et que les écrans s'ouvrent bien au fil de l'année.
- **Bruit des notifications** : `./gradlew :engine:test --tests '*NoiseProbe*' -Daudit=noise` liste les alertes d'une année par type.
- **Équilibrage** : `--tests '*Balance*' -Dbalance=true` simule des mandats complets avec plusieurs styles de jeu.
- **Vitesse** (`SolidityTest`) : une journée de jeu doit se simuler en bien moins de 0,4 s (environ 11 ms aujourd'hui).
