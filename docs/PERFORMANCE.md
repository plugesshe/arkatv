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

- Après lecture du cache, tout le catalogue est en mémoire : sur une source de 100 000 films, le temps
  de lecture et la mémoire resteront élevés. La correction est la pagination en base (P0 restant).
- Les URLs des playlists M3U sont déchiffrées une à une au chargement du cache.
- L'accueil regroupe et trie les listes en mémoire à chaque changement de catalogue.
- La recherche interroge la base par `LIKE` sur une colonne normalisée : chaque requête parcourt la
  table de la source. Temps non mesuré sur gros volume.
- Volumes cibles (20 000 chaînes, 100 000 films, 30 000 séries) : aucun test de charge exécuté.
