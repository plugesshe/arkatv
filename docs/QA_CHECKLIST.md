# Checklist qualité

## Automatisé (GitHub Actions, à chaque push)

- `testDebugUnitTest` : parseur M3U, parseur XMLTV (fuseaux, gzip, fichier tronqué), parseur Xtream
  (types hétérogènes, saisons en objet ou en tableaux), chiffrement, migration des données 0.1.0,
  règles de progression, cache Room (aller-retour, absence d'identifiants en base, remplacement par
  source), recherche (accents, classement, pagination), normalisation du texte.
- `lintDebug` : bloquant sur les erreurs.
- `assembleDebug`.

## Manuel (aucun de ces tests n'a encore été exécuté sur la branche de refonte)

L'environnement de développement n'a ni émulateur ni appareil : tout ce qui suit doit être vérifié sur
un vrai téléphone et une vraie Android TV.

### Mise à jour depuis 0.1.0
- [ ] Installer la nouvelle APK par-dessus 0.1.0 : les sources, favoris, « Ma liste » et « Reprendre la
      lecture » sont toujours là.
- [ ] La lecture d'un contenu de « Reprendre la lecture » fonctionne.

### Démarrage et cache
- [ ] Premier lancement après mise à jour : chargement réseau, puis catalogue affiché.
- [ ] Fermer et relancer : le catalogue apparaît sans attente réseau.
- [ ] Mode avion : le catalogue et le guide restent consultables ; la lecture échoue avec un message.
- [ ] Mon espace affiche la date de dernière actualisation ; le bouton d'actualisation la met à jour.
- [ ] Couper le réseau puis actualiser : message d'échec, catalogue conservé.

### Sources
- [ ] Ajout Xtream avec de mauvais identifiants : message clair, rien n'est enregistré.
- [ ] Ajout M3U ; suppression d'une source ; bascule entre deux sources.
- [ ] Changement de format MPEG-TS / HLS : effet immédiat, sans rechargement.

### Catalogue
- [ ] L'accueil affiche les rangées par catégorie au fil du défilement, sans catégorie vide.
- [ ] « Tout voir » ouvre la grille ; faire défiler charge la suite jusqu'au bout de la catégorie.
- [ ] Chaque tri (défaut, ajouts récents, A – Z, année, note) réordonne la grille.
- [ ] Ouvrir une fiche depuis le milieu d'une grille puis revenir : même position, même tri.
- [ ] Ouvrir une fiche depuis le bas de l'accueil puis revenir : même position.

### Recherche
- [ ] « heritiers » trouve « Les Héritiers du Vent » ; deux mots dans le désordre fonctionnent.
- [ ] Une recherche large charge la suite en faisant défiler les affiches ; « Afficher plus de chaînes ».
- [ ] Ouvrir un résultat ajoute la recherche à l'historique ; « Effacer l'historique » le vide.

### Lecture
- [ ] Film, épisode, chaîne en direct, zapping, pistes audio et sous-titres, mini-lecteur.
- [ ] Vitesse de lecture sur un film ; absente en direct.
- [ ] Fin d'épisode : compte à rebours, « Lire maintenant », « Annuler ».
- [ ] En direct : programme en cours, « Ensuite », bouton « Chaîne précédente » après un zapping.
- [ ] Couper le réseau pendant la lecture : message, puis « Réessayer » une fois le réseau revenu.
- [ ] Infos techniques : les valeurs correspondent au flux.

### Direct et sources
- [ ] Regarder deux chaînes : le groupe « Récentes » apparaît, dans l'ordre.
- [ ] Masquer un groupe : il disparaît des puces et de « Toutes » ; le réafficher.
- [ ] Source Xtream : date d'expiration et connexions affichées après une actualisation.
- [ ] Renommer une source, la monter et la descendre dans la liste.

### Android TV
- [ ] Toutes les cartes et lignes montrent un contour au focus.
- [ ] Accueil → fiche → retour ; guide ; ajout d'une source au clavier virtuel.
- [ ] Lecteur : OK, haut/bas, gauche/droite, CH+/CH-.

### Charge
- [ ] Source volumineuse : temps de démarrage sur cache et mémoire (voir `PERFORMANCE.md`).
