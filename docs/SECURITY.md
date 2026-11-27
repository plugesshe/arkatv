# Sécurité et confidentialité

## Ce qui est protégé

Les identifiants d'une source se retrouvent à plusieurs endroits : la source elle-même, l'URL d'une
playlist M3U, et toute URL de flux Xtream (`/live/<identifiant>/<mot de passe>/…`). Tout est donc traité
comme secret.

| Donnée | Stockage | Protection |
| --- | --- | --- |
| Sources (URL, identifiant, mot de passe) | SharedPreferences | AES-256-GCM |
| Favoris, « Ma liste », progression (contiennent des URLs de flux) | SharedPreferences | AES-256-GCM |
| Catalogue Xtream | Room | les URLs ne sont pas stockées, elles sont reconstruites à l'exécution |
| Catalogue M3U | Room | URL de chaque entrée chiffrée (AES-256-GCM) |
| Guide TV | Room | aucun secret |

## Chiffrement (`SecretBox`)

- Clé de données aléatoire de 256 bits, enveloppée par une clé non exportable de l'Android Keystore.
- AES-GCM avec un vecteur d'initialisation aléatoire par valeur : chiffrement authentifié, toute
  altération est rejetée.
- Si la clé du Keystore devient inutilisable, les valeurs chiffrées sont illisibles : l'application
  redemande alors les sources. C'est le comportement voulu, mais il n'est pas encore expliqué à l'écran.

## Migration depuis 0.1.0

Au premier lancement, les valeurs écrites en clair par la 0.1.0 sont rechiffrées puis supprimées.
Couvert par un test unitaire qui vérifie aussi qu'aucun mot de passe ne subsiste en clair.

## Sauvegardes

`allowBackup="false"` et règles d'extraction excluant tous les domaines : rien ne part dans une
sauvegarde cloud ni dans un transfert d'appareil.

## Réseau

- Validation TLS standard du système ; aucun gestionnaire de confiance permissif.
- HTTP en clair reste autorisé, car beaucoup de fournisseurs ne proposent que cela. L'écran d'ajout de
  source avertit quand l'adresse saisie est en `http://`.
- Le lecteur suit les redirections entre http et https.

## Permissions et collecte

Une seule permission : `INTERNET`. Aucun compte, aucun traceur, aucune télémétrie.

## Limites connues

- Les identifiants Xtream font partie de l'URL des flux : ils transitent en clair sur les sources HTTP.
  C'est une contrainte du protocole, pas de l'application.
- Les messages d'erreur réseau affichés proviennent des bibliothèques ; ils n'ont pas été audités un à un
  pour garantir qu'aucune URL complète n'y apparaît.
- En mémoire, le catalogue contient les URLs de flux en clair pendant l'exécution.
- Pas encore de politique HTTP explicite par source (accepter / refuser), seulement un avertissement.

## Mise à jour intégrée

- L'application lit `version.json` puis télécharge l'APK depuis la pré-release `dev` du dépôt GitHub public,
  en HTTPS. Aucune donnée n'est envoyée ; la vérification au lancement se désactive dans les paramètres.
- L'APK reçu est comparé au SHA-256 annoncé, puis remis à l'installeur système, qui demande confirmation.
- Limite connue : les builds sont signés avec la clé de debug conservée dans le dépôt, donc publique. Elle
  garantit la continuité des installations, pas l'authenticité. Avant toute diffusion plus large, il faut une
  clé de production gardée dans les secrets du dépôt.
- Chaque build vert de `claude/arkatv-excellence` est publié dans la pré-release `dev` (accord du propriétaire, 27/11/2026).

## Contrôle parental

- Le code n'est jamais conservé : seule une empreinte SHA-256 salée l'est, dans les préférences chiffrées.
- Les catégories masquées sont filtrées dans les requêtes du catalogue elles-mêmes, pas seulement à l'affichage.
- Limite connue : un contenu déjà présent dans « Ma liste » ou l'historique d'un profil y reste visible.

## TMDB et saisie à distance

- La clé TMDB appartient à l'utilisateur : saisie dans l'application, conservée chiffrée, envoyée
  uniquement à `api.themoviedb.org` en HTTPS. Elle n'est ni dans le dépôt, ni dans l'APK, ni dans les journaux.
- Seuls des titres nettoyés et des années sont envoyés à TMDB. Sans clé, aucune requête n'est faite.
- La page de saisie à distance est en HTTP sur le réseau local : elle n'existe que tant que le bloc de
  réglages est affiché, demande un code à quatre chiffres tiré au hasard et se ferme après cinq erreurs
  ou dès qu'une valeur est acceptée. À n'utiliser que sur un réseau domestique.
