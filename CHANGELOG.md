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
- Actualisation écrite en base au fil du téléchargement : la mémoire ne dépend plus de la taille de la source.

### Catalogue
- Grilles triables : ordre par défaut, ajouts récents, A – Z, année, note.
- Les catégories vides ne sont plus proposées.
- Revenir d'une fiche retrouve la grille, son tri et sa position ; l'accueil garde sa position.

### Recherche
- Recherche insensible aux accents, à la casse et à la ponctuation, sur plusieurs mots.
- Résultats classés par pertinence et chargés par pages, sans plafond.
- Historique local des recherches, effaçable.

### Lecteur
- Vitesse de lecture pour les films et séries.
- Compte à rebours avant l'épisode suivant, annulable.
- En direct : programme en cours avec avancement, programme suivant, retour à la chaîne précédente.
- Messages d'erreur compréhensibles avec Réessayer, Chaîne suivante et Fermer.
- Panneau d'infos techniques (codec, résolution, débit, tampon, images perdues).
- Sélecteur d'épisodes (ou de chaînes en direct) dans le lecteur.
- Choix de la qualité quand le flux en propose plusieurs.

### Direct
- Groupe « Récentes », masquage des groupes inutiles, numéro de chaîne affiché.
- Appui long sur une chaîne : favori, ordre dans les favoris, masquer la chaîne.

### Sources
- Date d'expiration et connexions du compte Xtream.
- Renommer et réordonner les sources.
- Fréquence d'actualisation automatique réglable (6 h, 12 h, 24 h, jamais).

### Identité et design system (maquettes Claude Design)
- Nouveau logo (l'arche), icône d'application et bannière Android TV.
- Polices Archivo et JetBrains Mono, palette, rayons et échelle typographique du design system.
- 55 icônes ArkaTV ; boutons, puces et cartes redessinés.
- Accueil refait : hero en fondu, titres condensés, cartes avec titre et note sous l'affiche.
- Barre de navigation mobile redessinée ; barre latérale rétractable sur Android TV avec Films, Séries
  et Ma liste en accès direct.

### Profils
- Profils locaux (jusqu'à 5) : favoris, « Ma liste », reprise de lecture, chaînes récentes et recherches
  propres à chacun. Écran « Qui regarde ? » au lancement dès qu'il y a plusieurs profils.

### Découverte
- Mode Découverte : type, catégories, puis tirage au sort d'un titre parmi les correspondances.

### Accueil et catalogue
- Hero tournant entre les titres récents (fondu, pause quand la télécommande est dessus).
- Catalogue : choix de la catégorie avec compteurs, tri par date d'ajout, nom, année ou note, dans les
  deux sens ; panneau de catégories sur Android TV.
- Squelettes de chargement animés ; apparition des images en fondu.

### Fiches film et série
- Fiches redessinées, avec une mise en page dédiée à Android TV.
- « Reprendre » avec le temps restant, « Recommencer », « Marquer vu » ; un titre lu jusqu'au bout est
  marqué vu automatiquement.
- Séries : le bouton principal propose le bon épisode (en cours, sinon premier non vu), onglets de saison,
  durée et état de chaque épisode, « Suivre » (rangée « Séries suivies » sur l'accueil).
- Rangée « Contenus similaires ».

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
