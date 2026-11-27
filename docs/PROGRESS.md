# Journal d'avancement

Document de reprise : une nouvelle session doit pouvoir repartir d'ici sans autre contexte.

## État au 27/11/2026

- Branche de travail : `claude/arkatv-excellence` (créée depuis `claude/arkatv-v1`, la 0.1.0 validée par
  l'utilisateur). Rien n'est poussé sur `main`. Aucune release publiée depuis cette branche.
- Version : `0.2.0-dev` (versionCode 6).
- CI : tests unitaires, lint et assemblage passent à chaque push.
- **Rien n'a été exécuté sur un appareil.** L'environnement de développement ne peut ni compiler
  localement (dépôts Maven et SDK Android inaccessibles) ni lancer d'émulateur : chaque vérification
  passe par GitHub Actions, et tout le comportement à l'écran reste à valider à la main
  (`QA_CHECKLIST.md`).

## Phase P0 — ce qui est livré

| Sujet | État | Où |
| --- | --- | --- |
| Audit de la 0.1.0 | fait | conclusions dans `ARCHITECTURE.md` (dette), `SECURITY.md`, `PERFORMANCE.md` |
| Chiffrement des données sensibles + migration 0.1.0 | fait, testé unitairement | `data/security/SecretBox.kt`, `data/Store.kt` |
| Exclusion des sauvegardes | fait | `res/xml/data_extraction_rules.xml`, manifeste |
| Cache catalogue + guide (Room), démarrage sur cache | fait, non testé sur appareil | `data/db/`, `data/Repo.kt` |
| Dernier catalogue conservé si l'actualisation échoue | fait, non testé sur appareil | `Repo.open` (`syncError`) |
| Tokens de design, nouvelle palette | fait | `ui/Theme.kt`, `DESIGN_SYSTEM.md` |
| Focus visible à la télécommande | partiel (cartes et lignes) | `Modifier.focusHighlight` |
| Tests unitaires | 44 tests, 7 classes (dont le cache Room et la recherche sur base en mémoire) | `app/src/test/` |
| Signature stable des builds de debug | fait | `app/debug.keystore`, `app/build.gradle.kts` |
| Films et séries lus par pages depuis la base (accueil, grilles), tris dans les grilles, position conservée au retour d'une fiche | fait, non testé sur appareil | `CatalogStore.loadSummary` / `vodPage`, `ui/CatalogGridScreen.kt`, `MainActivity.kt` |
| Actualisation en flux : le catalogue est écrit en base au fil du téléchargement, en une transaction ; un échec laisse l'ancien catalogue intact | fait, testé unitairement, non testé sur appareil | `Repo.sync`, `CatalogStore.replaceBlocking` |
| Lecteur : vitesse, compte à rebours avant l'épisode suivant, programme suivant en direct, erreurs avec actions, infos techniques, chaîne précédente | fait, non testé sur appareil | `PlayerActivity.kt` |
| Direct : chaînes récentes, masquage de groupes, numéros de chaîne | fait, non testé sur appareil | `ui/LiveScreen.kt`, `Repo` |
| Sources : expiration et connexions du compte Xtream, renommer, réordonner | fait, non testé sur appareil | `ui/ProfileScreen.kt`, `XtreamJson.accountInfo` |
| Recherche sur le catalogue stocké : sans accents, classée, paginée, historique local | fait, non testé sur appareil | `data/TextNormalizer.kt`, `CatalogStore.search*`, `ui/SearchScreen.kt` |
| Lint et tests en CI, « latest » protégée | fait | `.github/workflows/build.yml` |

## Reste à faire en P0

1. Validation manuelle sur téléphone et Android TV de tout le tableau ci-dessus.
2. Interface Android TV dédiée (barre latérale, focus initial, restauration du focus).
3. Chaînes d'interface en ressources, français et anglais.
4. Chaînes et guide : toujours entièrement en mémoire (acceptable jusqu'à quelques dizaines de milliers
   de chaînes ; à mesurer).
5. Recherche : tolérance aux fautes de frappe, programmes du guide, acteurs et genres, filtres.
6. Tests de migration Room (schéma exporté) quand le schéma devra être conservé ; tests UI Compose.

## Décisions prises

- **Un seul module Gradle** pour l'instant : le découpage `core:*` / `feature:*` attendra la séparation
  mobile / TV.
- **Room sert de cache**, pas de source de vérité des données utilisateur : celles-ci restent dans les
  préférences chiffrées, ce qui a permis une migration sans perte et sans changement de format.
- **Enveloppe de clés** (clé de données + Keystore) plutôt qu'un appel Keystore par valeur : chiffrer
  l'URL de chaque entrée M3U resterait trop lent autrement.
- **Les URLs Xtream ne sont pas stockées** en base : elles sont reconstruites depuis la source, ce qui
  évite de dupliquer les identifiants et rend le changement de format instantané.
- **Durées de validité** : catalogue 12 h, guide 6 h. Non configurables pour l'instant.
- **Recherche par `LIKE` sur une colonne normalisée** plutôt que FTS : suffisant pour des noms courts,
  sans index à maintenir, et vérifiable par des tests sur base en mémoire. À réévaluer par la mesure sur
  une source de 100 000 titres.
- **Pagination manuelle (limite / décalage)** plutôt que la bibliothèque Paging : même mécanisme que la
  recherche, peu de code, testé sur base en mémoire. Les pages déjà parcourues restent en mémoire tant
  que l'écran est dans la pile de navigation.
- **État des écrans conservé dans la pile de navigation** (`SaveableStateHolder` + `CatalogGridState`) :
  revenir d'une fiche retrouve la grille, son tri et sa position.
- **Actualisation séquentielle dans une seule transaction** : les trois listes Xtream (direct, films,
  séries) sont téléchargées l'une après l'autre et non plus en parallèle. C'est plus lent sur le réseau,
  mais la mémoire reste plate et le remplacement est atomique. La base est en mode WAL pour que la
  lecture reste possible pendant l'écriture.
- **Films ou séries indisponibles chez le fournisseur** : toléré tant que rien n'avait été stocké pour ce
  type ; sinon l'actualisation échoue et l'ancien catalogue est conservé, plutôt que de le vider.
- **Lint** : la fiche détaillée charge ses données avec `LaunchedEffect` ; `produceState` y déclenchait un
  faux positif de la règle `ProduceStateDoesNotAssignValue`.

## Points d'attention

- **Le lecteur a été réécrit sans pouvoir être lancé** : c'est la partie la plus exposée à un défaut
  invisible en CI (aucun test ne couvre l'affichage ni la lecture). À vérifier en premier sur appareil.

- **Signature des APK** : les builds de debug sont signés avec la clé fixe `app/debug.keystore`
  (mot de passe standard `android`), versionnée dans ce dépôt privé. Tout build à partir du commit qui
  l'introduit s'installe par-dessus le précédent. Ce n'est pas une clé de production. La 0.1.0 a été
  signée avec une clé jetable : il faut la désinstaller une fois, donc la migration de ses données ne
  peut être vérifiée que par le test unitaire, pas sur l'appareil de l'utilisateur.
- **Schéma Room en version 2** (colonne `search`) : le cache est reconstruit à la mise à jour, ce qui
  déclenche un rechargement réseau au premier lancement.
- **Récupération des APK** : depuis l'environnement de développement, les artefacts d'Actions ne sont pas
  téléchargeables ; seuls les fichiers de release le sont. L'utilisateur peut télécharger l'artefact
  `ArkaTV-debug` depuis la page du run.
- `Repo` reste un singleton ; `PlayerActivity` ne l'initialise pas et suppose que `MainActivity` l'a fait.

## Prochaine action

Faire valider le build sur appareil. Ensuite : interface Android TV dédiée (point 2), en s'appuyant sur
les maquettes Claude Design que l'utilisateur prépare pour mobile et TV, puis refonte des écrans de P1.

## Extension multimédia (téléchargements, PVR, timeshift, Cast, ArkaTV Connect)

Intégrée à `ROADMAP.md` en P2 (timeshift) et P4. Rien n'est commencé ; aucune action correspondante
n'est affichée dans l'application.
