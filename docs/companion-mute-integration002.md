# COMPANION MUTE INTEGRATION 002 — intégration du raccourci Android

Décision propriétaire du 10 novembre 2026 et amendement 001 : le paramétrage initial Android TV, y compris une nouvelle affectation MUTE au package principal, est explicitement autorisé et ne bloque pas la livraison. Aucune configuration physique Sony n'est réalisée dans ce mandat.

## Base et périmètre

La branche est créée directement depuis #47, commit `3a24849603f0e88f60946b870cb7b30b327da26c`. #48 reste Draft et n'est pas un ancêtre de cette branche. Ses seules parties éventuellement réutilisées concernent la projection temporelle et les widgets de recalage, après vérification. Son ouverture par notification ou retour dans SceneVibe ne constitue pas le déclencheur de cette livraison.

## Composant et affectation système

| Rôle | Package d'application | Classe du service |
| --- | --- | --- |
| Source expérimentale #44 | `com.scenevibe.tvcompanionpoc.netflixoverlay001` | `com.scenevibe.tvcompanionpoc.NetflixShortcutService` |
| Nouveau Companion principal | `com.scenevibe.tvcompanionpoc` | `com.scenevibe.tvcompanionpoc.CompanionShortcutService` |

Un ComponentName Android désigne un package et une classe. Le composant expérimental complet est donc `com.scenevibe.tvcompanionpoc.netflixoverlay001/com.scenevibe.tvcompanionpoc.NetflixShortcutService` ; le nouveau composant est `com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.CompanionShortcutService`. Ces identités sont différentes. Le réglage visant l'ancien composant ne devient pas un réglage du nouveau par simple mise à jour du Companion principal. Le prototype expérimental n'est pas modifié pour servir de relais et aucune deuxième application n'est requise.

La configuration exacte actuellement enregistrée sur la Sony n'a pas été lue. Si elle vise le composant expérimental décrit par #44, il faut sélectionner le nouveau service principal et lui affecter le raccourci dans Android TV. Cette manipulation minimale est acceptable selon l'amendement, à réaliser seulement après GO physique distinct. Aucune persistance future n'est garantie sans essai ; le nom du nouveau service est volontairement indépendant du numéro de version.

## Chemin de déclenchement P0

Raccourci MUTE attribué par Android → `AccessibilityButtonController.AccessibilityButtonCallback.onClicked` du service principal → contrôle de la connexion courante → `TvFirst009TextServiceOwner.openFromAccessibilityShortcut` → vérification de la session/cache déjà admis et des captures MEDIA fraîches → `CompanionControlPanel` / `WindowManager` du service principal.

Le service est protégé par `android.permission.BIND_ACCESSIBILITY_SERVICE`. Il demande uniquement `flagRequestAccessibilityButton`, zéro type d'événement, aucun contenu de fenêtre, aucune interception de touche et aucun geste. La connexion ne lance pas de Track. Sans service de restitution, horloge exploitable ou admission courante, le callback est refusé sans créer une nouvelle session ni demander de permission.

Les marqueurs `SceneVibeMute002` distinguent connexion, callback reçu, demande de fenêtre acceptée, doublon et refus. Les journaux de fenêtre existants distinguent attachement, focus réellement reçu et libération. Les logs ne contiennent pas les touches ordinaires, les fenêtres des plateformes, les credentials ou le contenu éditorial.

## Nature des tests

Les tests Robolectric injectent explicitement un callback synthétique pour les erreurs de cycle de vie ; ils ne prouvent pas une réception MUTE.

Le banc Android TV configure le vrai composant sur l'émulateur, conserve les autres services d'accessibilité avec `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`, puis injecte dans la politique d’entrée Android la combinaison système standard Android TV **BACK + BAS maintenus**. Le callback n’est pas injecté : il doit être délivré par le framework après reconnaissance de cette combinaison. L’action globale 13, non disponible dans l’image Android TV du banc, n’est pas présentée comme un PASS. Le callback doit ensuite parvenir via le framework à l'instance réellement connectée. Aucun `panelIntent(...).send()` ni appel direct à `onClicked` n'est utilisé dans ce scénario natif.

Cette combinaison de test sur émulateur qualifie le raccordement Android. Elle ne remplace pas MUTE dans le parcours Sony demandé. Elle ne prouve pas que le firmware Sony transforme l'appui MUTE de la télécommande en ce raccourci.

Références primaires : [AccessibilityButtonController](https://developer.android.com/reference/android/accessibilityservice/AccessibilityButtonController), [actions globales AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [UiAutomation](https://developer.android.com/reference/android/app/UiAutomation), [Configurator](https://developer.android.com/reference/androidx/test/uiautomator/Configurator), [politique d’entrée Android TV 12](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.0.0_r1/services/core/java/com/android/server/policy/PhoneWindowManager.java).

## Protocole Sony — uniquement après GO physique

1. Vérifier le Companion principal v31 et conserver son association et son cache privé. Installer le nouvel APK principal v32 signé M5 sans désinstallation ni réinitialisation, après GO propriétaire.
2. Dans les réglages Android TV d'accessibilité, sélectionner **SceneVibe — raccourci du Companion**. Si Android l'exige, activer ce service et remplacer l'affectation expérimentale par ce nouveau composant dans le raccourci système associé à MUTE. Les intitulés précis dépendent du firmware Sony. Ne pas changer les réglages de superposition ou MEDIA déjà acquis sans cause constatée.
3. Activer normalement la Track privée ou publique par le parcours existant, puis ouvrir Prime Video et l'œuvre compatible. Cette activation de Track ne configure pas le raccourci.
4. Depuis Prime, utiliser le raccourci MUTE tel que configuré, sans quitter le film, ouvrir SceneVibe, chercher une notification ou valider sur un autre appareil.
5. Vérifier séparément : callback diagnostiqué, panneau affiché, focus obtenu et réception du D-pad. Choisir reprise ou maintien en pause, ajuster le temps et confirmer ; vérifier TEXT visibles et AUDIO audibles au nouveau temps.
6. Tester BACK et une seconde ouverture MUTE. Vérifier annulation sans recalage partiel, absence de double fenêtre et retour normal du focus et des commandes à Prime. Recompter les 3 TEXT + 3 AUDIO privés de référence et tester le parcours public.
7. Relever explicitement tout effet réel sur la lecture native. Aucun PLAY, PAUSE ou SEEK du lecteur tiers n'est envoyé par SceneVibe. Ne pas confondre cette absence de commande avec une preuve physique de maintien de lecture malgré le focus.

Nouvelle TV : installation normale du Companion, autorisations existantes nécessaires à sa restitution, activation de ce service et affectation du raccourci Android une première fois. Aucun assistant de configuration supplémentaire n'est développé ici. Le maintien de cette affectation lors de futures mises à jour sera qualifié séparément.

**SONY MUTE : NOT EXECUTED — qualification physique requise.** STOP avant installation Sony, réglages physiques, fusion, Cloud, Neon/R2, Production ou Google Play.
