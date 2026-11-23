# ROADMAP — Je Remets Le Son (app Android)

Pas-à-pas pour reprendre le projet en CLI. Chaque étape a un objectif, des actions et des **critères de passage** : ne passe à la suivante que si tout est coché. État au 23 nov. 2026 : **v0.1.0 poussée, jamais compilée**.

Pour le contexte complet, lire `AGENTS.md` et `docs/specifications-app-standalone.md`.

---

## Étape 0 — Mise en place (5 min, humain)

- [ ] `git clone https://github.com/Polo0o/JeRemetsLeSon && cd JeRemetsLeSon`
- [ ] Ouvrir le dossier dans Android Studio (JDK 17, SDK 34) ; laisser le sync Gradle regénérer le wrapper.
- [ ] Téléphone de test : activer les options développeur + débogage USB, vérifier la présence du NFC.

## Étape 1 — Première compilation (objectif : BUILD SUCCESSFUL)

Le code a été écrit sans chaîne de compilation Android : il faut s'attendre à des ajustements.

- [ ] Lancer `./gradlew assembleDebug` (ou Build dans Android Studio).
- [ ] Corriger tout ce qui bloque. Points les plus probables :
  - versions des dépendances Compose / compose-compiler vs Kotlin (`app/build.gradle.kts`),
  - imports Compose (les API Material3 bougent : `darkColorScheme`, `BackHandler`, `rememberLauncherForActivityResult`),
  - changements d'API Media3 mineurs.
- [ ] Traiter aussi les erreurs bloquantes de lint seler pertinentes, mais ne pas chercher la perfection à cette étape.
- [ ] Commit : `fix: build initial (v0.1.1)`.

**Critères de passage** : `BUILD SUCCESSFUL`, APK debug généré dans `app/build/outputs/apk/debug/`.

## Étape 2 —Installation et smoke test (téléphone)

- [ ] `./gradlew installDebug`, lancer l'app.
- [ ] Appliquer la checklist du `README.md` (batterie « non optimisée », mise en veille écran au max).
- [ ] Premier lancement : définir le code parental.
- [ ] « Ajouter une carte » : poser une étiquette vierge, choisir un dossier audio, nommer.
- [ ] Passer en mode enfant, accepter l'épinglage si Android le demande.
- [ ] Définir l'étiquette STOP et la tester.
- [ ] Tester les boutons physiques de volume contre le plafond réglé (ex. 60 %).
- [ ] Journal des anomalies : latence NFC, zone d'antenne, comportement OEM, textes UI.

**Critères de passage** : tag posé → musique audible < 2 s ; écran reste noir/allumé ; retour arrière → code parental → interface parent ; STOP → pause ; re-pose de la carte → reprise.

## Étape 3 — Consolidation terrain (test avec l'enfant)

- [ ] Créer 5 étiquettes réelles décorées et les associer (dossiers distincts).
- [ ] Corriger les frictions relevées (zone de pose de l'étiquette à marquer physiquement, éventuel échauffement/batterie, textes).
- [ ] Corriger les bugs remontés par le test, un commit par fix.

**Critères de passage** : les 5 étiquettes fonctionnent au premier geste, l'enfant change d'album seul sans aide.

## Étape 4 — Montée en gamme (= « étape 3 » de la spec)

- [ ] Sortir la lecture de l'activité : **service de lecture en avant-plan** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`) avec MediaSession, pour survivre aux interruptions et aux changements d'écran.
- [ ] **RF08** : `RECEIVE_BOOT_COMPLETED` → relance auto de l'app en mode kiosque après redémarrage.
- [ ] **RF09** : minuteur d'arrêt automatique (30/45/60 min ou heure fixe), réglé côté parent.
- [ ] **RF10** : plafond de volume réduit automatiquement le soir (ex. après 20 h).
- [ ] **RF11** : reconnexion Bluetooth automatique vers l'enceinte appairée, reprise après audio focus perdu (appel).
- [ ] Commit global : `feat: service média, boot auto, minuteur (v0.2.0)`.

**Critères de passage** : reboot du téléphone → kiosque actif < 30 s ; appel entrant → reprise ensuite ; 8 h en kiosque sans crash ni coupure (section 7 de la spec).

## Étape 5 — Durcissement et release

- [ ] **RF12** : compteur de lectures par carte (interface parent).
- [ ] Optionnel : **RF13** cartes « surprise » (lecture aléatoire d'un dossier).
- [ ] Nettoyage lint/`gradle.lock`, revue des thèmes et textes.
- [ ] Build release signée (keystore local, jamais commité), tag `git v1.0.0` + note de version.
- [ ] Mettre à jour la section « Décisions » de `docs/specifications-projet.md`.

**Critères de passage** : critères d'acceptation de la spec tous verts, APK release installée sur le téléphone dédié.

---

## Après l'app

Deux chantiers hors de ce dépôt, documentés dans `docs/specifications-projet.md` : la décision « le vieux téléphone suffit-il comme boîte v1 ? » (sinon Raspberry Pi + Phoniebox, BOM ~110-170 €) et la v2 embarquée ESP32.
