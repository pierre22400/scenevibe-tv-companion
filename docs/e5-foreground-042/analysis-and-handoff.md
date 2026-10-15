# E5-042 — reconstruction ciblée du premier plan

Base vérifiée : `bb13d7cf7a1448fc23f6b5e0aebb5c6a1a022ec2` ; PR #22 OPEN / DRAFT / non fusionnée.
Branche : `work/scenevibe-e5-foreground-042`, vers `work/scenevibe-e5-selection-diagnostics-041`.

**QUALIFICATION EN COURS — ne pas installer ni déclencher tant que le verdict n'est pas READY.**

## Cause et preuve avant/après

Constat propriétaire, Sony BRAVIA Android12, Prime/Columbo, APK17, tentative unique `47b361f2-1` :
FOREGROUND_REFUSED / NO_RELEVANT_EVENTS, fenêtre 30000, 1 événement examiné, 0 pertinent,
read NOT_EVALUATED, transport TARGET, terminal REFUSED. Aucune PAUSE.

`AndroidNativeSessionPort.chooseObserved` exige `foreground.primeForeground` avant toute lecture
média. E5-041 crée un `NativeForegroundProof` vide à chaque appel et ne demande que
`queryEvents(now-30000, now)`. RESUMED décrit une transition, pas un heartbeat. Après 30 secondes
sans transition le RESUMED disparaît de la requête, même si l'activité continue au premier plan.
Un événement non lié au lifecycle ne peut remplir cet état vide. La signature est reproduite
avec les vraies sources E5-041 : refus, zéro commande ; les nouvelles sources reconstruisent
l'état avec le même historique cohérent et les mêmes valeurs média valides.

## Correction et limites Android12

- Requête de l'historique effectivement retenu (`queryEvents(0, now)`), sans allonger un délai
  d'autorisation. Plafond conservateur 32768 événements : dépassement = refus, aucun cache positif.
- Un repère d'état vide est obligatoire : DEVICE_STARTUP 27, DEVICE_SHUTDOWN 26,
  SCREEN_NON_INTERACTIVE 16 ou KEYGUARD_SHOWN 17. Ces événements invalident la propriété
  antérieure ; SCREEN_INTERACTIVE 15 ou KEYGUARD_HIDDEN 18 ne la rétablissent pas.
- Un RESUMED explicite après ce repère ouvre une activité identifiée par package ET classe.
  PAUSED/STOPPED ferment cette activité exacte. Un STOP ancien du menu ne supprime pas le Player.
  Une seule activité reprise Prime est admise. Multi-resume, même dans Prime, est refusé.
- Identité d'activité absente, doublon RESUMED non distinguable avec l'API publique, sortie
  inconnue pendant une propriété, ordre temporel invalide ou repère absent = refus.
  Un nouveau repère peut réinitialiser une ambiguïté ancienne, jamais un ordre de requête invalide.
- Le user doit être déverrouillé, le display interactif et le keyguard absent avant/après la
  reconstruction ; permission vérifiée de nouveau à la fin. Les gestionnaires absents refusent.
- Reconstruction réexécutée à chaque SELECT/OBSERVE/PAUSE/PLAY, puis encore immédiatement avant
  le dispatch de PAUSE/PLAY, avec une nouvelle vérification de la fraîcheur existante. Aucun
  retour vers Prime ne remplace le token ou le programme initial de la lease.

L'API publique conserve seulement quelques jours d'événements et ne fournit ni indicateur
universel de complétude ni snapshot atomique du top Activity tiers. Ce correctif refuse les
lacunes détectables et les historiques sans repère ; il ne prétend pas détecter un événement
qu'un firmware aurait silencieusement omis. Une transition postérieure à la dernière lecture
reste une course interprocessus inhérente ; aucun seuil ni délai ne peut l'éliminer. L'ancienne
preuve présentait déjà cette limite. La revalidation supplémentaire réduit cette fenêtre.
L'historique n'est ni persisté, ni journalisé, ni transmis ; seuls des compteurs/codes existants
sont exposés. Le coût d'une longue rétention peut conduire à un refus conservateur ; aucune
augmentation des gardes de fraîcheur n'est permise pour masquer cette situation.

Solutions examinées : `queryUsageStats`/dernier usage et importance de processus ne prouvent
pas le premier plan actuel ; MediaSession peut survivre à HOME ; `getRunningTasks` ne donne
pas librement les tâches tierces ; le callback top-resumed appartient à l'activité concernée.
Aucun service Accessibility, accès caché à instanceId, privilège additionnel, root, interception
Prime ou permission nouvelle n'est introduit. Une API OEM de premier plan n'est pas supposée.

Sources publiques Android (consultées le 15/10/2026) :
- https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,long)
- https://developer.android.com/reference/android/app/usage/UsageEvents.Event
- https://developer.android.com/develop/ui/views/layout/support-multi-window-mode#multi-resume

## Périmètre protégé et qualification

Owner de transport, policy/seuils, snapshots/identité, renderer, EOF, endpoint DUMP et manifests
de production restent byte-identiques à E5-041. Seule la preuve de premier plan, sa revalidation,
les codes de refus et la version de la candidate changent. Profil OFF/release : version12,
flag OFF, pas de fixture/receiver. Candidate debug de qualification : version18, suffixe042,
Cloud vide, LAN OFF, signature stable exclusivement dans la CI existante.

14 nouveaux tests JVM purs ; replay sur les vraies sources avant/après, 30 scénarios après :
lecture prolongée, départ/switch/multi-resume, historique incomplet ou contradictoire, identité
manquante, managers indisponibles, état écran/verrou, session absente/concurrente/remplacée,
métadonnées absentes/incohérentes, position invalide ou périmée, VOD absente, départ avant PAUSE,
départ durant interlude, EOF anormal, cycle complet confirmé par des observations indépendantes.
Les 138 tests Python protègent les anciens propriétaires, docstrings et inverses exacts.
Les anciens tests JVM sont exécutés sur les sources actuelles, aucun nouveau skip.
Les couches de provenance ne servent qu'aux comparaisons historiques de bytes ; elles ne
substituent aucune décision runtime. Le replay observationnel041 n'est pas présenté comme
équivalent au042 : la décision de premier plan est intentionnellement corrigée.

Android12 émulé : qualification des receivers/lectures natives existantes, puis Settings comme
activité témoin réelle stable >30s et sortie HOME. Le test seul traduit son package en cible
pour le reducer ; l'adapter Android réel refuse toujours Settings. Cela ne qualifie ni Prime
ni Sony. Le renderer/MediaPlayer réel reste couvert par la qualification E5 historique ; la
présente CI ajoute les transitions de premier plan et conserve les gardes d'endpoint.

Aucun accès Sony, aucune fusion, aucun déploiement, aucune mutation Cloud/Vercel/Neon/Production.
Les résultats exécutés, les IDs CI, les bytes APK et la procédure unique sont complétés après CI.
