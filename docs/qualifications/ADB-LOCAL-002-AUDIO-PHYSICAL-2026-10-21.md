# SceneVibe — ADB LOCAL 002 — Qualification AUDIO physique (Sony BRAVIA)

**Date des essais :** 21 octobre 2026  
**Décision de clôture :** `CLOSED — PASS AUDIO LOCAL PHYSIQUE`  
**Synchronisation MEDIA :** `NOT QUALIFIED`  
**Nature :** compte rendu d'observations manuelles, non validation de production.

## Objectif et périmètre

Qualifier sur Sony BRAVIA Android TV 12 un APK expérimental autonome permettant d'importer localement une track Studio `Columbo — Eaux troubles` (16 événements TEXT attendus et trois MP3 synthétiques de tonalité, environ 3 secondes chacun), puis d'en tester manuellement la restitution sonore.

Le test ne vise **pas** la synchronisation avec Prime Video, l'admission MEDIA, l'overlay TEXT, ni les commandes PAUSE/RESUME/SEEK. Les services historiques `OverlayService`, `MediaSessionAccessService` et `BootReceiver` sont désactivés dans la variante expérimentale.

## Artefacts de référence

| Artefact | Valeur observée |
| --- | --- |
| APK | `app-studiomultimedia001.apk` |
| Taille APK | 1 046 114 octets |
| SHA-256 APK | `E083E2E0668E5A77A444281B2CD4BA6DE96A16FB1258B7409F9B7C3BA151D97E` |
| Application ID | `com.scenevibe.tvcompanionpoc.studiomultimedia001` |
| Version | `0.8.2-tv-release-hardening-studio-multimedia001-local` |
| Version code | `1` |
| Signature | Android Debug, schéma APK v2 vérifié, un signataire |
| ZIP | `columbo-text-audio-studio.zip` |
| Taille ZIP | 175 091 octets |
| Dépôt | `pierre22400/scenevibe-tv-companion` |
| Base de travail locale annoncée | `414151c4b207544dd417db4a102ee6ccebedbd66` |

Le SHA-256 du ZIP n'a pas été relevé dans cette session. Les noms et tailles ne suffisent pas à prouver une identité cryptographique du ZIP entre les deux PC.

## Qualification logicielle préalable

- `:app:testStudiomultimedia001UnitTest` : `BUILD SUCCESSFUL` après corrections locales de qualification.
- `:app:lintStudiomultimedia001 :app:assembleStudiomultimedia001` : `BUILD SUCCESSFUL in 14s`, 41 tâches (11 exécutées, 30 à jour).
- `aapt dump badging` : application ID expérimental distinct de `com.scenevibe.tvcompanionpoc`.
- `apksigner verify --verbose --print-certs` : `Verifies`, signature v2 validée.
- Manifeste APK : `MainActivity` historique désactivée ; activité `StudioMultimediaQualificationActivity` exportée et lançable ; services historiques précités désactivés.
- Le code de l'activité confirme deux modes d'import (sélecteur Android et boîte de réception privée ADB) et trois commandes manuelles de lecture MP3. Il indique explicitement l'absence d'admission/synchronisation MEDIA.

Ces résultats concernent le **worktree local expérimental** ; ce document n'affirme pas que les correctifs locaux sont déjà publiés sur GitHub.

## Essai physique sur Sony BRAVIA

Connexion ADB réseau établie sur la Sony BRAVIA VH1, Android TV 12. Le numéro de série réseau et l'adresse IP sont omis du document public.

### Installation et coexistence

- Avant installation, `pm path com.scenevibe.tvcompanionpoc` retournait le chemin de l'application stable ; `pm path com.scenevibe.tvcompanionpoc.studiomultimedia001` ne retournait rien.
- Installation expérimentale via `adb install` **sans** `-r` : `Performing Streamed Install / Success`.
- Après installation, les deux `pm path` retournent chacun un `base.apk` distinct.
- `dumpsys package` confirme `versionCode=1`, `minSdk=26`, `targetSdk=35` et la version expérimentale attendue.
- Lancement explicite de `StudioMultimediaQualificationActivity` : **interface affichée, PASS**.

### Import du ZIP

- Copie du ZIP vers `/sdcard/Download/columbo-text-audio-studio.zip` : **175 091 octets transférés**.
- Le bouton `Choisir un ZIP depuis Android` a échoué avec le message : « aucune de vos applications ne peut effectuer cette action ». Aucun sélecteur compatible `ACTION_OPEN_DOCUMENT` n'était disponible sur ce téléviseur lors de l'essai.
- `run-as com.scenevibe.tvcompanionpoc.studiomultimedia001 pwd` a réussi, pointant vers le répertoire privé de l'application expérimentale.
- Le code `StudioMultimediaAdbInbox.java` fixe l'emplacement relatif à `studio-inbox/track.zip` sous `getFilesDir()`.
- Copie via ADB vers `files/studio-inbox/track.zip` : `ls -l` confirme **175 091 octets** et un propriétaire applicatif.
- Activation du bouton `Vérifier le ZIP transféré par ADB` : apparition de **trois boutons « Tester MP3 »** et du bouton **« Arrêter le son local »**. Le texte intégral du statut d'import (`16 TEXT · 3 AUDIO`) n'a pas été retranscrit ; ne pas présenter ce libellé comme une observation verbatim.

### Lecture physique des MP3

| Action | Observation utilisateur | Verdict |
| --- | --- | --- |
| `Tester MP3 001` | Tonalité audible, environ 3 secondes | PASS |
| `Tester MP3 002` | Tonalité audible, environ 3 secondes | PASS |
| `Tester MP3 003` | Tonalité audible, environ 3 secondes | PASS |

Exemple de statut affiché, rapporté par l'utilisateur :

```text
Tonalité 2 : PLAY local manuel. Aucune admission/synchronisation MEDIA
```

Le bouton d'arrêt a été affiché ; son comportement en cours de lecture n'a pas fait l'objet d'une observation distincte consignée ici.

## Verdict et limites

**`ADB LOCAL 002 — CLOSED — PASS AUDIO LOCAL PHYSIQUE`** pour la chaîne : paquet Studio ZIP → import privé ADB → application Android TV expérimentale → déclenchement manuel → tonalités audibles sur la Sony.

**`MEDIA SYNC — NOT QUALIFIED`** : aucune preuve de déclenchement automatique à partir de la timeline Columbo, de synchronisation avec Prime Video, d'overlay TEXT conjoint, d'admission MEDIA ou de comportement PAUSE/RESUME/SEEK dans cet APK.

La présence simultanée des deux paquets a été vérifiée ; aucun test de non-interférence exhaustive n'a été réalisé. L'APK utilise une signature Debug et ne constitue pas une version de distribution.

## Suite autorisée / non autorisée

- Conserver les artefacts expérimentaux et les résultats de cette campagne.
- Préparer une mission distincte **TV-AUDIO TRACK 002** pour relier les événements AUDIO à la timeline et à l'observation MEDIA, sans régression TEXT.
- **Aucune** fusion, installation supplémentaire, publication de code expérimental, mutation Cloud/Neon/R2 ou déploiement de production n'est autorisé par ce procès-verbal.
