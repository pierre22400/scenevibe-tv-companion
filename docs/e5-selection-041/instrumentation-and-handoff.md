# E5-041 — instrumentation observationnelle de la sélection native

Date : 15 octobre 2026. GitHub est la source de vérité.
**READY FOR SONY DIAGNOSTIC** — instrumentation et APK signée qualifiées.
HEAD logiciel/APK : `098f68aa522b100f441bf0aafa6242a1baaaf27b`.
PR [#22](https://github.com/pierre22400/scenevibe-tv-companion/pull/22) OPEN/DRAFT/UNMERGED.
E5-039 reste PHYSICAL FAIL ; E5-041 n'est pas une correction fonctionnelle.

## Références et périmètre

Base vérifiée : `0206a498c412dbf70535295e90426488cadede91`, PR #21 OPEN/DRAFT/UNMERGED.
PR #20 vérifiée OPEN/DRAFT/UNMERGED au HEAD `b97aa06885b264d64274dc65f054ffbf2cd6e53e`.
Le checkout initial était propre ; aucun AGENTS.md applicable n'a été trouvé.
Branche dédiée : `work/scenevibe-e5-selection-diagnostics-041`, PR Draft vers
`work/scenevibe-e5-native-admission-040`.

Le [rapport E5-040](../e5-admission-040/investigation-and-handoff.md) est la base causale.
Les trois tentatives et leurs extraits restent les preuves Sony antérieures ; aucun nouveau
journal physique ni nouvelle observation sur la TV n'est inventé ici.

Le delta applicatif est limité aux observations de `AndroidNativeSessionPort`,
`AndroidForegroundEvidence`, `NativeForegroundProof`, `NativeDiagnostics`, leur raccordement
dans `OverlayService`, au nouveau conteneur `NativeSelectionTrace` et à la version ON.
Les sondes Android ajoutent dix assertions aux vingt existantes. Les gardes/états du
`NativeTransportOwner`, sa policy, `NativeSessionSnapshot`, le receiver DUMP, le renderer,
les manifestes applicatifs, les commandes et les anciens tests JVM sont byte-identiques.
Banner, Studio, Cloud, main et les PR existantes ne sont pas modifiés.

## Ce qui est enregistré

`choose()` prépare une trace primitive par requête : opération SELECT/OBSERVE/PAUSE/PLAY,
compteur `query` propre au port, `selectedAtMs` et `finishedAtMs` en elapsedRealtime ms.
Le callback ne fait qu'une copie en mémoire dans `NativeDiagnostics` ; aucun Log n'est
émis entre les deux lectures Binder. La trace est jointe aux événements E5 existants,
notamment NATIVE_ADMISSION_CHECK et CLEANUP. Il n'y a pas de nouveau propriétaire de transport.

Le même `attemptId` (sel éphémère du service + génération du contrôleur) relie REQUEST,
admission, requêtes et état terminal. Le couple génération/trace est copié atomiquement ;
REQUEST efface la trace retirée. Une trace d'une autre génération n'est jamais sérialisée.
Les refus antérieurs à l'entrée du contrôleur restent des événements explicites sans requête
choose ; ne pas les confondre avec un TARGET résultant d'une sélection native nulle.

| Champ `selection` | Interprétation |
|---|---|
| `code` | Sortie exacte du sélecteur, indépendante de `reason` du transport |
| `operation`, `query`, horodatages | Motif de la requête et instant réel de sélection |
| `foreground`, `foregroundWindowMs` | Résultat du premier plan et fenêtre inchangée de 30000 ms |
| `eventsExamined`, `relevantEvents`, `resumedApps` | Compteurs du réducteur, sans noms d'applications |
| `lastRelevantEventAgeMs` | Âge UsageEvents calculé uniquement avec les temps Unix de cette API |
| `sessionCount`, `visitedSessions`, `ignoredSessions`, `primeCandidates`, `competingSessions` | Liste annoncée et partie réellement parcourue avant le premier refus |
| `readControllerIndex`, `readWasPrime` | Contrôleur auquel appartiennent les deux lectures diagnostiquées |
| `read`, `readStep`, présences | Comparaison et progression de lecture, y compris exception partielle |
| `firstState/secondState`, positions, vitesses, mises à jour | Valeurs techniques des deux lectures du même contrôleur |
| `metadataDifference` | Première catégorie changée : ID, TITLE, SUBTITLE ou DURATION, jamais sa valeur |
| `stage`, `detailsComplete` | Étape de l'exception ou éventuelle indisponibilité des seuls détails diagnostiques |

Les compteurs concernent le parcours réellement effectué, pas un inventaire exhaustif
après une sortie anticipée. L'ancien compteur d'événements peut atteindre 1025 quand la
garde refuse au-delà de 1024 ; le seuil n'est pas augmenté. `readControllerIndex` peut
désigner un autre lecteur PAUSED inspecté après Prime : le snapshot de la cible admise,
quand il existe, demeure celui des champs E5 extérieurs à `selection`.
`-1`/NOT_EVALUATED indiquent une donnée non obtenue, jamais une preuve positive.

## Catalogue des sorties

| `code` | Détail qui permet de conclure |
|---|---|
| `VOD_NOT_ATTESTED` | Court-circuit avant toute interrogation du premier plan |
| `FOREGROUND_REFUSED` | Sous-code ACCESS_DENIED, MANAGER_UNAVAILABLE, EVENTS_UNAVAILABLE, NO_RELEVANT_EVENTS, EVENT_LIMIT, AMBIGUOUS ou PRIME_NOT_FOREGROUND |
| `FOREGROUND_EXCEPTION` | Exception du premier plan, propagée exactement comme avant ; sous-code SYSTEM_EXCEPTION |
| `NOTIFICATION_ACCESS_DENIED` / `NOTIFICATION_ACCESS_EXCEPTION` | Accès absent ou exception au contrôle notification |
| `SESSION_MANAGER_UNAVAILABLE` / `SESSION_LIST_UNAVAILABLE` | Manager absent ou liste native nulle |
| `NO_PRIME_SESSION` | Aucun Prime retenu ; count=0 distingue la liste vide |
| `MULTIPLE_PRIME_SESSIONS` | Deuxième contrôleur Prime cohérent rencontré |
| `COMPETING_SESSION` | Autre session pertinente selon le prédicat historique inchangé |
| `NATIVE_READ_REFUSED` | METADATA_MISSING, STATE_MISSING, METADATA_CHANGED, STATE_CHANGED, POSITION_CHANGED, UPDATE_TIME_CHANGED, SPEED_CHANGED ou ACTIONS_CHANGED |
| `UNEXPECTED_CONTROLLER_ENTRY` / `UNEXPECTED_PACKAGE` | Entrée nulle ou package nul, auparavant réduits à null par le catch natif |
| `SYSTEM_EXCEPTION` | Exception dans le catch natif historique ; `stage` et `readStep` situent l'appel |
| `SELECTED` | Un snapshot a été rendu ; les gardes du transport peuvent encore le refuser |

PENDING est uniquement l'état initial interne. COHERENT décrit les deux lectures ; il
ne signifie pas PLAYING, position fraîche ou admission autorisée. Une session NONE à
position ancienne peut légitimement produire SELECTED/COHERENT puis STALE_POSITION.
La raison finale TARGET, STALE_POSITION, PAUSE_DISPATCH_FAILED, etc. reste inchangée et
est affichée à côté du code de sélection. Aucun code diagnostique ne remplace cette raison.

## Preuve d'absence de changement fonctionnel

- Le prédicat natif de cohérence est conservé dans un booléen indépendant des enums de
  diagnostic. Aucun champ de trace n'est lu pour accorder un snapshot ou un transport.
- Les requêtes Android, leurs courts-circuits et la frontière historique du catch sont
  conservés. Les exceptions foreground/notification qui remontaient remontent encore ;
  les exceptions natives qui rendaient null rendent encore null.
- Les détails supplémentaires et le callback sont best effort : une faute RuntimeException
  du diagnostic n'accorde ni ne retire une autorisation. Aucune exception/message privé
  n'est exporté. Les erreurs fatales VM ne sont pas masquées.
- Les seuils native sample/provider (1000/20000 ms), temps de préparation/confirmation,
  ancre de pause immobile, contrôles EOF et politique de reprise sont inchangés.
- Aucun appel PLAY/PAUSE, aucune Activity, permission ou dépendance réseau n'est ajouté.

Le coût d'observation en mémoire et la sérialisation additionnelle restent un coût réel.
Les tests ne prétendent pas rendre identiques toutes les courses physiques d'un système
temps réel. Les requêtes Binder et commandes sont comparées dans l'ordre ; aucune journalisation
n'est intercalée entre les deux acquisitions natives et la fraîcheur réelle reste revalidée.

## Tests et provenance

Le [rejeu](../../.github/scripts/e5-selection-041-replay.py) compile les sources réelles
du commit E5-040 immuable puis les sources E5-041 avec les mêmes doubles d'API Android.
Les 57 scénarios comparent décisions, raisons, commandes et ordre des requêtes critiques.
Tous les codes de sortie (hors PENDING), sous-codes foreground, comparaisons et catégories
de métadonnées sont exécutés et vérifiés, ainsi que les lectures partielles et exceptions.
Un observateur défaillant et un faux code SELECTED n'autorisent aucune commande.
Le cycle PLAYING → PAUSED observé → EOF → PLAYING observé reste confirmé indépendamment.

Les six nouveaux tests `NativeSelectionDiagnostics041Test` vérifient la corrélation terminale,
l'absence de mélange de générations, le reset, la copie défensive, la confidentialité et
l'absence d'autorité des diagnostics. Les quatre nouveaux tests Python contrôlent la portée
réelle, les hashes, les docstrings/Javadocs conservées et l'absence de capacité native dans la trace.

Les anciens tests et leurs docstrings sont conservés. Les deux comparaisons historiques
E5-039 passent par une inversion E5-041 finie et hashée, contrôlée séparément ; les JVM,
sondes Android et tests différentiels utilisent toujours le véritable code candidat.
Le registre n'introduit aucun wildcard ni skip. Voir [qualification.md](qualification.md).

## Confidentialité et collecte

La trace ne retient aucun MediaController, token, titre, sous-titre, ID média brut ou nom
d'application tierce. Seuls codes, compteurs et valeurs numériques sont ajoutés. Les anciennes
empreintes E5 sont toujours salées par service. Le probe SceneVibeMedia n'est pas modifié ;
ses lignes de contenu ne font pas partie de l'export E5-041 proposé.

[Invoke-E5-041.ps1](Invoke-E5-041.ps1) capture tous les niveaux des seuls tags SceneVibeE5 et
SceneVibeE5Adb dans les buffers main/system, sans filtre sur un motif de refus. Il vérifie
la version, effectue un seul broadcast et conserve REQUEST jusqu'à CLEANUP. Il exclut un
ancien REQUEST déjà présent, refuse les tentatives ambiguës et n'invente aucun terminal.
Le [test hôte](../../.github/scripts/e5-selection-041-powershell-test.py) exécute le vrai script
avec faux ADB : formats threadtime/brief, ancien ID, refus, terminal incomplet, mauvaise
version, livraison refusée et IDs concurrents. Aucun appareil n'est contacté par ces tests.

## Livraison et limites

APK disponible : `SceneVibe_E5_041_diagnostic_stable.apk`, package `com.scenevibe.tvcompanionpoc`,
versionCode **17 >16**, versionName `0.8.2-tv-release-hardening-e5-native041-diagnostics`.
SHA-256 : `a7c5ea934b0e3ab909eece48cc9233d5dde786a05594afed908d1e2d7aec01f6`.
Certificat SHA-256 : `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
Signature API31, identité manifest/DEX, flags, fixture et égalité du payload avec l'APK ON
testée ont été vérifiés sur les bytes téléchargés. Le certificat est identique à celui de
l'APK E5-039 de référence, elle aussi téléchargée et vérifiée à nouveau.
[Artefact signé](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38693178040/artifacts/11952439950).
Le profil OFF reste versionCode 12 et E5 désactivé. La clé stable existante reste uniquement
sur le runner de signature, après les gates software et platform31 ; aucune nouvelle clé.

La [procédure Sony](Procedure_PowerShell_E5_041.md) est réservée au propriétaire. WORK ne
se connecte pas à la Sony, n'installe rien et ne déclenche aucune tentative physique.
Un SELECTED indique que la sélection n'a pas été refusée sur cette tentative ; il ne prouve
pas que les anciennes conditions ont été reproduites ou le défaut corrigé. Un refus doit
fournir son motif concret. Aucun READY FOR SONY FUNCTIONAL TEST ni PASS physique n'est revendiqué.

Aucune fusion, aucun déploiement, Vercel, Neon, Cloud ou Production. STOP après livraison
de l'APK diagnostique et du kit pour ta décision de collecte propriétaire.
