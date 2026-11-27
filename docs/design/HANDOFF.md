# ArkaTV — dossier de transmission

## Contenu
- `tokens/tokens.json` — couleurs, typographie, espacements, rayons, tailles, mouvement (source unique).
- `android/res/values/colors.xml`, `dimens.xml` — ressources Android (Views / Leanback).
- `android/ArkaTheme.kt` — mêmes tokens pour Jetpack Compose (mobile + Compose for TV).
- `icons/*.svg` — 55 icônes, grille 24, trait 2, `currentColor`. Importer via Vector Asset Studio. TV : 36 px, trait 2,5.
- `logo/` — marque, icône d'application, avant-plan adaptatif 108 dp, bannière lanceur TV 320×180.

## Conversion TV
Les maquettes TV sont en px à 1920×1080. Android TV = 960×540 dp : **diviser toutes les valeurs TV par 2** pour obtenir des dp/sp.

## Polices
Archivo (variable, axes wdth 62–125 et wght) et JetBrains Mono — Google Fonts, licence OFL. Titres d'affiche : Archivo 800, largeur 72 %. Logotype : 800, largeur 112 %.

## Correspondance maquettes → fonctionnalités
| Fichier | Contenu |
|---|---|
| ArkaTV Design System | tokens, logo, composants et leurs états, icônes |
| Écrans 1 — Démarrage et sources | démarrage, profils, ajout Xtream, HTTP, validation, erreur, gestion des sources |
| Écrans 2 — Films et séries | accueil, catalogue, fiche film, fiche série, Découverte |
| Écrans 3 — Direct et guide | chaînes, espace Direct TV, organisation, guide, multiview |
| Écrans 4 — Lecteur | VOD, épisode suivant, mini-lecteur, direct, timeshift, diagnostics, erreurs, gestes |
| Écrans 5 — Recherche et Mon espace | recherche, Mon espace, téléchargements, enregistrements, parental, paramètres lecture |
| Écrans 6 — Appareils | Google Cast, ArkaTV Connect |
| Écrans 7 — États et variantes | matrice 7 écrans × 8 états, états TV, actions avec / sans, interactions, contenus extrêmes |
| Écrans 8 — Compléments | Ma liste, historique, M3U, paramètres détaillés, recherche TV vide, voix |
| Spécifications | grille adaptative, focus TV, mouvement, gestes, disponibilité des actions, accessibilité |
| Prototype TV | navigation télécommande (flèches, OK, Retour) |
| Prototype Lecteur mobile | gestes du lecteur |

## Règles clés
- Rouge réservé : action principale, sélection, progression, direct. Texte rouge = #FF3457.
- Focus TV : contour #F5F5F7 3 px (décalage 3 px) + scale par `graphicsLayer`, jamais par la mise en page.
- Une action indisponible est retirée ; seule « Lire » hors connexion reste visible, désactivée et motivée.
- Pas de téléchargement sur Android TV.
- Images : les zones rayées des maquettes = visuels fournis par la source (affiche 2:3, fond 16:9). Repli sans image : titre typographique sur #18181F.
