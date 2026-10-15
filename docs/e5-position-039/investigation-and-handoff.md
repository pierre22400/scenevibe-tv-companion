# E5-039 — Sony : durée inconnue et refus STALE_POSITION

Date : 15 octobre 2026. GitHub est la source de vérité.
Base immuable : `efd3a7983a03458ec1f919d760c10f1b1f9c57ff`.
Branche : `work/scenevibe-e5-native-stale-position-039`, cible PR E5-038.

**READY FOR SONY RETEST — E5 Sony Android 12/API31/VOD attestée.**

PR Draft : https://github.com/pierre22400/scenevibe-tv-companion/pull/20
HEAD logiciel qualifié et APK : `24a1b9376eb629567bf018b1a4e967f01625688b`.
La clôture documentaire ultérieure ne modifie ni logiciel ni test compilé.
**E5 PHYSICALLY QUALIFIED : non acquis.**

## Faits physiques et portée de la preuve

Le journal original `scenevibe-e5-038-logcat.txt` a été récupéré intégralement :
1 113 616 octets, UTF-16LE avec BOM, SHA-256
`14b4bbf1c7c8cab84f87681af200d61d631a0f75ed36bb6903b0d63f86b09724`.
Les [extraits expurgés](sony-evidence.json) conservent les numéros de lignes.
Le fichier contient **une** tentative complète, génération 3. Les deux essais
rapportés par Pierre ne deviennent pas artificiellement deux traces enregistrées.

| Ligne | Observation |
|---|---|
| 2982 | PLAYING, position brute 2 474 518 ms, âge provider 1 ms, durée -1 |
| 3051 | réception ADB QUEUED_GUARDS_PENDING |
| 3053–3054 | REQUEST puis PREFLIGHT / PREPARING |
| 3508 | PLAYING, brute 2 475 551 ms, estimée 2 475 557 ms, âge 6 ms, durée -1 |
| 3510–3511 | PLAYER_READY / PAUSE_REQUESTED puis NATIVE_ADMISSION_STALE_POSITION / REFUSED |
| 3561 | CLEANUP / REFUSED |
| 3596 | Prime toujours PLAYING, position 2 476 561 ms |

Aucun TARGET_VALIDATED ni PAUSE_SENT n'apparaît. **PAUSE_REQUESTED est une phase de
contrôleur, pas la preuve d'une commande envoyée.** Le refus observé précède PAUSE.
Le reason STALE_POSITION apparaît dès REQUEST parce que l'ancien owner conservait
le refus précédent : ce n'est pas une deuxième évaluation anticipée de la garde.

## Chemin causal et condition fautive

1. `E5AdbTriggerReceiver.onReceive` contrôle DUMP, enveloppe explicite et attestation.
2. `E5LocalRequest.confirmedVod` fournit l'intent unique d'`OverlayService`.
3. `OverlayService.requestE5Local` vérifie Sony/API31/VOD, foreground et disponibilité.
4. `SceneRuntimeController.requestLocal` préflighte le slot et prépare la fixture.
5. `localReady` entre en PAUSE_REQUESTED puis appelle `NativeTransportOwner.requestPause`.
6. `AndroidNativeSessionPort.select/choose/snapshot` relit le même controller deux fois.
7. `NativeQualificationPolicy.fresh` E5-038 exigeait `sample.durationMs > 0`, avant
   d'examiner les âges. `durationMs == -1` échoue donc, même avec une position récente.
8. L'owner renvoie STALE_POSITION sans bail ni dispatch ; `finishLocal` libère le
   player, retire le host et termine REFUSED. Aucun PLAY de récupération n'est envoyé.

Défaut reproduit : la notion de fraîcheur était couplée à une durée obligatoirement
connue. Deux autres conditions auraient aussi bloqué la même cible :
`requestPause` comparait l'estimation à -1 et `sameTarget` exigeait une durée positive.
Ces trois usages sont corrigés ensemble, sans remplacer de valeur inconnue par une
fausse durée. L'adaptateur Android et la surveillance historique restent byte-exact.

La documentation Android indique qu'une durée négative représente une durée inconnue
ou infinie ; un identifiant natif est spécifique au service. **La durée -1 ne prouve
pas la VOD.** L'admission dépend toujours de l'attestation explicite du propriétaire.
Références primaires consultées le 15 octobre 2026 :
- https://developer.android.com/reference/android/media/MediaMetadata#METADATA_KEY_DURATION
- https://developer.android.com/reference/android/media/MediaMetadata#getLong(java.lang.String)
- https://developer.android.com/reference/android/media/session/PlaybackState#getLastPositionUpdateTime()

