# Design system

Source unique : `app/src/main/java/tv/arka/app/ui/Theme.kt`. Aucune couleur, aucun rayon ne doit être
déclaré dans un écran.

## Couleurs (`ArkaColors`)

| Token | Valeur | Usage |
| --- | --- | --- |
| `Background` | `#08080B` | Fond OLED de l'application |
| `BackgroundAlt` | `#101014` | Barre de navigation, dialogues |
| `Surface` | `#18181F` | Cartes, puces, vignettes sans image |
| `SurfaceHigh` | `#22222B` | Surface surélevée |
| `Red` | `#E50935` | Action principale, élément sélectionné, progression, direct |
| `RedBright` | `#FF3457` | Réservé aux états actifs sur fond rouge (pas encore utilisé) |
| `RedContainer` | `#4A0A18` | Fond d'un élément actif (source active, programme en cours) |
| `TextPrimary` | `#F5F5F7` | Texte principal, contour de focus |
| `TextSecondary` | `#A1A1AA` | Texte secondaire |
| `Violet` | `#7957FF` | Accent occasionnel (pas encore utilisé) |
| `Error` | `#FF6B6B` | Messages d'erreur |

Règle : le rouge signale une action ou un état, jamais un décor.

## Espacements, rayons, typographie

- `ArkaSpacing` : 4, 8, 12, 16, 24, 32 dp.
- `ArkaRadius` : 6 (cellules), 8 (cartes), 12 (champs) dp ; pilule pour les puces.
- Échelle typographique Material 3 redéfinie : `displaySmall` 30, `headlineSmall` 22, `titleMedium` 17,
  `bodyLarge` 15, `bodyMedium` 14, `bodySmall` 12, `labelSmall` 11 sp.

## États

- **Focus (télécommande)** : `Modifier.focusHighlight()` — contour clair de 2 dp et agrandissement de 6 %
  (120 ms) sans déplacer les voisins. À placer avant `clickable`. Les lignes de liste utilisent
  `scale = 1f` (contour seul).
- **Sélection** : fond `Red` (puces) ou `RedContainer` (lignes).
- **Progression** : barre `Red` sur piste sombre.

## Composants existants

`Logo`, `SectionTitle`, `Poster`, `VodRow`, `ChannelLogo`, `ChannelRow`, `ArkaChip`, `MyListButton`.

## Reste à faire

- Remplacer les tailles de texte encore écrites dans les écrans par l'échelle typographique.
- Squelettes de chargement, états vides illustrés, badges, boutons primaire/secondaire dédiés.
- Variante TV des composants (tailles lisibles à distance), logo et icône définitifs.
- Respect du réglage système de réduction des animations.
