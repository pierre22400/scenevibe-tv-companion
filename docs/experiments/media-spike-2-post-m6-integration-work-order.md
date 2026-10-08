# SceneVibe OS — suite au Spike Audio/Vidéo 2.0.2 — work order post-M6

**Statut : PROPOSITION D'ARCHITECTURE / CANDIDAT POUR WORK, NON AUTORISATION D'IMPLÉMENTATION.**
Le [Spike 2.0.2](media-spike-2-sony-physical-closure-2026-10-08.md) est physiquement PASS sur Sony BRAVIA / Prime, mais demeure expérimental et distinct de SceneVibe OS M6.

## 0. Contraintes non négociables

1. GitHub est source de vérité : établir au démarrage **les HEAD et états actuels** de `pierre22400/scenevibe-tv-companion` et `pierre22400/interface-scenevibe` ; ne pas réutiliser un SHA ancien sans contrôle.
2. Préserver M6 et sa qualification Sony réussie (`0.8.3-m6-phase-e`). Les PR TV #16, Cloud #28, et le Spike #17 sont séparées ; toutes sont maintenues **OPEN / DRAFT / unmerged** en l'absence de décision de merge indépendante. Production **SHADOW**.
3. Le travail décrit ici **n'autorise ni merge, ni déploiement, ni APK de production, ni mutation Neon, ni changement de signing/permissions Cloud**.
4. Le `mediaexperiment` reste une preuve physique isolée : l'export Android d'actions debug, les commandes ADB et les fenêtres interactives anciennes ne sont **pas** des composants à copier dans `app`.
5. L'absence de demande `AudioFocus` dans le test voix et son atténuation OEM constatée ne doivent pas être transformées en promesse cross-device. Le DUCK explicite du Spike 1.0 reste un échec.
6. Aucune extraction de sous-titres en direct depuis la TV, aucun détournement DRM, aucune publicité injectée pendant un programme tiers. Droits/licences des assets et conditions d'usage des plateformes sont des gates séparées.

## 1. Socle existant à respecter

- **M4** : installation durable, `ExecutionRequirements`, capacités de la TV, validation, prepare/commit/restore, ACK et provenance.
- **M5** : `SceneEvent` / `MediaCalendar`, source temporelle **MEDIA**, conservation des invariants pause/freeze/seek/replay et moteur passif. À M5, ce scheduler **n'émet aucune commande native PAUSE/PLAY** ; ne pas enfreindre cet invariant en glissant le transport dans le scheduler.
- **M6** : exécution **WALL** distincte pour Banner et chemin de livraison PACKAGE_V1 ; conserver sa politique d'ownership et l'exclusivité des surfaces Video/Banner. Un événement VIDEO MEDIA ne doit pas devenir un événement WALL par opportunisme.
- La trajectoire post-M6 déjà retenue prévoit : assets contrôlés → modèle Generic Multimedia Track → plateforme publique de tracks → SceneVibe Studio → identité/publication créateur. **Ne pas fixer arbitrairement de numéro M7/M8.**
- **Observation Spike** : audio simultané et interlude vidéo reposent sur des politiques de lecture **différentes** ; un simple changement de renderer ne suffit pas.

Sources à relire :
- `docs/scenevibe-os-m5-scene-event-media-calendar-architecture.md` (TV).
- `docs/scenevibe-os-m6-banner-wall-clock-architecture.md` et `docs/m6-phase-e-final-qualification-report.md` (TV).
- `docs/m6-phase-e-final-cutover-qualification-report.md` (Cloud).
- `docs/experiments/media-interlude-poc-report.md` et le présent dossier Spike (TV).

## 2. Décision proposée : événements multimédias dans un même track, transport séparé

**Hypothèse architecturale à confirmer par audit des modèles M5 et du PACKAGE_V1 M6** :

| Intention éditoriale | Horloge | Effet TV | Propriété de transport |
| --- | --- | --- | --- |
| Commentaire texte | MEDIA | Overlay non interceptant | **Aucune** |
| Commentaire audio local | MEDIA | Lecture d'un asset voix borné | **Aucune** commande native ; politique audio à mesurer |
| Interlude image/vidéo avec son | MEDIA | Fenêtre native bornée de présentation | **PAUSE volontaire acquise, puis PLAY strictement gardé** |
| Banner programmé | WALL | Overlay Banner M6 | Pas de transport Prime ; propriétaire WALL distinct |