Les valeurs brutes exactes du snapshot privé du contrôleur n'étaient pas journalisées
au moment du refus. Le journal montre les valeurs du probe immédiatement voisin et
le même package ; le défaut logiciel est démontré et reproduit avec ce type d'entrée.
Il ne permet pas d'exclure un second refus concurrent non journalisé. La candidate
expose précisément l'entrée et la garde évaluée pour lever cette limite au rejeu Sony.
Les temps du replay sont construits sur la chronologie observée ; ils ne sont pas
présentés comme des horodatages provider extraits du snapshot privé original.

## Distinctions de données et hypothèses

| Donnée | Usage |
|---|---|
| positionMs | position brute publiée par le provider, négatif = indisponible |
| estimatedPosition | brute + avance monotone en PLAYING/vitesse 1 uniquement |
| updatedAtMs | dernière mise à jour provider, base elapsedRealtime en ms |
| observedAtMs | réception de notre double lecture, même base monotone |
| âge réception / provider | maintenant après query moins observed / updated, limites 1000 / 20000 ms |
| PAUSED | position brute stationnaire, aucune extrapolation ; marque provider après notre PAUSE |
| ancre détenue | même token, package, ID, métadonnées, durée, position exacte et marque provider |
| durationMs -1 | inconnu, jamais converti en position ou date d'expiration |

Le chemin examiné utilise `elapsedRealtime` et `getLastPositionUpdateTime`, en ms :
aucune conversion nano/ms ou temps système n'est présente dans cette garde.
Les horodatages système du foreground sont traités séparément par son adaptateur.
L'explication par vieillissement pendant le clip ou après pause ne correspond pas
à cette trace : aucun PAUSE n'a été envoyé. Aucun seuil n'est augmenté.
Une course/session différente demeure une limite générale des lectures Android ;
les lectures cohérentes et toutes les revalidations au dispatch sont conservées.

## Correction bornée et garanties maintenues

`NativeSessionSnapshot.usableDurationEvidence` accepte une durée positive, ou
**strictement -1 avec ID natif non vide et attestation VOD**. Zéro/clé manquante,
autre valeur négative et identité fondée seulement sur un titre restent refusés.
L'égalité exacte de durée et de toutes les dimensions d'identité demeure obligatoire
à chaque relecture, y compris lorsque la durée passe d'inconnue à connue.
Les durées connues conservent leur borne de position et le refus de fin de média.
Les positions indisponibles, incohérentes et débordantes sont refusées.

Aucune garde d'âge, génération, foreground, actions, confirmation PAUSED/PLAYING,
ancre exacte, EOF normal, watchdog, timeout ou récupération n'est désactivée.
`NATIVE_ADMISSION_STALE_POSITION` reste le refus pour les données insuffisantes.
Aucune reprise sur STOP/erreur/perte de session. Aucun PLAY aveugle. Aucun Cloud.
L'absence de durée connue supprime seulement la borne supérieure native inconnue ;
la reprise dépend de l'ancre PAUSED exacte, pas d'un calcul à partir de cette durée.
Cette modification du profil est explicite et justifiée, pas un relèvement de seuil.

`beginAttempt` remet à NONE un refus retiré, uniquement sans bail. Aucun bail actif
n'est remis à zéro. Les événements de garde enregistrent le temps post-query et le
résultat exact de l'évaluation, plutôt qu'une estimation de son âge au moment du log.

## Reproduction et tests

[Replay unique avant/après](reproduction/SonyAdmissionReplay.java), exécuté par
`.github/scripts/e5-position-039-reproduce.py` : sources immuables E5-038 compilées
fresh, même scénario et assertion pour la candidate. Échec attendu avant correction :
REFUSED / STALE_POSITION / pauses=0. Après : PAUSE_REQUESTED / NONE / pauses=1.
Envoi PAUSE ne signifie toujours ni interlude commencé ni réussite physique.

