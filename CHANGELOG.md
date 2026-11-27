# Changelog

## 0.2.0-dev — en cours (branche `claude/arkatv-excellence`)

### Sécurité
- Sources, favoris, « Ma liste » et progression chiffrés (AES-256-GCM, clé protégée par Android Keystore).
- Migration automatique des données écrites en clair par la 0.1.0.
- Données exclues des sauvegardes cloud et des transferts d'appareil.
- Avertissement à l'ajout d'une source en HTTP.

### Performance
- Catalogue et guide TV conservés dans une base locale : démarrage sur le cache, actualisation en
  arrière-plan, dernier catalogue valide conservé en cas de panne.
- Changement de format du direct (MPEG-TS / HLS) sans rechargement.
- Films et séries lus par pages depuis la base locale au lieu d'être tous gardés en mémoire.

### Catalogue
- Grilles triables : ordre par défaut, ajouts récents, A – Z, année, note.
- Les catégories vides ne sont plus proposées.
- Revenir d'une fiche retrouve la grille, son tri et sa position ; l'accueil garde sa position.

### Recherche
- Recherche insensible aux accents, à la casse et à la ponctuation, sur plusieurs mots.
- Résultats classés par pertinence et chargés par pages, sans plafond.
- Historique local des recherches, effaçable.

### Interface
- Nouvelle palette et tokens de design centralisés.
- Contour de focus sur les cartes et les lignes pour la navigation à la télécommande.
- Mon espace : date de dernière actualisation et état de synchronisation.

### Qualité
- Tests unitaires et lint exécutés en CI.
- La pré-release « latest » n'est plus publiée que depuis la branche 0.1 validée.
- Builds de debug signés avec une clé fixe : une version s'installe par-dessus la précédente.

## 0.1.0 — 2026-11-27

Première version : sources Xtream Codes et M3U, accueil films et séries, fiches, direct, guide TV,
recherche, lecteur Media3.
