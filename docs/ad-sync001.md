# AD-SYNC 001 — prototype local

Base : `main@7a2768d36dbe85ed4b366f687d1f6f4d24e84bc2`, PR #30 fusionnée.
Le jalon Cloud → TV 002 et son PASS physique restent acquis. Aucun fournisseur,
Cloud, média Prime ou appareil Sony n'est modifié par cette évolution.

## Signaux et limite fondamentale

Le même MediaSessionProbe lit les API publiques : token de session opaque,
identité compatible, état, position brute, position déjà estimée, vitesse,
ancre de mise à jour, instant de capture et durée. Aucun token, titre ou identifiant
n'est ajouté aux diagnostics AD-SYNC. La sonde ne commande jamais le lecteur.

Un changement/disparition de session, une identité/horloge invalide, une durée
modifiée, un saut inexpliqué ou une nouvelle ancre PLAYING à position inchangée
suspendent les effets. Ce sont des indices d'incertitude, **pas une détection de
publicité**. Une ancre inchangée extrapolée est aussi un fonctionnement MEDIA
normal : on ne peut pas l'assimiler à une position de film figée. Si une publicité
conserve la même identité et une horloge continue, les données disponibles ne
permettent pas de la distinguer du film. Le prototype n'en promet pas la détection.

## Fonctionnement dans le Companion

Seule la variante expérimentale `adsync001` v18 ajoute les contrôles. AD-SYNC est
OFF par défaut ; v17 et les profils historiques conservent leur comportement.
L'opt-in et le recalage ne persistent pas après la fin du processus et ne créent
aucune admission native. Le cache, la confirmation de l'édition et les permissions
existantes restent nécessaires.

- **Suspendre les commentaires** arrête immédiatement TEXT et AUDIO SceneVibe.
  Aucun temps d'événement ne change et aucun événement futur n'est consommé.
- **Reprendre les commentaires** demande deux captures compatibles distinctes,
  avec des ancres récentes. Une disparition de signal ne vaut jamais fin de pub.
  Une ancre trop ancienne impose un recalage explicite plutôt qu'une reprise supposée.
- **Recaler sur le temps du film** accepte mm:ss ou hh:mm:ss après confirmation
  humaine de la reprise. La correction locale est bornée à ±1 heure et utilise
  toujours le temps MEDIA observé, sans seconde horloge ni écriture Cloud.
- Une interruption arrête le MP3 en cours ; il n'est pas repris/rejoué automatiquement.
  Le calendrier conserve les identités consommées au retour, saute les fenêtres
  expirées et conserve les événements futurs. Un SEEK ultérieur à cette reprise
  garde le comportement historique du calendrier. Un SEEK ambigu pendant une
  interruption attend la confirmation/recalage, sans relecture automatique d'une
  phrase déjà consommée.
- Les interrupteurs TEXT/AUDIO restent indépendants. OFF rétablit le chemin v17.
  Les commandes sont dans Réglages et la notification de SceneVibe, pas dans Prime.

Les diagnostics bornés `SceneVibeAdSync` indiquent rawMs, selectedMs, contentMs,
offsetMs, état, raison et âge d'ancre, avec `adSignal=UNAVAILABLE`. Aucun signal
publicitaire Prime réellement exposé n'est présumé. Les lignes MEDIA détaillées
avec métadonnées sont désactivées dans le seul build expérimental.

## Références examinées le 27 octobre 2026

Code SceneVibe écrit indépendamment ; aucun fichier/code de ces projets n'est intégré.

