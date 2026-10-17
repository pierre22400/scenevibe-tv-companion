# TV-FIRST 006 — observation MEDIA passive

Décision propriétaire du 17 octobre 2026 : développement et qualification locale
isolée seulement. Architecture de référence : TV-FIRST 005. GitHub reste source de
vérité ; ce delta local n'est ni publié ni candidat à une installation.

## Profil et frontières

La variante `observation`, dérivée de release, fixe `TVFIRST_OBSERVATION_ONLY=true`,
`CLOUD_ORIGIN=""`, `ENABLE_LAN_DEV=false` et aucune signature. Debug et release
gardent le flag d'observation faux. La propriété explicite
`scenevibeQualificationUnsigned=true` désactive aussi la signature debug pendant
la qualification, sans changer sa configuration par défaut hors qualification.
La source d'offres release est réutilisée sans modification ; aucun corpus debug
n'entre dans l'observation.

`OverlayService.onStartCommand` route l'observation avant toute lecture de position
persistée, contrôle d'overlay, restauration, scheduler, installer, Cloud ou LAN.
Toutes les actions, y compris reset/connect, boot et Intent nul, prennent ce chemin.
Seuls la notification Android de service et l'état mémoire de diagnostic sont
créés dans `onCreate`. Aucune fenêtre de commentaire n'est créée. Les deux entrées
de renderers et le SceneSink ont leurs propres fermetures. Aucun moteur de
calendrier ou d'ACK alternatif n'est créé.

Une seule `MediaSessionProbe` existe dans le service. Sa nouvelle branche appelle
un lecteur distinct et n'émet aucun `Snapshot` historique. Les constructeurs,
le snapshot et le corps de sélection historique restent disponibles pour le mode
par défaut. Le lecteur appelle uniquement `getActiveSessions` et les getters des
contrôleurs ; jamais leurs commandes de transport.

Le callback capture un writer immuable de durée de vie. La destruction ferme ce
writer avant d'arrêter la probe. Un callback tardif, une séquence répétée ou un
ancien writer après remplacement ne peut republier un fait courant. Le dernier
batch est uniquement en mémoire, supprimé à l'arrêt, sans journal de métadonnées.

`DiagnosticsActivity` affiche ce batch sans appeler la capture historique de
credentials/installation ; son reset est fermé dans ce profil, même service arrêté.
`MainActivity` est inchangée. Le retour d'écran avec origine Cloud vide ne construit
pas de credentials dans son refresh. **L'application entière n'est pas une interface
en lecture seule** : les actions explicites historiques telles que Disconnect Cloud
ou changement d'autostart restent des commandes utilisateur. Elles ne font pas
partie du protocole d'observation passive et ne doivent pas être utilisées dans
une future séance sans décision complémentaire. Aucun nouveau verrou UI n'est
introduit en violation de la frontière imposée à MainActivity.

## Faits, inconnus et refus

| Donnée | Traitement logiciel | Statut physique Sony au lot 006 |
| --- | --- | --- |
| Capture | Horloge monotone, séquence ; cadence configurée de la probe 1000 ms, non garantie temps réel | Non mesuré |
| Sessions | Toutes les sessions, maximum 16 ; au-delà source TOO_MANY_SESSIONS et aucun candidat | Disponibilité et exhaustivité inconnues |
| Token réel | Clé privée mémoire vers label local ; jamais sérialisé ni affiché ; label retiré à disparition ou perte de source | Stabilité inconnue |
| Package propriétaire | Chaîne brute bornée | Ne prouve jamais le premier plan |
| État/vitesse | Valeurs natives ; état non PLAYING et vitesse autre que 1 ou non finie donnent un refus diagnostique | Non mesuré |
| Position/ancrage | Valeurs brutes séparées ; ancrage futur non corrigé ; absence à -1 | Fiabilité et sémantique inconnues |
| Estimation | Extrapolation descriptive séparée, refus d'overflow/incohérence ; jamais horloge de commentaire | Aucun horizon d'extrapolation qualifié |
| ID/titre/sous-titre | Métadonnées réellement publiées ; null reste absent ; tailles bornées et troncature signalée | Aucun ID ni titre Sony observé |
| Durée | Clé native présente seulement ; -1 sinon | Aucune durée film mesurée ; plannedEndSec jamais substitué |
| Permissions | Lecture du droit notification et overlay ; aucune demande ou obtention automatique | Droits Sony non constatés |
| Programme/édition | Toujours UNKNOWN à l'acquisition, même ID exact ou titre concordant | UNKNOWN |
| Premier plan | Aucune source implémentée ; aucun Usage Access demandé | UNKNOWN, y compris MediaSession encore PLAYING depuis Home |
| Timeline/offset/publicité | Aucune source qualifiée | UNKNOWN |

