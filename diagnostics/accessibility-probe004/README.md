# SceneVibe Probe 004 — privé

Application autonome com.scenevibe.accessibilityprobe004, version 0.0.4-private-diagnostic, versionCode 1, minSdk 31. Branche issue de Probe003 qualifiée dbe5f4a484d78a3cdc9a9a054ffd9704d9574c75. Probe003 et le Companion ne sont pas modifiés.

## Finalité

Cette instrumentation répond à l'angle mort de Probe003 : sans callback, aucune relecture n'avait lieu. Un seul worker relit maintenant les racines publiques à cadence bornée, sans commander Netflix/Disney+. Aucun AD_START, AD_END ou moteur de protection n'est implémenté. Une catégorie AD_LABEL_CANDIDATE reste une observation lexicale, jamais une preuve automatique.

## Architecture et limites

- Connexion désarmée, armement volontaire volatil, un seul package parmi OWN_CONTROL, NETFLIX, DISNEY_PLUS.
- NORMAL : ticks à 2 s ; DENSE_30_SECONDS : 1 s pendant les 30 premières secondes de campagne, puis 2 s. Le mode est choisi avant armement.
- EVENT_ONLY_CONTROL : comparateur synthétique réservé à OWN_CONTROL ; aucun tick périodique.
- Les événements déclenchent aussi des lectures bornées, au plus toutes les 250 ms globalement, sans deuxième parcours concurrent. Un événement coalescé reste enregistré ; le tick suivant relit l'état courant.
- Un worker sérialisé et un indicateur busy empêchent chevauchement et accumulation. Un tick en retard n'est pas rejoué en rafale.
- Tous les nœuds interprétés reçoivent refresh public. La racine et les descendants peuvent néanmoins rester incomplets parce que le fournisseur de nœuds ne reflète pas tous les pixels.
- Une fenêtre déjà visitée n'est pas traversée à nouveau par source/racine/fenêtre. Les relations parentIndex/nodeIndex sont locales à un snapshot ; comparaison uniquement sur projections scalaires, jamais hash de texte.
- Maximum 600 s, 2 000 000 octets avec réserve de clôture, 200 tentatives de lecture, profondeur 12, huit fenêtres. Budget souple 250 ms, sans pouvoir préempter un Binder engagé. Deux durées de parcours consécutives >500 ms arrêtent la campagne (OVERLOAD).
- Arrêt : annule le timer, invalide la génération puis ferme le fichier. Un Binder déjà engagé peut revenir tard ; aucune nouvelle lecture ni écriture dans une campagne suivante n'est admise. observationInFlightAtStop indique cette limite.
- RangeInfo est lu sur les nœuds non password déjà visités, uniquement si présent ; nombres finis et ordonnés, unité toujours UNKNOWN. Aucune conversion en temps du film.
- Le mode observation ne survit ni à la reconnexion ni au redémarrage du processus. Pas de wake lock, service de premier plan, réseau, boot receiver, capture, overlay, gestes, touches ou focus demandé.

## Journal et confidentialité

UTF-8 sans BOM, UUID de campagne technique et séquences, horloges de réception uptime/elapsedRealtime/murale ; eventTimeUptimeMs séparé. Ne pas soustraire ces bases entre elles. Les enregistrements TICK_START/TICK_END encadrent toute exploration ; trigger distingue INITIAL, ANDROID_EVENT et PERIODIC_TICK. Ils portent snapshotId, début/fin, retard, callbacks depuis l’exploration précédente et, pour PERIODIC_TICK, callbacks depuis le tick périodique précédent, CPU du processus, PSS et tas natif. Les opérations déjà engagées à l'arrêt sont explicitement non préemptibles.

NODE décrit rôle/ID public catégorisé, enfants, profondeur, parent local, visibilité/focus/sélection/activation, présence et catégories fermées de texte/description, fraîcheur et plage numérique. Aucun texte libre, titre, compte, URL, token ou hash stable de contenu n'est exporté. Password interdit l'accès aux payloads texte/description/plage. Les classes et IDs inconnus ne sont pas exportés librement.