| Référence primaire | Enseignement retenu et frontière |
| --- | --- |
| [Morphe, conception Prime ATV](https://github.com/ajstrick81/morphe-androidtv-patches/blob/main/docs/PRIME_VIDEO_ATV_SYSTEM_DESIGN.md) | Décrit une timeline interne composée de périodes de contenu et de publicité et des problèmes de continuité aux transitions. Cette étude sur certaines versions n'établit aucune API tierce disponible sur la Sony. Lecture documentaire uniquement ; aucun hook, patch, accès interne ou interception repris. |
| [Ad Break Mute, détecteur Prime web](https://github.com/lpawa/ad-break-mute/blob/main/extension/sites/primevideo/page.js) | Compte à rebours visible, apparition/disparition et confirmation différée illustrent l'intérêt d'une confirmation de transition. Aucun DOM supposé sur Android ; licence non établie (endpoint de licence sans résultat), aucun code copié. |
| [iSponsorBlockTV](https://github.com/dmunozv04/iSponsorBlockTV) | Application indépendante et suivi par le protocole propre à YouTube/pyytlounge. Ce canal n'est pas attribué à Prime ; aucune commande distante ou fonction de saut intégrée. |
| [SkipperKit](https://github.com/foodlbs/skipperkit) | Arbres d'accessibilité et confirmation des contrôles ; son README exclut explicitement Android TV. Aucune permission/service d'accessibilité ajouté au Companion. Les affirmations Play du projet ne constituent pas une règle officielle vérifiée pour SceneVibe. |
| [Comskip](https://github.com/erikkaashoek/Comskip) | Détecteur sur médias/fichiers avec FFmpeg ; les indices de contenu nécessitent un accès aux images/sons qui n'est pas disponible dans ce périmètre. Aucun traitement, capture ou téléchargement du contenu protégé. |
| [Media3 Player](https://developer.android.com/reference/androidx/media3/common/Player) | `getCurrentPosition()` peut concerner contenu ou publicité ; `getContentPosition()` et `isPlayingAd()` existent dans un Player qui expose ces données. Aucune instance du Player interne de Prime n'est accessible ici. |
| [Media3 MediaController](https://developer.android.com/reference/androidx/media3/session/MediaController) | Ces accès dépendent de la session et des commandes disponibles. La documentation d'interop précise que sur MediaSessionCompat, la position de contenu n'est pas disponible séparément et devient la position courante. Ajouter Media3 ne restituerait pas une information absente. |
| [PlaybackState Android](https://developer.android.com/reference/android/media/session/PlaybackState) | Fournit les faits temporels publics utilisés ; pas de flag publicitaire Prime documenté. Le prototype ne lit pas d'extras privés ni de clés publicitaires présumées. |

## Qualification logicielle et physique

Les séquences déterministes couvrent lecture normale, hold, reprise récente,
reprise ambiguë, ancre figée/fraîche ou ancienne, saut, perte/changement de session,
identité invalide, offset manuel, événements expirés/consommés et interrupteurs.
Le runtime testé est le calendrier/regie/renderer AUDIO existant avec ports de
mesure explicites. Les fixtures n'ont aucune autorité de cache ou Cloud.

La suite native Android TV emploie uniquement des MediaSessions appartenant à
SceneVibe, les vrais widgets Réglages et le port MediaPlayer avec le MP3 de test
SceneVibe déjà committé. Elle ne génère aucun AUDIO et n'observe pas Prime.
Elle utilise une origine Cloud vide. L'APK candidat est construit séparément avec
l'origine de staging déjà qualifiée. Ces essais ne démontrent pas une détection
publicitaire physique et ne remplacent pas les preuves Cloud/Sony acquises.

## Essai Sony proposé — après nouveau GO seulement

1. Installer le candidat v18 signé M5 après vérification de son empreinte ;
   conserver package et cache. Aucune installation n'est exécutée par Work.
2. Relire le cache, activer explicitement TEXT/AUDIO puis AD-SYNC dans Réglages.
3. Avant/au début d'une publicité, **Suspendre les commentaires** dans SceneVibe
   (ou sa notification). Confirmer l'arrêt du son et de l'overlay SceneVibe ; Prime
   doit continuer librement.
4. Quand le film reprend, **Reprendre** ; si l'ancre est refusée, saisir le temps
   visible du film avec **Recaler**. Vérifier un événement futur et l'absence de
   relecture de l'AUDIO interrompu.
5. Vérifier TEXT OFF/AUDIO OFF puis AD-SYNC OFF ; relever les seuls diagnostics
   SceneVibeAdSync/SceneVibeText009. Ne pas capturer le flux ou l'interface Prime.

STOP avant Sony, fusion, déploiement ou modification d'une plateforme tierce.
