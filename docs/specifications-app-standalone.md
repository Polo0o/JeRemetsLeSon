# Specifications for standalone app

**« Je Remets Le Son » — Application Android dédiée**

Version 1.0 — 19 novembre 2026. Statut : prête à implémenter. Complète la feuille de route v0.5 du [cahier des spécifications](specifications-projet.md).

## 1. Objectif et périmètre

L'app transforme un vieux téléphone Android dédié en boîte à musique du projet « Je Remets Le Son » : l'enfant pose une étiquette NFC sur le téléphone, la musique associée démarre automatiquement, sans aucun contact avec l'écran.

Elle remplace les solutions tierces (MacroDroid, NFC Radio) par un logiciel unique et autonome : lecture automatique native, volume plafonné garanti par construction, mode kiosque, et interface parent pour associer les étiquettes à la musique.

Hors périmètre de la v1 : iOS, streaming, multi-appareils, synchronisation. Tout doit fonctionner 100 % hors-ligne.

## 2. Parcours d'utilisation

**Enfant** (aucun écran requis) :

- Il pose une étiquette : sa musique démarre automatiquement (moins de 2 secondes).
- Il pose une autre étiquette : la musique en cours s'arrête, la nouvelle démarre.
- Il pose l'étiquette STOP : pause. Il repose une étiquette déjà jouée : reprise.

**Parent** :

- Il ouvre l'app, saisit le code parental, accède au panneau d'administration.
- Assistant d'association : poser un tag vierge, nommer la carte, choisir le dossier audio, associer.
- Il règle le volume maximum, l'étiquette STOP, la luminosité.
- Il importe la musique : dossiers du stockage interne ou copie via USB (depuis le NAS).

## 3. Exigences fonctionnelles

Priorités : **P1** = indispensable (v1 de l'app), **P2** = souhaitable, **P3** = version ultérieure.

- **RF01 (P1) Lecture automatique** : tag posé, app au premier plan (écran éventuellement verrouillé ou noir), démarrage de la lecture en moins de 2 secondes.
- **RF02 (P1) Association** : chaque tag (identifié par son UID) correspond à une carte = un dossier audio + un titre + une image.
- **RF03 (P1) Changement de carte** : arrêt propre de la lecture courante (fade court), lecture immédiate de la nouvelle carte, sans chevauchement.
- **RF04 (P1) Stop et reprise** : étiquette STOP dédiée ; reprise en reposant une carte déjà jouée ; mémorisation de la position dans le dossier.
- **RF05 (P1) Verrouillage du volume** : plafond réglé par le parent, appliqué en permanence, y compris contre les boutons physiques de volume.
- **RF06 (P1) Mode kiosque** : plein écran, écran maintenu allumé mais noir (ou image de fond), navigation Android bloquée, sortie uniquement par code parental.
- **RF07 (P1) Bibliothèque locale** : import de dossiers MP3/FLAC/OGG, ordre de lecture par nom de fichier, lecture en boucle du dossier.
- **RF08 (P1) Persistance** : l'app survit au redémarrage du téléphone (lancement automatique au boot, retour en mode kiosque).
- **RF09 (P2) Minuteur d'arrêt automatique** : 30/45/60 minutes ou heure fixe, configurable par le parent.
- **RF10 (P2) Conditions horaires** : plafond de volume réduit automatiquement le soir (ex. après 20 h).
- **RF11 (P2) Résistance aux interruptions** : audio focus (un appel prend la priorité, la musique reprend ensuite), reconnexion Bluetooth automatique vers l'enceinte appairée.
- **RF12 (P2) Statistiques simples** : nombre de lectures par carte.
- **RF13 (P3) Cartes « surprise »** : lecture aléatoire d'un dossier.

## 4. Contraintes techniques

- **Plateforme** : Android, minSdk 26 (Android 8.0) pour couvrir les vieux téléphones. Langage : Kotlin. UI : Jetpack Compose.
- **Lecture audio** : Media3 / ExoPlayer, AudioAttributes USAGE_MEDIA, gestion de l'audio focus.
- **Capture NFC** : foreground dispatch / Reader Mode (NfcAdapter). Fiable tant que l'app est au premier plan et l'écran allumé. L'UID du tag suffit : **aucun contenu à écrire sur les tags**, les étiquettes restent indestructibles et réutilisables plus tard sur le Raspberry Pi.
- **Permissions** : NFC (aucune permission runtime), FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, accès fichiers via Storage Access Framework, exemption d'optimisation de batterie (les surcouches constructeur tuent sinon les services : checklist à l'installation).
- **Écran** : FLAG_KEEP_SCREEN_ON + luminosité nulle ; Screen Pinning (Lock Task) pour le kiosque natif.
- **Volume** : clamp continu (AudioManager) au plafond parental.
- **Stockage** : registre des cartes et réglages (Room à terme) ; musique via SAF.
- **Alimentation** : téléphone posé sur chargeur en permanence ; écran noir permanent (pixels OLED éteints) pour limiter le marquage.
- **Limitation matérielle assumée** : le NFC ne lit pas écran éteint, aucune app ne peut contourner ; l'écran reste donc allumé, noir.