Ces **intentions sémantiques** peuvent relever d'un Generic Multimedia Track commun ; elles ne constituent pas un nouveau type de `Track Banner` ou un deuxième moteur MEDIA. Les noms de types et les clés de payload sont **à concevoir**, pas des APIs existantes.

Séparer strictement :
- **Déclenchement** : le calendrier MEDIA existant détermine la fenêtre d'éligibilité et émet l'événement DUE. Aucun transport dans `MediaCalendarScheduler`.
- **Éligibilité** : matcher identité du média, session et état natif actualisé, tolérances M5 inchangées.
- **Orchestration** : un *MediaInterludeCoordinator* proposé posséderait une machine à états bornée et un lease de pause ; il serait distinct du rendu.
- **Ports Android** : session/transport, audio local et rendu vidéo local derrière interfaces testables.
- **Rendu** : observer les règles d'ownership `OverlayService` et coexistence/exclusion avec les surfaces existantes, notamment Banner WALL.
- **Assets** : fichiers prévalidés et identifiés par hash/version via le futur dépôt d'assets contrôlés ; **pas** de dépendance à ADB, chemin externe expérimental ou URL libre non autorisée.
- **Contrôle utilisateur** : options individuelles ON/OFF pour commentaires écrits, commentaires audio et interruptions ; tout désactivé par défaut lors de la première mise en service, sans auto-activer les fonctions non qualifiées.

### Contrats de sécurité de l'interlude

1. N'émettre PAUSE que si l'original est actuellement PLAYING, qu'une session/media identity cible vérifiée existe, et qu'aucun autre opérateur ne possède le transport.
2. Confirmer réellement PAUSED avant d'ouvrir l'interlude ; refus/timeout → STOP local sans PAUSE/PLAY supplémentaire.
3. L'interlude a un budget de durée, un owner/attempt/generation et une stratégie d'annulation. Les callbacks tardifs, le double démarrage et les assets défectueux ne doivent pas recréer d'overlay.
4. À fin normale : relire session, identity, état et propriété de pause ; autoriser **au maximum une** commande PLAY et exiger la confirmation PLAYING. En cas de changement de session, pause opérateur, autre application, permission perdue ou doute → **NE PAS reprendre**.
5. `STOP/HIDE` / disparition de session / erreurs de décodeur / arrêt service retirent la voix et le rendu et abandonnent les ressources **sans PLAY natif**.
6. Tout contrôle overlay doit être passif et non focusable sur la TV ; la commande ADB du Spike n'est pas la surface UX de production.

### Contrats audio de la voix

- Lire une voix bornée uniquement si la session native MEDIA est éligible. **Pas** de PAUSE/PLAY du lecteur pour cette intention.
- Revalider périodiquement la session et le media identity ; interruption ou remplacement → STOP voix local.
- AudioFocus/atténuation : **détection et politique par provider/appareil** à définir après caractérisation. Sur Sony/Prime testé : audio original atténué pendant la voix puis restauré ; ce comportement est observé, mais son mécanisme et sa portabilité sont inconnus.
- Valider le cas audio inaudible, volume irrécupérable, casque/Bluetooth, réglages d'accessibilité, mute et changement de sortie audio avant tout support produit.

## 3. Work order proposé, dans l'ordre

### Phase A — audit architectural **en lecture seule** (prochaine opération recommandée)

**Périmètre** : inventaire exact du code TV M5/M6, du Cloud PACKAGE_V1, des formats `OverlayManifest`, des `ExecutionRequirements`, et du Spike qualifié. Ne rien modifier hors nouveaux documents d'analyse isolés explicitement autorisés.