`SonyStalePosition039Test` couvre le nominal complet avec PLAYING progressif,
PAUSED fourni indépendamment, EOF et reprise confirmée ; provider/réception périmés,
position absente, bases murale/nano/secondes, observation future, VOD/token/ID absents,
durée nulle/invalide, changement de session, seek 1ms, nouvelle marque PAUSED et âge
excessif, fin connue et débordement. Les 64 tests E5 historiques restent byte-exact.
`NativeDiagnostics039Test` vérifie âges, positions, anonymisation, identifiant de
tentative et état terminal. Les sondes Android emploient un MediaSession synthétique
pour lire de vraies métadonnées -1, PLAYING puis PAUSED ; elles ne simulent pas Prime
comme s'il avait été testé sur matériel.

Les inventaires historiques restent exacts par SHA de fichiers complets et inverses
littéraux finis dans les deux registres. Seules leurs comparaisons de source utilisent
la reconstruction historique. Les JVM et Android exécutent **les vrais bytes courants**.
Aucun ancien test négatif, assertion, docstring ou skip n'est retiré.

## Qualification, APK et risques résiduels

Résultats finaux vérifiés sur le HEAD logiciel
`24a1b9376eb629567bf018b1a4e967f01625688b` :

- Local : 131 Python PASS ; 80 JVM E5 PASS (64 historiques + 14 Sony + 2 diagnostics).
- CI OFF et ON : **1131 cas par profil, 1130 PASS + 1 SKIP historique, zéro échec**.
  Les XML téléchargés ont été reconcomptés indépendamment dans [jvm-summary.json](jvm-summary.json).
- Builds debug/release OFF ; debug/AndroidTest ON ; lint : software SUCCESS.
- Replay identique : [échec attendu E5-038](reproduction/before-result.txt),
  puis [PASS E5-039](reproduction/after-result.txt).
- PowerShell : ParseFile local et CI PASS ; faux ADB local, argv exact, une demande
  par invocation, capture complète, terminal REFUSED interprété comme refus,
  sortie non nulle sur rejet, aucun PLAY/PAUSE ni retry.

Run de qualification :
https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38668622187
Artefact logiciel : 11942423451 ; digest ZIP revérifié
`dd881714648829214294aecc13d72f6643ca0292ab429c8858544feba3f573a7`.
Les APK OFF/ON ont été inspectées localement : identité, version, flags compilés,
fixture, composant DUMP et absence OFF conformes.

Le premier run 38667880726 a compilé et exécuté les JVM avec succès puis échoué
au compteur figé des suites M4/M5, qui ignorait les deux nouvelles classes E5-039.
Le deuxième commit ajoute exactement 14 et 2 cas à cet inventaire ; aucune assertion
ancienne, fixture ou règle de skip n'est modifiée. Le premier échec reste visible.
Le run 38668130263 a ensuite passé tout le logiciel, mais la nouvelle sonde Android
s'est arrêtée à ANDROID_UNKNOWN_DURATION sur sa première lecture immédiate de
MediaSession synthétique. Le dernier ajustement attend au plus 2s la publication
cohérente de cette fixture, sans modifier l'adaptateur ni aucune garde du runtime.
Un timeout affiche l'état/durée réellement disponibles ; il ne devient pas PASS.
La signature est restée SKIPPED sur ce run en échec.
Les inventaires Python provisoires ont également refusé les fichiers non encore
admis ; ils ont ensuite tous passé après l'admission exacte, sans wildcard.

Les builds Gradle locaux ont échoué **avant compilation**, au téléchargement du
plugin Android via le réseau du poste de travail. Ils ne sont pas déclarés PASS.
La compilation/lint/suite complète retenues sont celles du runner CI du HEAD exact.
Les 80 JVM critiques ont été compilés localement depuis les sources courantes et
exécutés avec JDK 17, JUnit réel et JSON réel, indépendamment de cette difficulté.
Le run final compte trois jobs **SUCCESS** : software, platform31, stable-candidate.
Les sondes API31 et leurs fichiers ont été téléchargés et relus : 20 assertions
réelles (15 enveloppe/permissions + 5 conversion Android), émetteur non autorisé
sous UID 10146 refusé (1 assertion), même foreground Settings avant/après, commande
malformée refusée, endpoint OFF absent. Le PAUSE/PLAY Prime n'est pas exercé par
ces sondes ; le nominal complet est couvert par les observations indépendantes JVM.

