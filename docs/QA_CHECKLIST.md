# Checklist qualité

## Automatisé (GitHub Actions, à chaque push)

- `testDebugUnitTest` : parseur M3U, parseur XMLTV (fuseaux, gzip, fichier tronqué), parseur Xtream
  (types hétérogènes, saisons en objet ou en tableaux), chiffrement, migration des données 0.1.0,
  règles de progression.
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

### Lecture
- [ ] Film, épisode (enchaînement), chaîne en direct, zapping, pistes audio et sous-titres, mini-lecteur.

### Android TV
- [ ] Toutes les cartes et lignes montrent un contour au focus.
- [ ] Accueil → fiche → retour ; guide ; ajout d'une source au clavier virtuel.
- [ ] Lecteur : OK, haut/bas, gauche/droite, CH+/CH-.

### Charge
- [ ] Source volumineuse : temps de démarrage sur cache et mémoire (voir `PERFORMANCE.md`).
