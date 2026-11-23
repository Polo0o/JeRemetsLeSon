# Je Remets Le Son — Application Android (code source)

Implémentation des étapes 1 et 2 du document « Specifications for standalone app » :
transforme un vieux téléphone Android en boîte à musique kiosque pilotée par étiquettes NFC.

Modèle : le téléphone Android dédié, posé sur un chargeur, reste dans l'app.
L'enfant pose une étiquette NFC, le dossier de musique correspondant joue
automatiquement. Aucun contenu n'est jamais écrit sur les tags : l'app lit
uniquement leur UID, les étiquettes restent donc indestructibles et
compatibles avec la future boîte Raspberry Pi.

## Contenu du projet

    settings.gradle.kts, build.gradle.kts, gradle/wrapper/...   : build Gradle
    app/build.gradle.kts                                         : module + dépendances
    app/src/main/AndroidManifest.xml                             : activité unique, permission NFC
    app/src/main/res/                                            : thème sombre, nom de l'app
    app/src/main/java/com/durandestebe/jrls/
        MainActivity.kt    : interface enfant/parent, dispatch NFC (Reader Mode), kiosque
        BoxPlayer.kt       : moteur de lecture Media3/ExoPlayer (dossier en boucle, reprise)
        CardRegistry.kt    : association tag → dossier + réglages (SharedPreferences)
        VolumeGuard.kt     : plafond de volume garanti (RF05), double sécurité

## Couvert par cette version (v0.1.0)

- RF01 lecture automatique au tag (app au premier plan, écran éventuellement noir)
- RF02 association UID → carte (assistant parent en 3 étapes)
- RF03 changement de carte avec sauvegarde de la position
- RF04 étiquette STOP dédiée + reprise en reposant la même carte
- RF05 volume plafonné en permanence (boutons physiques inclus)
- RF06 kiosque : plein écran, écran maintenu allumé mais noir, app pinned, sortie par code parental
- RF07 bibliothèque locale SAF (dossier = carte, ordre alphabétique, boucle)
- Interface parent : liste des cartes, écoute d'essai, suppression, volume max, étiquette STOP

Non couvert (étapes 3 et 4 de la spec) : démarrage automatique au boot,
minuteur de sommeil, conditions horaires, statistiques, service audio dédié
en avant-plan (ExoPlayer vit ici dans l'activité).

## Compiler et installer

Prérequis : Android Studio (Koala ou plus récent) ou Gradle 8.7+, JDK 17.

1. Ouvrir ce dossier dans Android Studio (il régénère le wrapper Gradle au premier sync).
2. Brancher le téléphone, débogage USB activé.
3. `./gradlew installDebug` ou Run depuis Android Studio.

L'app n'a pas besoin du Play Store : APK signée en debug, usage familial.

## Checklist d'installation sur le téléphone dédié

- Le téléphone doit avoir une puce NFC (quasi systématique).
- Optimisation de batterie : mettre l'app en « Non optimisée » (Réglages > Batterie).
- Écran : définir la mise en veille au maximum ; l'app maintient de toute façon
  l'écran allumé et noir en mode enfant.
- Le premier lancement demande un code parental, puis ouvre l'interface parent :
  créer la première carte (Ajouter une carte → poser l'étiquette → choisir le dossier
  → nommer), régler le volume maximum, définir l'étiquette STOP.
- « Retour au mode enfant » épine l'app : accepter l'épinglage (« Épingler ») si
  Android le demande. Sortir du mode enfant appui touche retour puis code parental.

## Copier la musique

1. Depuis le PC (ou le NAS Synology), copier un album par dossier sur le stockage
   du téléphone (ex. `Musique/Julia - Album X/01 - Titre.mp3`).
2. Dans l'app, l'association se fait dossier par dossier via le sélecteur de fichiers
   (SAF), sans limite de nombre de cartes.

## Limites connues (assumées au stade MVP)

- Le NFC ne lit pas écran éteint (matériel) : l'écran reste allumé, noir.
- La lecture peut être coupée par la mise en veille profonde du téléphone si
  l'exclusion de batterie n'est pas faite (voir checklist).
- Un dialogue d'épinglage peut apparaître à chaque cycle de verrouillage selon
  le fabricant ; la sortie reste protégée par le code parental.