**Livrables** :
1. Graphe de responsabilités : `SceneEvent`, `MediaCalendar`, `MediaCalendarScheduler`, `OverlayService`, `SceneRenderer`, `InstallationHandlerRegistry`, `CloudPackageInstallationAdapter`, les handlers Video/Banner et leurs ports existants.
2. Matrice des types : ce qui peut être porté par les événements MEDIA existants, ce qui exige un Generic Multimedia Track futur, et ce qui est **incompatible** avec les contraintes M5/M6.
3. Matrice de sécurité : ownership (TV/Cloud), ressources, arbitrage audio, R0 non-focus, portabilité par device/provider, STOP et restauration après crash.
4. Proposition d'interfaces pures et preuves nécessaires avant chaque ajout, sans figer de champ JSON ou API non audité.
5. Décision GO/NO-GO pour une phase B **logicielle isolée**, sans implémentation sur `app`.

**Critère PASS Phase A** : audit ancré sur HEAD GitHub, aucun changement de code runtime, M6 et le Spike restent intacts, risques et contrats différés identifiés, proposition compatible avec les invariants MEDIA/WALL.

### Phase B — logique pure et tests différentiels, si Phase A autorise

Spécifier le coordinteur d'interruption, l'identité de session et le lease de PAUSE sous interfaces Java testables. Tests déterministes : pause confirmée, pause refusée, timeout, changement de token, seeks avant/pendant/après, double DUE, media change, stop, callbacks tardifs, erreur asset, abandon service. Aucun `PLAY` sans lease et revalidation. Vérifier absence de régression M5/M6 et Video/Banner.

### Phase C — assets contrôlés et pilote Android isolé

Prouver signature/hash des médias, codecs/durations, préflight avant pause, cache/restauration, limites de stockage et licences. Intégrer les ports sur une branche/qualification TV séparée **après décision** ; conserver R0 et les garde-fous du Spike. Les fixtures expérimentales de 10 s ne sont pas un catalogue éditorial.

### Phase D — contrat Cloud / distribution / Studio, après autorisation distincte

Définir un `MediaAssetRef` immutable et les intentions audio/interlude dans le futur modèle de track générique. Publication versionnée, validation source/droits, capacité requise, activation opt-in, compatibilité des vieux tracks, package/ACK exact, aucun changement silencieux du codec Video/Banner M6. SceneVibe Studio devrait permettre de placer les voix et interruptions sur la timeline MEDIA, prévisualiser pause/reprise et vérifier les collisions.

### Phase E — qualification physique multi-cas et décision produit

- Sony/Prime : reproduire R0, A1, A2 avec logs horodatés et tests d'annulation / mauvaise identité, plusieurs épisodes, seeks et retour de volume.
- Autres apps TV / appareils : chaque couple provider + Android/OEM produit son propre verdict ; aucune « capacité universelle » non prouvée.
- Non-régression M6 Banner WALL / Video MEDIA / cache / installation / télécommande / reboot, et final-track legacy.
- Matrice feature ON/OFF : texte seul, audio seul, interruption seule, combinaisons; ownership exclusif et priorité définie.

## 4. Synchronisation et intelligence éditoriale — risque à traiter

Les commentaires audio et les interruptions sont des **événements synchronisés au média** ; leur lecture et leur reprise doivent suivre la position réellement observée par M5, pas une horloge murale réinventée. La vidéo insérée par SceneVibe fige le média natif, puis restitue la même position. Les publicité/interstitiels **de la plateforme** peuvent en revanche introduire des discontinuités ou des périodes où la MediaSession reste PLAYING sans relation simple avec la timeline attendue.

Le pipeline documentaire / LLM de production des tracks doit donc intégrer un **test de discontinuités et de changements d'identité**, ainsi que la traçabilité de la position observée et la règle de non-rattrapage intempestif après interruption. Ce sujet est un risque d'architecture et de qualité des pistes, **non résolu** par un A1/A2 physique sur un seul épisode.

## 5. Contrôle de fin et décision

**État actuel :** physique Spike 2.0.2 qualifié sur Sony/Prime ; **aucune intégration SceneVibe OS autorisée par ce document**.

**Prochaine action concrète : lancer la Phase A en lecture seule avec WORK**, sur les HEAD courants des deux dépôts. WORK doit d'abord produire un dossier d'architecture / décision GO-NO-GO, puis obtenir une nouvelle autorisation avant Phase B et a fortiori avant tout changement `app`, Cloud, Neon ou M6.

Ne pas fusionner PR #17 et ne pas supposer qu'un prototype Android signé pour le debug représente un composant réutilisable en production.
