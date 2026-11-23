# AGENTS.md — Consignes pour un agent travaillant sur ce dépôt

Ce fichier s'adresse à tout outil d'IAassistante (CLI, IDE agent) qui ouvre ce dépôt : lis-le en entier avant de produire du code, puis lis les sources de vérité mentionnées plus bas.

## Contexte projet (résumé)

« Je Remets Le Son » : boîte à musique physique pour un jeune enfant. Un vieux téléphone Android dédié, posé sur un chargeur, reste dans l'app. L'enfant pose une étiquette NFC contre le téléphone : le dossier de musique associé joue automatiquement. Aucun écran interactif, aucun streaming, tout est hors-ligne.

- **Équipement cible** : vieux téléphone Android, minSdk 26 (Android 8.0), NFC requis.
- **Stack** : Kotlin, Jetpack Compose, Media3/ExoPlayer, SAF (Storage Access Framework), pas de Room pour l'instant (SharedPreferences + org.json).
- **État actuel** : v0.1.0 (étapes 1-2 de la spec) poussée mais **jamais compilée**. La toute première mission est d'obtenir un build propre, puis de dérouler `ROADMAP.md`.

## Invariants — à ne jamais casser

1. **Les tags NFC sont lus uniquement via leur UID.** On n'écrit jamais rien dessus (ils doivent rester compatibles avec la future boîte Raspberry Pi du projet).
2. **Le volume est plafonné en permanence** (RF05) : toute modification de la chaîne audio doit préserver ou renforcer `VolumeGuard` (plafond parental, contests les boutons physiques).
3. **Le mode enfant est verrouillé** : écran noir maintenu allumé, app pinned (Lock Task), sortie uniquement par code parental, aucun élément interactif visible.
4. **100 % hors-ligne** : pas de permission INTERNET, aucune dépendance réseau, aucune API tierce au chemin critique (cf. arbitrage Spotify/Deezer dans `docs/`).
5. **UI en français**, destinée à un parent non-développeur et à un enfant de 3-6 ans.
6. **Une carte = un dossier de musique**, lu en boucle, ordre alphabétique, position mémorisée pour reprise.

## Carte du code

| Fichier | Rôle |
| --- | --- |
| `app/src/main/java/com/durandestebe/jrls/MainActivity.kt` | UI enfant/parent (Compose), NFC Reader Mode, kiosque, assistant d'association |
| `app/src/main/java/com/durandestebe/jrls/BoxPlayer.kt` | Moteur de lecture Media3/ExoPlayer |
| `app/src/main/java/com/durandestebe/jrls/CardRegistry.kt` | Association UID → dossier + réglages (pins, volume, positions) |
| `app/src/main/java/com/durandestebe/jrls/VolumeGuard.kt` | Plafond de volume, double sécurité (ContentObserver + clamp périodique) |

## Sources de vérité (dans l'ordre)

1. `docs/specifications-app-standalone.md` — exigences RF01-RF13, architecture, **critères d'acceptation** (section 7) : c'est la référence pour valider tout changement.
2. `docs/specifications-projet.md` — contexte global, arbitrages streaming/local, feuille de route v0.5/v1/v2.
3. `ROADMAP.md` — le pas-à-pas courant : quelle étape faire, dans quel ordre, avec quels critères.

## Convention de travail

- Une étape de `ROADMAP.md` = une branche (`fix/…`, `feat/…`, `docs/…`), commits conventionnels en une ligne claire (ex. `fix: alignement des versions compose pour Kotlin 1.9.24`).
- Après chaque étape terminée : mettre à jour `ROADMAP.md` (case de l'étape) et, si une décision de conception évolue, la section « Décisions » de `docs/specifications-projet.md`.
- Incrémenter `versionName` dans `app/build.gradle.kts` à chaque étape achevée.
- Toute modification de la lecture NFC ou du volume doit être justifiée par rapport aux invariants ci-dessus.
- L'agent ne peut probablement pas compiler Android dans son environnement : produire le code, puis demander à Paul de lancer `./gradlew assembleDebug` et itérer sur les erreurs rapportées.

## Limites connues à garder à l'esprit

- NFC inopérant écran éteint (matériel) : l'écran reste allumé noir, c'est voulu.
- Ne pas ajouter de fonctionnalités au-delà de la roadmap sans accord explicite : la simplicité est un principe du projet.
- Les surcouches OEM (Samsung, Xiaomi) tuent les services : les étapes 4+ (service en avant-plan, boot) doivent inclure la checklist batterie du README.
