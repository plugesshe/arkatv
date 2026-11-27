# Journal d'avancement

Document de reprise : une nouvelle session doit pouvoir repartir d'ici sans autre contexte.

## État au 27/11/2026

- Branche de travail : `claude/arkatv-excellence` (créée depuis `claude/arkatv-v1`, la 0.1.0 validée par
  l'utilisateur). Rien n'est poussé sur `main`. Aucune release publiée depuis cette branche.
- Version : `0.2.0-dev` (versionCode 2).
- CI : tests unitaires, lint et assemblage passent (run 45990509451).
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
| Tests unitaires | 23 tests, 5 classes | `app/src/test/` |
| Lint et tests en CI, « latest » protégée | fait | `.github/workflows/build.yml` |

## Reste à faire en P0

1. **Signature stable des builds** (bloquant pour tester la migration, voir plus bas).
2. Validation manuelle sur téléphone et Android TV de tout le tableau ci-dessus.
3. Pagination du catalogue en base (Paging 3) et recherche indexée : le catalogue est encore chargé
   entièrement en mémoire.
4. Interface Android TV dédiée (barre latérale, focus initial, restauration du focus).
5. Chaînes d'interface en ressources, français et anglais.
6. Tests de migration Room (schéma exporté) quand le schéma évoluera ; tests UI Compose.

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
- **Lint** : la fiche détaillée charge ses données avec `LaunchedEffect` ; `produceState` y déclenchait un
  faux positif de la règle `ProduceStateDoesNotAssignValue`.

## Points d'attention

- **Signature des APK** : chaque exécution de GitHub Actions signe l'APK de debug avec une clé générée
  pour l'occasion. Android refuse donc d'installer un build par-dessus un autre ; il faut désinstaller,
  ce qui efface les sources. Tant qu'une clé de signature stable n'est pas en place, la migration depuis
  la 0.1.0 ne peut pas être testée sur appareil. Décision attendue de l'utilisateur.
- **Récupération des APK** : depuis l'environnement de développement, les artefacts d'Actions ne sont pas
  téléchargeables ; seuls les fichiers de release le sont. L'utilisateur peut télécharger l'artefact
  `ArkaTV-debug` depuis la page du run.
- `Repo` reste un singleton ; `PlayerActivity` ne l'initialise pas et suppose que `MainActivity` l'a fait.

## Prochaine action

Mettre en place la signature stable (selon la décision de l'utilisateur), faire valider le build sur
appareil, puis attaquer le point 3 (pagination et recherche indexée) avant toute refonte d'écran de P1.

## Extension multimédia (téléchargements, PVR, timeshift, Cast, ArkaTV Connect)

Intégrée à `ROADMAP.md` en P2 (timeshift) et P4. Rien n'est commencé ; aucune action correspondante
n'est affichée dans l'application.
