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

### Direct et guide (nouveau design)
- [ ] Direct mobile : numéro, logo, programme, barre d'avancement, heure de fin ; appui long = feuille d'actions.
- [ ] Direct TV : groupes à gauche, chaînes au centre, détail à droite qui suit le focus.
- [ ] Guide : ligne rouge alignée sur l'heure dans toutes les rangées ; « Maintenant » y ramène.
- [ ] Guide : programmes passés atténués ; toucher un programme ouvre son détail ; en cours = lecture.
- [ ] Guide TV : le détail en tête suit le focus.

### Lecteur, recherche, Mon espace (nouveau design)
- [ ] Lecteur mobile : un toucher affiche / masque les commandes ; double toucher gauche / droite = ∓10 s
      cumulés ; appui long = 2× tant que maintenu ; glisser horizontal = recherche ; vertical droite =
      volume, gauche = luminosité. Vérifier qu'un glisser ne déclenche pas aussi un toucher.
- [ ] Lecteur : « Audio · ST » et « ⋯ » ouvrent un panneau à droite ; toucher hors du panneau le ferme.
- [ ] Lecteur direct : « Chaînes » ouvre la liste à gauche ; bandeau « Épisodes » pour une série.
- [ ] Lecteur TV : focus sur Lecture / Pause à l'affichage ; Retour masque les commandes puis quitte.
- [ ] Recherche : filtres avec compteurs, mot cherché en rouge dans les titres, × sur une recherche récente.
- [ ] Recherche TV : la grille de lettres compose la recherche ; Effacer retire une lettre.
- [ ] Mon espace : menu, puis Sources, Historique (reprendre, effacer) et Paramètres ; Retour revient au menu.

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

## Ajout et gestion des sources (0.2.0-dev, build 13) — à vérifier sur appareil

- [ ] Premier lancement : étape 1, choix Xtream / M3U, « Continuer ».
- [ ] Adresse en http:// : avertissement affiché, ajout non bloqué.
- [ ] Mauvais mot de passe : « Identifiants refusés » ; serveur inexistant : « Connexion impossible ».
- [ ] Étape 3 : compteurs cohérents, « Commencer » actif seulement une fois le catalogue chargé.
- [ ] Playlist M3U invalide : « Source inaccessible », « Modifier la source » revient au formulaire.
- [ ] TV : tout le parcours à la télécommande, saisie au clavier virtuel.
- [ ] Mon espace → Sources : état, actualiser, renommer, supprimer.
- [ ] Mode avion avec catalogue en cache : bandeau « Hors connexion », « Réessayer » relance.

## Mise à jour intégrée — à vérifier sur appareil

- [ ] Paramètres → Mises à jour : version installée affichée, « Vérifier maintenant » répond.
- [ ] Build plus récent publié : fenêtre au lancement, « Installer » télécharge puis ouvre l'installeur.
- [ ] Première fois : Android demande d'autoriser ArkaTV à installer des applications.
- [ ] TV : fenêtre utilisable à la télécommande, « Plus tard » la ferme.
- [ ] Après installation : sources, favoris et progression conservés.
- [ ] Vérification au lancement désactivée : aucune requête ni fenêtre.

## Lecteur et navigation sur TV — à vérifier sur appareil

- [ ] Film, commandes masquées : gauche / droite déplacent la lecture, la barre compacte s'affiche ; touche tenue : de plus en plus vite.
- [ ] OK met en pause et affiche les commandes ; elles restent affichées en pause.
- [ ] Commandes affichées : le focus est sur Pause ; haut atteint la barre de progression, gauche / droite y déplacent la lecture.
- [ ] « Épisodes » : le focus est sur l'épisode en cours ; Retour ferme le panneau et rend le focus aux commandes.
- [ ] Une minute avant la fin d'un épisode : carte « Épisode suivant », OK lance le suivant, Retour la masque.
- [ ] Fin d'épisode : le suivant démarre seul.
- [ ] Direct : haut / bas changent de chaîne, gauche ouvre la liste avec le focus sur la chaîne en cours.
- [ ] Barre latérale : OK entre dans la section ; Retour dans le contenu ramène à la barre.
- [ ] Retour d'une fiche : le focus revient sur l'affiche ouverte.
- [ ] Téléphone : verrouillage, déverrouillage, zoom à deux doigts.
- [ ] Films : filtres « Note ≥ 7 », années, « Non vus ».

## Contrôle parental — à vérifier sur appareil

- [ ] Paramètres › Contrôle parental : définir un code (saisie deux fois), le changer, le supprimer.
- [ ] Saisie du code à la télécommande (flèches + OK) et aux touches numériques.
- [ ] Catégories masquées : cocher des catégories de direct, de films et de séries.
- [ ] Profil enfant : ces catégories n'apparaissent ni dans les listes, ni à l'accueil, ni dans la recherche.
- [ ] Profil enfant : pas d'entrée Sources ni Paramètres ; code demandé pour passer à un autre profil.
- [ ] Changement de tuile sur TV : plus de tremblement ; son de navigation présent.
- [ ] Reprise d'un épisode depuis l'accueil : « Épisodes » apparaît, flèche du bas ouvre les épisodes.

## TMDB — à vérifier sur appareil

- [ ] Paramètres › Catalogue enrichi : « Saisir depuis un autre appareil » affiche une adresse et un code.
- [ ] La page s'ouvre sur un téléphone du même réseau ; mauvais code refusé ; clé acceptée, la page se ferme.
- [ ] Le compteur « titres reconnus » monte ; l'accueil gagne des rangées (du moment, mieux notés, genres).
- [ ] Une œuvre présente en plusieurs versions n'apparaît qu'une fois dans ces rangées ; la fiche propose « Versions ».
- [ ] Étiquettes langue / qualité sur les affiches des catégories du fournisseur.
- [ ] « Accueil organisé par ArkaTV : non » rétablit les catégories du fournisseur.
- [ ] Profil enfant : aucune rangée ne montre un titre d'une catégorie masquée.

## Lot « catalogue ArkaTV, filtres, groupes » — à vérifier sur appareil

- [ ] Films / Séries : genres TMDB, tri par popularité, « Non reconnus », bouton « Organisation ».
- [ ] Filtres Langue et Qualité, dans les deux organisations.
- [ ] Une œuvre en plusieurs versions : la tuile ouvre la version française, « Versions » propose les autres.
- [ ] Épisode suivant : carte une minute avant la fin, dix secondes, puis épisode suivant ; « Masquer » laisse finir.
- [ ] Direct : appui long sur une chaîne › « Nouveau groupe… », puis ajout d'autres chaînes ; le groupe apparaît dans le guide.