Le modèle et les collections sont immuables. Une capture diagnostique âgée de plus
de 2500 ms ou future est marquée non courante. Ce seuil concerne seulement la
fraîcheur d'affichage du diagnostic ; il n'est pas un seuil d'ancrage Sony. Un
ancrage ancien reste observable : aucun âge universel ne l'admet à une timeline.
Les métadonnées manquantes, la permission perdue et l'ambiguïté ne sont jamais
remplacées par « Prime supposé actif ». Le modèle distingue UNKNOWN, OBSERVED,
CONTRADICTED pour les fixtures d'évaluation ; le lecteur réel n'émet que UNKNOWN
pour ces quatre preuves. Aucune admission par titre ni matching nouveau.

Même une fixture contenant toutes les preuves OBSERVED peut seulement rendre
`conditionsComplete()` vrai. `renderingAllowed()` reste faux ; le service réel
n'a pas de callback d'exécution raccordé à ce résultat. Les tests appellent ses
vrais sinks, y compris après destruction. Les écrans applicatifs et la notification
Android sont distincts des fenêtres d'overlay de commentaire.

Les exceptions de query publient QUERY_FAILED et retirent les anciennes clés sans
exposer leur message. Titres et sous-titres ne figurent pas dans le diagnostic ;
les IDs/package bornés ne sont pas des secrets d'authentification. Aucun fichier
de métadonnées, cache de session, requête Cloud, Send ou ACK n'est produit.

## Limites de qualification

Les tests JVM utilisent les classes réelles du service, de la probe, du lecteur,
du store et de l'Activity, avec les shadows Android de Robolectric. Les contrôleurs
reçoivent des faits synthétiques via ShadowMediaController : la propagation
MediaSession vers MediaController n'est pas implémentée par ce simulateur. Une
preuve de zéro vue WindowManager, des champs runtime non construits et des maps de
preferences invariantes couvre ces chemins logiciels ; elle ne mesure ni SurfaceFlinger,
ni un OS Sony, ni le réseau d'un téléviseur réel. L'absence de CloudClient construit
et d'origine Cloud fait partie de la preuve logicielle, sans émission HTTP de test
vers une route réelle. Les magasins comparés sont cloud_track, overlay,
cloud_identity, cloud_secret, installation et pairing. Le snapshot valide en attente
d'ACK et le snapshot générique corrompu restent intacts.

Le mode par défaut est vérifié par les suites M4/M5 et les 38 cas de présentation
sur la compilation courante, plus son test de routage réel. Aucune équivalence de
comportement d'un OS ou joueur natif physique n'est déduite des JVM. Aucun émulateur,
appareil, ADB, dumpsys ou lecture du programme audiovisuel n'est utilisé.

## Protocole physique futur — NON EXÉCUTÉ

Ce document n'autorise aucune étape ci-dessous. Un nouveau GO doit viser les
artefacts concrets, la continuité d'installation et le protocole final. Avant tout
candidat : vérifier applicationId, versionCode/versionName et monotonicité de mise
à jour, certificat historique et lignée de signature, source exacte installée,
compatibilité des schémas et données privées, identité durable et cache conservés,
permissions existantes, possibilité de retour arrière sans clear-data/uninstall,
et choix explicite du profil observationnel. Le présent intermédiaire unsigned ne
permet pas de prouver une mise à jour sur l'installation historique ; ne pas le
signer ou l'installer sur la base de ce lot.

Après GO séparé seulement, une séance serait limitée à constater les permissions
réelles sans les supposer, ouvrir le diagnostic passif, puis relever l'absence de
session, la lecture native, pause/reprise, un seek, le retour Home avec session
encore PLAYING, changement d'application/session, permission perdue si expressément
autorisé, arrêt/reprise du service et extinction/réveil si autorisés. Pour chaque
transition : heure monotone, numéro et écart de capture, package/label local, état,
position/ancrage/estimation, clés de métadonnées présentes/absentes et refus. Ne
pas enregistrer le programme, les sous-titres, les jetons ou les credentials.

Critères : zéro fenêtre de commentaire, zéro création de runtime d'installation,
zéro Cloud/ACK, données privées inchangées et UNKNOWN maintenu pour toute preuve
non observable. Un refus de permission ou une source silencieuse est un résultat,
pas une invitation à contourner les droits. Toute mutation, fenêtre inattendue,
secret nécessaire ou besoin de modifier les moteurs gelés impose STOP et nouvelle
décision. Aucune qualification TEXT ni synchronisation d'édition ne peut être
prononcée par ce protocole d'observation seul.
