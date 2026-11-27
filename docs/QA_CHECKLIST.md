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

### Design, profils, Découverte
- [ ] Accueil : le hero change toutes les 8 secondes en fondu ; sur TV il s'arrête quand le focus y est.
- [ ] Catalogue mobile : « Catégorie » et « Tri » ouvrent une feuille ; compteur « n / total » ; les deux
      sens de tri ; « Effacer le filtre ».
- [ ] Catalogue TV : panneau de catégories à gauche avec compteurs, tri par la puce.
- [ ] Découvrir : choisir des catégories, « Autre tirage » propose un autre titre, « Lire » le lance.
- [ ] Profils : créer un deuxième profil, vérifier que favoris, liste et reprise sont vides pour lui et
      intacts pour le premier ; « Qui regarde ? » au lancement suivant ; renommer, supprimer.
- [ ] Squelettes animés pendant le chargement des rangées et des grilles.

### Fiches
- [ ] Film entamé : « Reprendre · reste N min », barre de progression, « Recommencer » repart de zéro.
- [ ] « Marquer vu » retire le film de « Continuer à regarder » ; un film fini passe à « Vu ».
- [ ] Série : le bouton rouge propose l'épisode en cours, sinon le premier non vu ; onglets de saison ;
      badge « VU », durée et temps restant par épisode ; « Suivre » ajoute la rangée « Séries suivies ».
- [ ] « Contenus similaires » ouvre une autre fiche ; retour revient à la première.
- [ ] TV : focus initial sur le bouton rouge ; série en deux colonnes.

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
- [ ] Appui long sur une chaîne (tactile, puis OK maintenu à la télécommande) : favori, monter/descendre
      dans les favoris, masquer ; « Gérer les groupes » permet de réafficher les chaînes masquées.
- [ ] Lecteur : « Épisodes » et « Chaînes » ouvrent la liste sur l'élément en cours et changent de contenu.
- [ ] Lecteur : « Qualité » n'apparaît que sur un flux à plusieurs qualités ; « Automatique » y revient.
- [ ] Actualisation automatique sur « Jamais » : pas de rechargement à l'ouverture ; le bouton manuel marche.

### Android TV
- [ ] Toutes les cartes et lignes montrent un contour au focus.
- [ ] Accueil → fiche → retour ; guide ; ajout d'une source au clavier virtuel.
- [ ] Lecteur : OK, haut/bas, gauche/droite, CH+/CH-.

### Charge
- [ ] Source volumineuse : temps de démarrage sur cache et mémoire (voir `PERFORMANCE.md`).
