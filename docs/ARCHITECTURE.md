# Architecture

État au terme de la phase P0. Un seul module Gradle (`app`) : le découpage en modules n'apporterait
rien à cette taille de code ; il sera reconsidéré quand les interfaces mobile et TV divergeront (P1).

## Vue d'ensemble

```
ui/ (Compose)  ──lit──>  Repo (état applicatif, snapshot state Compose)
                           │
          ┌────────────────┼──────────────────────────┐
          ▼                ▼                          ▼
   Store (prefs chiffrées) CatalogStore (Room)   Réseau : XtreamApi, M3u, Epg (OkHttp)
   sources, favoris,       catalogue + guide     + XtreamJson (parsing pur)
   Ma liste, progression   = cache reconstructible
          │
          ▼
   SecretBox (AES-GCM, clé enveloppée par Android Keystore)
```

| Paquet | Rôle |
| --- | --- |
| `tv.arka.app` | `MainActivity` (navigation par pile d'écrans), `PlayerActivity` (Media3) |
| `tv.arka.app.ui` | Écrans Compose, `Theme.kt` (tokens de design, focus TV), composants communs |
| `tv.arka.app.data` | Modèles, `Repo`, `Store`, clients et parseurs des sources |
| `tv.arka.app.data.db` | Base Room `arkatv-catalog.db` et `CatalogStore` |
| `tv.arka.app.data.security` | `SecretBox` |

## Flux de données au démarrage (offline-first)

1. `Store.init` déchiffre les sources ; `Repo.select` charge favoris, liste et progression.
2. `CatalogStore.load` relit le dernier catalogue valide et le guide : l'interface est utilisable sans réseau.
3. Si le cache est vide, périmé (12 h) ou si l'utilisateur le demande, le catalogue est rechargé en
   arrière-plan puis enregistré en une transaction.
4. Un échec de rechargement laisse le cache en place (`Repo.syncError`) ; seule l'absence totale de
   données produit une erreur bloquante (`Repo.error`).
5. Le guide suit son propre cycle (6 h) et ne bloque jamais les chaînes.

## Deux stockages, deux statuts

- **Données utilisateur** (`Store`, SharedPreferences chiffrées) : jamais effacées par une
  actualisation ni par une panne du fournisseur ; supprimées uniquement avec leur source.
- **Cache catalogue** (`Room`) : reconstructible depuis le fournisseur, donc recréé en cas de changement
  de schéma (`fallbackToDestructiveMigration`). Aucun identifiant n'y figure en clair.

## Dette connue

- `Repo` est un singleton à état Compose, sans ViewModel ni injection de dépendances.
- Le catalogue est encore entièrement chargé en mémoire après lecture du cache : pas de pagination.
- Les chaînes d'interface sont codées en français dans le code (pas de ressources de traduction).
- La même interface sert le téléphone et la TV.

Ces points sont planifiés dans `ROADMAP.md`.
