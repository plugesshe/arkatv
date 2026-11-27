# Roadmap

Légende : ✅ fait et vérifié en CI · 🟡 partiel · ⬜ à faire. Le détail de ce qui est réellement livré
est dans `PROGRESS.md`.

## P0 — Fondations

- ✅ Audit du code 0.1.0 et compilation de référence
- ✅ Stockage chiffré des identifiants et des URLs, migration des données 0.1.0
- ✅ Cache persistant du catalogue et du guide, démarrage sur le cache, actualisation en arrière-plan
- ✅ Tokens de design centralisés, nouvelle palette
- ✅ Tests unitaires (parseurs, chiffrement, migration, progression), lint et tests en CI
- 🟡 Navigation Android TV : focus visible sur les cartes et les lignes ; pas encore d'interface TV dédiée
- ⬜ Chaînes d'interface en ressources (français, anglais)
- ✅ Signature stable des builds de debug
- ✅ Recherche sur le catalogue stocké : insensible aux accents, classée par pertinence, paginée, historique local
- ✅ Films et séries lus par pages depuis la base ; tris (défaut, récents, A–Z, année, note) dans les grilles
- ✅ Position et filtres conservés au retour d'une fiche
- ✅ Actualisation en flux, atomique : aucun catalogue complet en mémoire, ancien catalogue conservé en cas d'échec

## P1 — Refonte premium

Accueil VOD (hero choisi par score, rangées « mieux notés », « à découvrir »), écrans Films et Séries avec
tris et filtres, fiches enrichies (vu/non vu, recommencer, similaires), interface TV avec barre latérale,
recherche tolérante et paginée, lecteur (vitesse, épisode suivant avec compte à rebours, diagnostics),
gestion des sources (expiration, connexions, renommage).

## P2 — Excellence du direct

Interface TV à trois zones, overlay de zapping, chaînes récentes et précédente, masquage et
réorganisation, guide avec ligne « maintenant », jours, association XMLTV par nom, replay quand la
source le déclare, timeshift local.

## P3 — Différenciation

Profils locaux, recommandations par règles, séries suivies, contrôle parental par PIN, enrichissement
facultatif des métadonnées (clé fournie par l'utilisateur), mode Découverte.

## P4 — Fonctionnalités avancées

Téléchargements VOD et téléchargements intelligents, enregistrements (PVR), Google Cast, ArkaTV Connect
(appairage local téléphone ↔ TV), multiview, synchronisation facultative.

Chacune sera précédée d'une étude de faisabilité : compatibilité des flux, limites de connexions du
fournisseur, contraintes Android d'arrière-plan. Aucune action n'apparaîtra dans l'interface avant
d'être opérationnelle.

## P5 — Finition

Accessibilité, tests prolongés sur appareils, captures d'écran, optimisation, version candidate.
