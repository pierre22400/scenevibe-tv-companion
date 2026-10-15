# E5-039 — Sony : durée inconnue et refus STALE_POSITION

Date : 15 octobre 2026. GitHub est la source de vérité.
Base immuable : `efd3a7983a03458ec1f919d760c10f1b1f9c57ff`.
Branche : `work/scenevibe-e5-native-stale-position-039`, cible PR E5-038.

**QUALIFICATION EN COURS — aucune qualification physique revendiquée.**

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

Résultats et identité de candidate à consolider après les jobs de qualification.
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