La candidate a été signée uniquement après ces succès. ZIP et APK ont été vérifiés
localement : égalité de tout le payload applicatif avec l'APK ON testée, certificat
API31, package, version, flags DEX, fixture et SHA-256. La référence E5 stable
47f675db a été revérifiée localement par apksigner : même certificat f908…a2e9c.
Le mode debug opt-in reste intentionnel et Sony/API31 seulement.

| Candidate | Valeur |
|---|---|
| Fichier | SceneVibe_E5_039_stable.apk |
| applicationId | com.scenevibe.tvcompanionpoc |
| versionCode | **16 > 15** |
| versionName | 0.8.2-tv-release-hardening-e5-native039-position |
| SHA-256 APK | aacda02ac414e9d4e8a885870fcaf5f4e444eeb0db746a7c3aef80231d02db64 |
| Certificat SHA-256 | f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c |
| Cloud / LAN / qualification E5 | vide / OFF / ON |

[Preuve indépendante APK](candidate-proof.json).
Artefacts du run final (digests ZIP revérifiés) :
- software 11942423451 : `dd881714648829214294aecc13d72f6643ca0292ab429c8858544feba3f573a7` ;
- platform31 11943228780 : `965863e3d59cc578106fc0c39b08ee2b51a33bd2179b51083e7cf43268dece0d` ;
- candidate 11943138804 : `c3eecd5fdd8d3361d5a4f5a3795c4f086f8bf9bf7c8a22ccc35fd69283b70ea8`.

La procédure et [le script PowerShell](Invoke-E5-039.ps1) ne sont pas exécutés sur
Sony. Le script est livré en UTF-8 avec BOM pour Windows PowerShell 5.1 ; le fichier
final a été reparsé localement. Les modifications finales de documentation/encodage
ne sont pas attribuées à une nouvelle compilation APK.
Signature uniquement avec la clé stable déjà configurée, sans extraire de clé vers
le poste de travail, ni générer une nouvelle identité.

La base E5 demeure dérivée de M5 ; Banner M6 n'est pas incorporé dans cette APK.
La branche M6 contrôlée séparément reste
`60d3e25c604cd1a0ad0c62f00d97b13c284ed103`. Aucun fichier/contrat Banner, calendrier,
pairing, installation, stockage, Cloud ou permission main n'est modifié. Cela ne
constitue pas une nouvelle qualification de coexistence E5/M6 dans un même binaire.

Restent à observer sur Sony : capacités/actions publiées par Prime, confirmation
PAUSED, ancre stationnaire, visibilité/audio natifs, EOF et reprise correcte.
Un changement OEM bénin de marque PAUSED peut toujours provoquer RECOVERY_REQUIRED.
Dans ce cas, la reprise manuelle appartient au propriétaire ; aucun retry automatique.
Les données live mal attestées ne peuvent pas être identifiées comme VOD par la seule
métadonnée -1 : le mandat reste manuel et limité à l'épisode VOD attesté.

Aucune installation/intervention Sony, fusion, Production, Cloud, Vercel ou Neon.


## Références des fonctions modifiées et décision de clôture

| Source | Fonctions / rôle |
|---|---|
| [NativeQualificationPolicy.java](../../app/src/main/java/com/scenevibe/tvcompanionpoc/NativeQualificationPolicy.java) | fresh / evidence : âge, cohérence et durée distingués |
| [NativeSessionSnapshot.java](../../app/src/main/java/com/scenevibe/tvcompanionpoc/NativeSessionSnapshot.java) | usableDurationEvidence / sameTarget : ID explicite, égalité exacte |
| [NativeTransportOwner.java](../../app/src/main/java/com/scenevibe/tvcompanionpoc/NativeTransportOwner.java) | requestPause / sameFresh / beginAttempt : admission, observations et motif retiré |
| [NativeDiagnostics.java](../../app/src/main/java/com/scenevibe/tvcompanionpoc/NativeDiagnostics.java) | evaluated / describe : tentative, garde réelle, données et terminal |
| [SceneRuntimeController.java](../../app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRuntimeController.java) | requestLocal : seule remise à NONE du motif précédent |

La correction logicielle et sa candidate sont prêtes pour une décision de rejeu.
Le prochain geste est un **GO propriétaire distinct** pour la mise à jour en place
et le nouveau test physique décrit dans [la procédure](Procedure_PowerShell_E5_039.md).
Le critère physique reste pause effective de Prime, interlude image+son puis reprise
correcte observés par Pierre. Aucun résultat logiciel ne le remplace. STOP après livraison.
