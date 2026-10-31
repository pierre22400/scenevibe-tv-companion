# SceneVibe Accessibility Probe 003 — diagnostic privé

Projet Android autonome, autorisé par Pierre le 30 octobre 2026, développé depuis
`90b55c1c9dcb6894a8285c6a1dbe830a584bba8c`. Le statut « non autorisé » du mandat de
conception a été levé pour le développement, les tests, le push et une PR Draft.
Il ne l'est pas pour la Sony.

## Limite de la livraison

Package `com.scenevibe.accessibilityprobe003`, versionCode 1, versionName
`0.0.3-private-diagnostic`, minSdk 31, target/compileSdk 35. Le build est indépendant
du Companion : exécuter Gradle avec `-p diagnostics/accessibility-probe003`.
L'APK privée `debug` est **debuggable**, signée avec une clé Android de diagnostic
du banc, sans certificat M5 ni secret de production. Ce n'est ni une mise à jour
de SceneVibe v27.1 ni une version publique. La clé du runner n'est pas livrée et
une compatibilité de mise à jour avec un prochain build n'est pas garantie.

Le logiciel n'émet aucune frontière AD_START/AD_END. Les catégories
AD_LABEL_CANDIDATE/COUNTDOWN_CANDIDATE sont des projections lexicales sans
autorité. Un mot « Publicité » dans un menu peut produire le même candidat.
L'exposition sémantique de Netflix/Disney+ reste UNKNOWN.

## Composants et cycle de vie

- `ProbeActivity` : préparation/résultats volontaires, consentement expliqué,
  profil unique, contrôle synthétique, arrêt, export local et suppression.
- `ProbeService` : callbacks d'accessibilité existants, sources et racines ;
  aucun lecteur, réseau, capture, geste, touche, focus demandé ou action de nœud.
- `ProbeController` : état volatil, garde de package, admission temporelle et
  plafond de fréquence ; aucune connaissance de commentaires ou de MediaSession.
- `JournalStore` : lignes JSONL scalaires, bornage, clôture, export byte-identique
  et suppression limitée aux fichiers de cette application.
- `NodeSurvey` / `TextProjection` : parcours borné et réduction des textes/IDs.

Android doit lier le service après activation manuelle. La connexion est toujours
désarmée. Armer fige un package : OWN_CONTROL, Netflix ou Disney+. OFF ferme le
journal, annule les tâches, efface les références éphémères et demande un masque
d'événements nul. Reconnexion/processus neuf = désarmé. Le réglage Android peut
rester activé, mais il ne restaure pas une collecte.

Les dix minutes sont vérifiées en elapsedRealtime avant toute admission/lecture,
avec un unique timer. Le timer Handler ne réveille pas un appareil en sommeil
profond : après réveil, aucune lecture n'est admise au-delà du délai ; la clôture
est constatée au prochain callback/retour UI. Pas de polling ou de permission
d'alarme supplémentaire. La mort du processus peut laisser un journal sans
SESSION_END : il est incomplet, jamais artificiellement terminé.

## Configuration, budgets et données

Capacité statique `canRetrieveWindowContent=true`, BIND_ACCESSIBILITY_SERVICE
protégeant le service exporté, `isAccessibilityTool=false`. Zéro uses-permission.
Backup et transfert de données sont exclus. À l'armement : TYPES_ALL_MASK,
FEEDBACK_GENERIC, notificationTimeout 0 et les trois flags documentés (IDs,
vues non importantes, fenêtres interactives). Un seul package est publié dans
packageNames. Ces flags n'obligent pas Netflix à fournir des nœuds Gibbon.

Bornes : 600 000 ms, **2 000 000 octets** par journal (2 Mo décimaux), profondeur
12, **200 lectures de nœuds par exploration**, huit fenêtres suivies et au plus
une exploration par fenêtre par 250 ms. Une source, la racine active et les
racines interactives partagent le budget. Des doublons peuvent être comptés :
les comptes ne sont pas un inventaire de nœuds uniques.

