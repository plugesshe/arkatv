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

### Titres
- Les titres sont affichés sans les marques du fournisseur (« FR - », « 4K-D+ - », année et pays entre
  parenthèses) : affiches, mise en avant, fiches, reprises, épisodes. La recherche continue de porter
  sur le nom d'origine.

### Android TV
- Mise en avant de l'accueil : revenir dessus à la télécommande la réaffiche en entier, le bord gauche
  de l'image ne se voit plus, bouton « Ma liste » avec son libellé.
- Le tremblement de l'écran au changement de tuile est corrigé : l'agrandissement au focus ne modifie
  plus la position réelle de l'élément, donc les listes ne se recalent plus pendant l'animation.
- Son de navigation au passage d'un élément à l'autre (suit le réglage « sons système » de l'appareil).
- Reprise d'un épisode : la saison est rechargée, « Épisodes » et l'épisode suivant sont disponibles ;
  flèche du bas pour ouvrir les épisodes, commandes affichées ou non.
- Accueil : le haut de la mise en avant n'est plus coupé.

### Contrôle parental
- Code à quatre chiffres (seule une empreinte salée est conservée, chiffrée).
- Profils enfant : les catégories choisies (direct, films, séries) leur sont masquées partout, y compris
  dans la recherche et la découverte ; pas d'accès aux sources ni aux paramètres.
- Le code est demandé pour quitter un profil enfant, gérer les profils et modifier ces réglages.

- Build optimisé (non débogable) : l'interface est compilée par l'appareil au lieu d'être interprétée.
- Lecteur à la télécommande : flèches gauche / droite pour avancer ou reculer (de plus en plus vite si la
  touche reste enfoncée), OK pour la pause, barre de progression utilisable au focus, touches multimédia.
- Les panneaux (épisodes, chaînes, audio et sous-titres, réglages) prennent le focus à l'ouverture ;
  Retour ferme le panneau, puis les commandes, puis le lecteur.
- Les commandes restent affichées en pause et tant que la télécommande est utilisée.
- Barre latérale : OK entre dans la section, Retour revient à la barre, puis à l'accueil.
- Retour d'une fiche : le focus revient sur l'affiche qui avait été ouverte.
- Affiches gardées sur disque, sans fondu sur TV.

### Lecteur
- Épisode suivant proposé une minute avant la fin (« Lire maintenant » ou « Masquer »), enchaînement
  automatique à la fin.
- Téléphone : verrouillage de l'écran, et deux doigts pour remplir l'écran ou revenir à l'image entière.

### Catalogue (filtres)
- Filtres « Note ≥ 7 », par période d'années, et « Non vus » pour les films des sources Xtream.

### Mises à jour
- Mise à jour intégrée : vérification au lancement (désactivable) ou manuelle, téléchargement avec contrôle
  SHA-256, puis installeur système. Rien n'est installé sans confirmation.

### Sources
- Ajout d'une source en trois étapes : type, connexion vérifiée avant enregistrement, résumé du contenu trouvé.
- Identifiants refusés distingués d'un serveur injoignable.
- Cartes de sources : état, nombre de films, séries et chaînes, expiration, connexions, dernière actualisation.
- Bandeau « Hors connexion · catalogue en cache » avec « Réessayer », et indication d'actualisation en arrière-plan.

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

### Direct et guide TV
- Liste des chaînes redessinée : numéro, logo, programme en cours, avancement et heure de fin.
- Android TV : espace Direct en trois zones (groupes, chaînes, détail du programme sous le focus).
- Guide TV : ligne rouge « maintenant », programmes passés atténués, bouton de retour à maintenant,
  détail du programme en feuille (mobile) ou en tête d'écran (TV).

### Lecteur, recherche et Mon espace (nouveau design)
- Lecteur redessiné : commandes minimales, panneaux audio / sous-titres et réglages, liste des chaînes à
  gauche en direct, bandeau d'épisodes, carte « Épisode suivant ».
- Gestes sur mobile : double toucher ±10 s cumulable, appui long en 2×, glisser horizontal pour chercher,
  glisser vertical pour le volume (à droite) ou la luminosité (à gauche).
- Recherche redessinée : filtres avec compteurs, correspondance surlignée, recherches récentes
  supprimables une à une, genres ; grille de lettres pilotable à la télécommande sur Android TV.
- Mon espace réorganisé : profil, séries suivies, puis Ma liste, Historique, Sources et Paramètres.

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
