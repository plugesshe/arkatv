# ArkaTV

Lecteur IPTV Android (téléphone, utilisable sur Android TV) pour vos propres sources.
L'application ne fournit aucun contenu.

- Sources : Xtream Codes et playlists M3U, guide XMLTV
- Films et séries : accueil, catégories, fiche détaillée, saisons et épisodes, reprise de lecture, « Ma liste »
- Direct : catégories, favoris, programme en cours, guide TV en grille
- Lecteur : Media3/ExoPlayer, pistes audio et sous-titres, zapping, mini-lecteur (PiP)

## Compiler

    ./gradlew assembleDebug

L'APK est produite dans `app/build/outputs/apk/debug/`. Chaque push la compile aussi via GitHub Actions
(artefact `ArkaTV-debug`).