Les callbacks sont projetés immédiatement. Les explorations rapprochées sont
coalescées vers une demande différée qui ne garde que l'ID numérique de fenêtre ;
l'ancienne source événementielle est alors indisponible et signalée. Une fenêtre
non admise, un événement ancien (>5 s d'uptime), une source nulle, un refresh
échoué, une limite ou une exception ne constituent jamais une preuve d'absence
de signal. Le débit peut atteindre les 2 Mo avant dix minutes : arrêt explicite.
Le champ droppedEventCount porte seulement les pertes connues de ce code ; la
complétude de livraison des événements par Android reste inconnue.

Chaque ligne contient schemaVersion, UUID de campagne, séquence, profil/package
compilé, provenance OWN_CONTROL/TARGET_PLATFORM, type, uptime/elapsedRealtime et
heure murale de réception. Les événements portent eventTimeUptimeMs, état de
fraîcheur, types de changement, IDs numériques de fenêtres, catégories et comptes.
Comparer les uptime entre eux ; ne pas soustraire eventTime d'elapsedRealtime.
Un contrôle positif synthétique ne prouve pas l'état d'un lecteur officiel.

Les textes/descriptions restent transitoires, limités à 160 caractères par
élément ; seul un vocabulaire fermé FR/EN et un motif de compteur sont classés.
Les champs password ne sont pas lus. Aucun texte de titre/compte, extras, jeton,
URL privée ou hash de contenu n'est conservé. Les classes/IDs inconnus deviennent
des références numériques éphémères (256 maximum, effacées aux frontières).
Seuls les quatre IDs publics Netflix fournis par Pierre peuvent être nommés par
catégorie ; ils ne sont pas supposés être publicitaires.

Journaux privés dans `files/probe003/`. Export arrêté seulement, après action
explicite, vers les fichiers externes propres de la sonde : `files/Documents/`.
Pas de sélecteur susceptible de choisir un fournisseur Cloud. La copie est
vérifiée par SHA-256, l'original est conservé ; conflit = refus d'écrasement.
Supprimer retire seulement les noms de campagne reconnus, internes et exportés
dans cette application. Les originaux internes âgés de plus de 24 h sont purgés
quand l'application s'exécute ; aucune purge à heure exacte n'est promise après
son arrêt. Pierre reste responsable des copies exportées hors de la sonde.

## Reproduction logicielle

Java 17, Gradle 8.9, AGP 8.7.3, SDK 35/build-tools 35.0.0. Pas de wrapper ni de
dépendance copiée. Le workflow dédié installe ces outils sur son banc jetable.

```bash
bash diagnostics/accessibility-probe003/tests/core-checks.sh
python3 -B -m unittest discover -s diagnostics/accessibility-probe003/tests -p 'test_*.py' -v
gradle -p diagnostics/accessibility-probe003 --no-daemon --max-workers=2 \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

`core-checks.sh` exécute les scénarios pure Java indépendants, sans Android.
`test_boundaries.py` vérifie les autorités, la documentation et le diff protégé.
Les tests Android s'exécutent sur un émulateur TV API31 possédé, avec UiAutomation
configuré pour ne pas supprimer notre propre service. Le script CI refuse tout
appareil non qemu et utilise uniquement emulator-5554. Ses commandes d'activation
automatique sont exclusivement celles du banc ; ne pas l'utiliser sur la Sony.

La référence du Companion doit rester identique octet par octet : nouveaux
fichiers ici et nouveau workflow seulement. Les tests historiques de #35 ne
sont pas des résultats de cette livraison et ne sont pas recopiés comme PASS.

## Preuves et prochaine étape

Le rapport livré avec l'APK donne le commit, les résultats réellement exécutés,
la signature et les limites. PASS SOFTWARE ne vaut ni PASS INSTRUMENTAL physique,
ni PASS AD DETECTION. Les démarches Play/conditions des plateformes restent
distinctes d'un diagnostic ADB privé ; aucune publication n'est autorisée.

Références documentaires : [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService),
[AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo),
rapport de conception de Pierre du 30 octobre 2026. Ces APIs ne garantissent pas
qu'une application cible publie un signal publicitaire.

Voir [PROTOCOLE-SONY.md](PROTOCOLE-SONY.md), **à exécuter seulement après un nouveau
GO physique explicite**. STOP actuel : pas de Sony, installation, activation,
campagne, fusion, publication ou mutation Cloud ; Companion et Prime inchangés.
