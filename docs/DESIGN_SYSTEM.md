# Design system

Source : le projet Claude Design « ArkaTV » (design system, 8 planches d'écrans, 2 prototypes,
spécifications). Le dossier de transmission est repris dans `docs/design/` (`tokens.json`, `HANDOFF.md`).
Dans le code, tout passe par `app/src/main/java/tv/arka/app/ui/Theme.kt` et `Components.kt` : aucune
couleur, aucun rayon, aucune taille de texte ne doit être déclaré dans un écran.

## Ce qui est repris tel quel du handoff

| Élément | Où |
| --- | --- |
| Couleurs | `ArkaColors` (`Theme.kt`), `res/values/colors.xml` |
| Espacements, rayons, tailles | `ArkaSpacing`, `ArkaRadius`, `ArkaSize`, `res/values/dimens.xml` |
| Typographie mobile et TV | `ArkaTheme.type` (bascule automatique selon l'appareil) |
| Polices Archivo (variable) et JetBrains Mono | `res/font/`, licences OFL dans `licenses/` |
| Mouvement et focus | `ArkaMotion`, `Modifier.focusHighlight()` |
| 55 icônes (grille 24, trait 2) | `res/drawable/ic_arka_*.xml` |
| Logo (arche), icône adaptative, bannière TV | `arka_mark.xml`, `ic_launcher_foreground.xml`, `drawable-xhdpi/tv_banner.png` |

Conversion TV : les maquettes sont en px à 1920×1080, Android TV fait 960×540 dp ; toutes les valeurs TV
sont divisées par 2 (`ArkaSize`, échelle typographique TV).

## Règles

- **Rouge** : action principale, sélection, progression, direct. Texte ou icône rouge = `RedBright`.
- **Un seul bouton primaire rouge par écran** (`PrimaryButton`). Le reste : `SecondaryButton`, `SquareButton`.
- **Puces** (`ArkaChip`) : sélection = fond clair + coche, jamais la couleur seule.
- **Cartes** : affiche 2:3 ou vignette 16:9, titre et métadonnées sous l'image. Sans image, titre
  typographique sur `Surface`.
- **Focus TV** : contour clair de 3 dp décalé de 3 dp, agrandissement 1,06 (cartes) ou 1,04 (boutons)
  par transformation, voisins immobiles. `focusHighlight()` se place avant `clickable`.
- **Action indisponible** : retirée, pas grisée.
- **Marges** : `ArkaTheme.margin` (16 dp mobile, 40 dp TV), `ArkaTheme.rowGap` entre rangées.

## Composants en place

`Logo`, `ArkaIcon`, `PrimaryButton`, `SecondaryButton`, `SquareButton`, `ArkaChip`, `ArkaBadge`,
`SectionTitle`, `ScreenHeader`, `Poster`, `VodRow`, `ChannelLogo`, `ChannelRow`, barre inférieure mobile,
barre latérale TV.

## Écarts connus avec les maquettes

- Les écrans autres que l'accueil, la navigation et les grilles n'ont pas encore été redessinés : ils
  héritent des couleurs, des polices et des puces, pas de leur mise en page (voir `PROGRESS.md`).
- Tailles de texte encore écrites en dur dans les écrans non refondus.
- Squelettes sans lueur animée ; pas de fondu d'apparition des images ; hero fixe (pas de rotation 8 s).
- Réglage « Animations réduites » non pris en compte.
- États pressés (échelle 0,97) appliqués au bouton primaire uniquement par la couleur.