SNAPSHOT décrit tentatives, nœuds frais, racines, échecs, troncature et différence structurelle. FRESH_OK signifie lecture publique réussie, pas absence de publicité. ROOT_UNAVAILABLE, REFRESH_FAILED, STALE_NODE, FOREIGN_PACKAGE, INCOMPLETE, TRUNCATED et TIMEOUT_OR_DELAY restent des états indéterminés. Un échec invalide la comparaison structurelle précédente.

Les journaux app-privés sont purgés après 24 h lorsque le processus s'exécute, jamais garantie à l'heure exacte si l'app ne tourne pas. Export et suppression sont volontaires après arrêt. Un journal sans SESSION_END est incomplet. droppedEventCount ne mesure pas les événements Android jamais livrés.

## Contrôle positif silencieux

SilentFixtureView appartient à la sonde. Son AccessibilityNodeProvider publie des enfants virtuels, un libellé synthétique et une plage numérique. setSilentPhase change uniquement un entier : aucun événement n'est envoyé, aucun texte de View n'est modifié, aucun invalidate n'est demandé.

Le bouton de préparation planifie cette mutation cinq secondes plus tard ; les événements produits par le clic doivent donc précéder la mutation. Le test Android automatise le comparatif avec un marqueur synthétique : aucun callback natif après ce marqueur, aucun candidat frais en mode événementiel seul, puis nouveau candidat NODE/FRESH_OK/PERIODIC_TICK en mode normal. Les deux journaux sont conservés comme preuves synthétiques.

Cela prouve une capacité instrumentale sur le banc, jamais une détection Netflix. Le signal d'un nœud resté opaque ne peut pas être créé par le polling.

## Qualification reproductible

Depuis la racine du dépôt : bash diagnostics/accessibility-probe004/tests/core-checks.sh ; puis python3 -B -m unittest discover -s diagnostics/accessibility-probe004/tests -p 'test_*.py' -v.

Build indépendant avec Gradle 8.9 / JDK17 / Android SDK35 : gradle -p diagnostics/accessibility-probe004 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug. Aucun build/signing du Companion n'est lu. APK de diagnostic debuggable pour extraction locale run-as ; certificat debug généré sur le banc, distinct de M5, clé privée jamais exportée.

Le workflow dédié accessibility-probe004.yml prépare Android TV API31 x86. emulator-checks.sh exige emulator-5554 et ro.kernel.qemu=1 avant installation/activation. Tous les runners Android refusent également le matériel autre que ranchu/goldfish avant setup. Aucun appareil physique n'est autorisé. Les tests historiques conservés concernent le noyau copié, les frontières et le contrôle Android ; la non-régression des autres modules est démontrée par diff, pas par recopie des résultats historiques.

Les résultats et limites réellement exécutés sont dans l'artefact du workflow et le rapport de livraison, jamais présumés PASS par ce README.

## Références et divergences résolues

PROBE004-CONCEPTION.md, SCENEVIBE-PROBE003-PHYSICAL-A-RAPPORT.md et RECHERCHE-INTERNATIONALE.md ont été lus avant développement. Le GO du 31 octobre remplace leur statut « conception seulement » pour le développement et la PR ; il conserve le STOP physique. Le GO fixe ici l'identité 004 indépendante. L'observation de source d'événement reste bornée à une copie détenue par un unique worker, les racines complètes étant prioritaires pour éviter les doubles comptes.

## Arrêt

PROTOCOLE-SONY.md décrit uniquement les étapes futures. STOP avant Sony, campagne réelle, modification de Probe003/Companion/Prime, fusion, Cloud ou publication. PASS SOFTWARE éventuel reste distinct de PASS INSTRUMENTAL SONY, CANDIDATE AD SIGNAL et PASS AD DETECTION.
