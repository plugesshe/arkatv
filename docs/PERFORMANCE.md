# Performance

## Objectifs (appareil de référence à définir)

| Objectif | Cible | Mesuré |
| --- | --- | --- |
| Démarrage avec cache exploitable | < 2 s | non mesuré |
| Recherche locale | < 150 ms | non mesuré |
| Fluidité de navigation | proche de 60 FPS | non mesuré |
| Aucun ANR en charge | 0 | non mesuré |

Aucune mesure n'a été faite : l'environnement de développement ne dispose ni d'émulateur ni d'appareil.
Les chiffres devront être relevés sur un téléphone et une Android TV réels, en notant le modèle, la
version d'Android et la taille de la source.

## Ce qui a changé en P0

- Le catalogue n'est plus retéléchargé à chaque lancement : il est relu depuis la base locale, puis
  actualisé en arrière-plan au plus toutes les 12 heures (guide : 6 heures).
- Changer le format du direct ne déclenche plus de rechargement réseau.
- Le parsing Xtream et XMLTV se fait en flux, hors du thread principal.

## Limites connues

- Les films et séries ne sont plus chargés en mémoire : l'accueil lit 20 titres par rangée à l'affichage,
  les grilles 60 par page. Les chaînes et le guide restent entièrement en mémoire.
- Pendant une actualisation, les entrées sont écrites en base par lots de 500 au fil du téléchargement :
  la mémoire ne dépend plus de la taille de la source. En contrepartie, les listes Xtream sont
  téléchargées l'une après l'autre.
- Les URLs des chaînes M3U sont déchiffrées une à une au chargement du cache ; celles des films M3U,
  page par page.
- La recherche interroge la base par `LIKE` sur une colonne normalisée : chaque requête parcourt la
  table de la source. Temps non mesuré sur gros volume.
- Volumes cibles (20 000 chaînes, 100 000 films, 30 000 séries) : aucun test de charge exécuté.

## Fluidité sur Android TV (27/11/2026)

- Les builds publiés sont désormais du type `release`, non débogable : ART compile le code et installe
  les profils de démarrage fournis par Compose. Un build débogable est interprété et saccade.
- R8 n'est pas activé : à activer après une validation sur appareil, car une règle manquante casserait
  le lancement et la mise à jour intégrée ne pourrait pas réparer.
- Images : une requête par image mémorisée, cache disque de 256 Mo indépendant des en-têtes du
  fournisseur, pas de fondu sur TV, titre de secours dessiné seulement si l'image manque.
- Recherche dans le lecteur à la télécommande sur l'image clé la plus proche.
- Aucune mesure chiffrée n'a été prise.