## 5. Architecture logicielle

Composants :

- **KioskForegroundService** : service média en avant-plan, détient le lecteur, l'état machine et le clamp volume. *(MVP v0.1 : la lecture vit dans l'activité ; le service dédié arrive à l'étape 3.)*
- **TagRouter** : réception des événements NFC, extraction de l'UID, transmission au routeur.
- **PlaybackEngine** (BoxPlayer) : Media3, playlist du dossier de la carte, audio focus, sauvegarde de position.
- **CardRegistry** : association UID → carte ; entité Carte `{ uid, titre, dossier, image, volumeMax, ordreLecture, activée }` ; réglages.
- **KioskController** : drapeaux écran, pinning, blocage de sortie.
- **ParentActivity** : interface parent Compose : liste des cartes, assistant d'association, réglages, import de musique.

Machine à états du lecteur : `Veille → Lecture(carte) → Pause` ; événements : tag posé, tag STOP, minuterie écoulée, interruption audio, changement de carte.

Robustesse : fichier audio illisible ignoré avec journal ; dossier manquant → erreur claire côté parent, pas de crash.

## 6. Interface

- **Écran enfant (kiosque)** : fond noir par défaut ou image de la carte en lecture ; aucun élément interactif ; indicateur discret lecture/pause.
- **Écran parent** : liste des cartes (titre, dossier, nombre de lectures), bouton « Ajouter une carte » (assistant en 3 étapes), réglages (volume max global et par carte, minuterie, conditions horaires, luminosité, auto-démarrage), bouton « Écouter » (test) par carte.
- **Premier lancement** : configuration réservée au parent, définition du code parental.

## 7. Critères d'acceptation

- Tag posé : musique audible en moins de 2 secondes (3 essais sur 5 étiquettes).
- Changement de carte : moins de 1,5 seconde, sans chevauchement audio.
- Le volume ne dépasse jamais le plafond, même en manipulant les boutons physiques.
- 8 heures en mode kiosque sans intervention : pas de crash, pas d'extinction d'écran, pas de coupure sonore.
- Après un appel entrant : reprise automatique de la carte en cours.
- Enceinte Bluetooth : redémarrage du téléphone avec l'enceinte allumée → reconnexion et lecture opérationnelles.
- Redémarrage du téléphone : retour automatique en mode kiosque en moins de 30 secondes.
- Dossier d'une carte supprimé du stockage : message clair pour le parent, pas de crash en mode enfant.

## 8. Risques et limites connues

- NFC impossible écran éteint (matériel) : écran allumé noir en permanence ; marquage physique de la zone de pose des étiquettes (l'antenne NFC varie selon les téléphones).
- Surcouches constructeur (Samsung, Xiaomi...) : gestion de batterie agressive qui tue les services → checklist d'exemption (batterie non optimisée, démarrage auto activé).
- Téléphone sous tension en continu : usure de la batterie à long terme (charge permanente).
- Interopérabilité préservée : les tags UID restent utilisables tels quels par la future v1 Raspberry Pi.

## 9. Plan de développement indicatif

1. **Étape 1 (MVP, un week-end)** : dispatch NFC → lecture automatique du dossier ; clamp volume ; kiosque basique.
2. **Étape 2** : écrans parent, assistant d'association, registre des cartes.
3. **Étape 3** : service en avant-plan dédié, démarrage au boot, minuteur, audio focus, reprise Bluetooth.
4. **Étape 4** : polissage, tests d'acceptation, installation sur le téléphone dédié.

*État du dépôt : étapes 1 et 2 implémentées (v0.1.0), non encore compilées. Distribution : APK signée en sideload (usage familial, pas besoin du Play Store).*

## 10. Références

- [Android Developers — NFC](https://developer.android.com/develop/connectivity/nfc/nfc)
- [Android Developers — Media3 / ExoPlayer](https://developer.android.com/media/media3)
- [Jooki (MuuseLabs)](https://rfidjournal.com/small-children-jam-to-nfc-enabled-jukebox) — référence commerciale du concept.
