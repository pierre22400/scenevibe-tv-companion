# TV-UX 002-B / AD-SYNC 001-C — v22

## Périmètre et verdict

Cette évolution reste dans la PR brouillon #31 et le Companion existant. Aucune installation Sony, fusion, opération Neon/R2, modification de permission ou déploiement. La qualification physique CLOUD → ANDROID TV 002 reste acquise ; elle ne qualifie pas les nouvelles interactions v22. Le résultat de cette mission reste **PARTIAL pour l’accès avec la télécommande Sony seule** : aucune API publique démontrée ne permet au badge passif de recevoir `000`, et la prise de focus peut affecter un lecteur sensible. Une commande explicite de notification et un véritable contrôle local par téléphone sont livrés comme alternatives distinctes. Un panneau passif n’est pas présenté comme une interface D-pad fonctionnelle.

## Journal Sony v21 intégral

Original analysé : `scenevibe-v21-diagnostic(1).log`, 995192 octets, UTF-16LE BOM, 4022 lignes, SHA-256 `6b89d011a18c31a9180837e51986e3a04543cccda0a67ae7c9fc41b3be233c5d`. Il reste dans les pièces privées de Pierre ; le dépôt ne reçoit ni logcat tiers ni identifiants. L’analyse porte sur tout le fichier, pas un extrait de cent lignes.

- 203 observations AD-SYNC `IDENTITY_OR_CLOCK_INVALID`, 68 `SESSION_UNAVAILABLE`, une transition OFF ; `adSignal=UNAVAILABLE` ne prouve aucune publicité. Beaucoup d’échantillons ont une position brute 0, sélectionnée -1 et un état PAUSED : leurs données sont effectivement inutilisables/incompatibles. On ne peut pas déclarer tous les refus injustifiés.
- Deux `CACHE_OR_CONSENT_INVALID`, à 23:01:34.375 et 23:03:54.880. Le second suit `MainActivity.onResume` de 255 ms. Le code v21 liait un permis déjà accordé à l’état d’interface ASSOCIATED : la vérification normale du compte passe par CONNECTING et pouvait le révoquer. Le log ne sépare pas l’ancien garde-fou exact ; la cause des deux incidents historiques ne peut donc pas être affirmée individuellement.
- OFF à 23:11:42.115 pendant l’absence de session ; lancement normal à 23:11:45.057, identité refusée à 45.268, horloge inutilisable à 49.976, `MEDIA_PLAYING` à 53.877, panneau passif à 53.915, `TEXT_RENDER_REQUESTED` à 55.492 / 306704 ms. Cela corrobore une restitution après OFF, **pas** un A/B contrôlé du même flux valide.
- Aucun événement de touche ni focus interactif confirmé. Le crash `com.android.statementservice` présent ailleurs dans le fichier n’est pas un crash SceneVibe.

## Focus et faisabilité de 000

`configureSize` maintient le badge et le panneau v21 sous `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`, avec descendants non focalisables. Ils n’obtiennent donc ni D-pad ni touches numériques. `FLAG_NOT_TOUCH_MODAL` concerne le passage des pointeurs hors fenêtre ; il ne duplique pas les touches d’une autre fenêtre. `FLAG_ALT_FOCUSABLE_IM` concerne le clavier logiciel, pas le routage de la télécommande. `FLAG_LOCAL_FOCUS_MODE` repose sur de l’injection locale : il n’est pas utilisé. Android ne possède qu’une fenêtre focalisée par écran pour les touches.

Le passage v20 `activatePrepared` retirait ces protections et demandait le focus automatiquement. La perte de focus est un mécanisme plausible pour un lecteur sensible ; aucun journal interne de Prime ne démontre pourquoi celui-ci est revenu à la fiche. Le banc possède donc deux lecteurs **SceneVibe de test** : l’un conserve sa lecture, l’autre quitte volontairement sa lecture à la perte de focus. Cette seconde simulation caractérise un risque, sans attribuer le comportement réel de Prime à une cause certaine.

Le test natif `numericZerosBelongToTheViewerNotThePassivePanel` mesure les événements réellement dispatchés : séquence rapide `KEYCODE_0`, séquence lente/incomplète et `KEYCODE_NUMPAD_0`. Ils appartiennent à l’Activity de lecture focalisée, pas au panneau passif. Aucun compteur `000` trompeur n’est ajouté au produit. Les codes effectivement émis par la télécommande physique Sony ne sont toujours pas observés. Ne pas recommander `000` dans Prime : ses touches iraient au lecteur.

| Accès | Implémentation | Concession / preuve restante |
| --- | --- | --- |
| Télécommande seule depuis Prime, `000` | Non disponible en overlay passif | Pas de capture globale, accessibilité détournée ni privilège caché |
| Notification système « Ouvrir le panneau » | PendingIntent immuable vers le service existant, pas MainActivity | La présence d’une entrée Sony accessible et le maintien de l’écran Prime restent à vérifier physiquement |
| Prise de focus explicite | États natifs demandés puis confirmés par callback | Peut interrompre un lecteur sensible ; jamais automatique par défaut |
| Téléphone sur réseau privé | Page embarquée, mêmes commandes/service, panneau restant passif | Ne remplace pas le D-pad Sony ; nécessite téléphone + réseau privé de confiance |

Les états `PASSIVE`, `INTERACTIVE_REQUESTED`, `INTERACTIVE_CONFIRMED`, `RELEASING`, `CLOSED` sont distincts. Un appel WindowManager réussi n’est pas un focus confirmé. Back et × ne ferment que l’interface ; les touches de transport sont consommées sans appel à un lecteur tiers. La libération est observée séparément de la demande. Une perte de focus remet le panneau en état passif, sans reprendre de force le focus.

Sources Android officielles consultées : [drapeaux WindowManager](https://developer.android.com/reference/android/view/WindowManager.LayoutParams), [cible unique des touches](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs#no-focused-window), [NotificationCompat.TvExtender](https://developer.android.com/reference/androidx/core/app/NotificationCompat.TvExtender). Le Companion utilise AndroidX 1.13.1 pour Android 12, et non la nouvelle classe plateforme API 35.

## Alternative téléphone réellement commandable

Dans Développeur, activer le mode puis « Activer les commandes locales (téléphone) ». L’écran TV affiche l’URL privée et un code à huit chiffres. Ouvrir cette URL dans le navigateur du téléphone et saisir le code avant de retourner au film. Aucun ordinateur/ADB n’est nécessaire à cet usage. La page offre TEXT/AUDIO/AD-SYNC, suspension, reprise, recalage sans réarmement, affichage et fermeture du moniteur.

L’opt-in ouvre pendant **dix minutes absolues** un port HTTP privé éphémère dans OverlayService. Le code est à usage unique, cinq essais au maximum ; le bearer aléatoire reste seulement en mémoire de la page. Aucun jeton Cloud, identité, contenu de commentaire ou URL de ressource n’est envoyé. Le contrôle se révoque à l’expiration, à sa désactivation, à l’arrêt du mode ou du service. Il ne redémarre pas après arrêt du processus. Une page rechargée exige une nouvelle activation explicite sur la TV.

HTTP local est en clair : cette variante de développement requiert un LAN privé de confiance. Elle n’est ni un accès Internet ni une API distante productive. Host/Origin exacts, en-tête same-origin, absence de CORS, CSP, tailles/temps/concurrence bornés, authentification et identifiants de commande non rejouables protègent cette surface ; ils ne chiffrent pas le LAN. Aucune nouvelle permission Android n’est ajoutée, la politique HTTPS Cloud demeure inchangée. Pas de commande PLAY/PAUSE/SEEK Prime, upload, suppression, activation de track ou téléchargement sur cette interface.

## Politique AD-SYNC v22

Le nouveau chemin est limité au build `tvux002` par `ADSYNC_NONDESTRUCTIVE`. Les profils historiques gardent leur politique qualifiée. La préférence ON/OFF est persistée sans être changée par une incertitude.

| Faits observés | Comportement |
| --- | --- |
| Identité et horloge MEDIA compatibles | Restitution normale ; publicité explicitement non observable |
| Métadonnées absentes, identité différente, horloge invalide ou capture périmée | Retenue des effets, cache/track/événements futurs conservés |
| Retour de deux captures distinctes, récentes et temporellement cohérentes | Récupération automatique ; aucune réinitialisation de l’horloge ni nouvelle admission |
| Rupture/session remplacée | Confirmation par deux observations compatibles, jamais « publicité détectée » |
| Ancre ancienne, capture actuelle et extrapolation déjà validée | Ancienneté de l’ancre seule non bloquante |
| Pause normale | Calendrier et lecteur local gelés selon le mécanisme existant |
| Suspension manuelle | Maintenue jusqu’à Reprendre/Recaler explicite, même OFF puis ON |
| Consentement expiré/révision/identité d’association/récépissé révoqués | Session fermée, fichiers conservés, nouvelle vérification et confirmation requises |

Le calendrier unique, les fenêtres originales et les identités consommées restent inchangés. La récupération ne réarme pas les MP3. Le recalage local n’envoie pas SEEK à Prime et le contrôle téléphone ne propose aucun réarmement. Ce prototype est **consultatif avec suspension publicitaire manuelle**, sans détecteur publicitaire fiable. Aucun AUDIO nouveau n’a été généré.

## Cache, consentement et cycle de vie

La restauration automatique relit uniquement la génération chiffrée connue et le cache privé, une fois par époque UI, sur le worker existant. Elle vérifie manifeste/ressources/MP3 avec les composants Android existants. Elle ne télécharge rien et n’active aucune restitution.

Le permis en mémoire conserve son échéance initiale et ses coordonnées exactes : génération/appareil, révision, binding, reçu, époque globale de révocation. Le passage UI CONNECTING/ERROR ou sa destruction ne constitue plus à lui seul une révocation. Les vrais 401/403 et changements de ces coordonnées continuent de fermer l’autorité. L’ancienne logique de MainActivity qui arrêtait le service sur une simple présentation ERROR est également exclue de v22. Aucun consentement ne survit artificiellement à son expiration ou à la mort du processus.

L’UI distingue cache vérifié, service actif/arrêté et confirmation nécessaire. Les préférences TEXT/AUDIO/AD-SYNC survivent au processus, pas le permis de lecture, le mode Développeur ou le contrôle local. Les interrupteurs restent disponibles comme préférences même sans service ; cela ne prétend pas rendre une fenêtre passive accessible au D-pad. ON clair/rouge, OFF blanc/neutre, focus noir et actions indisponibles sont conservés.

## Observabilité

Les diagnostics distinguent les transitions de récupération, `USER_SUSPENDED`, `MEDIA_IDENTITY_INVALID`, `MEDIA_CLOCK_INVALID`, `MEDIA_UNAVAILABLE`, les refus précis de consentement/cache, `CACHE_RESTORED`, `FOCUS_REQUESTED`, `FOCUS_GRANTED`, `FOCUS_RELEASED`, et le refus/perte de focus. Pas de répétition AD-SYNC à 250 ms, ni alternance artificielle SESSION_UNAVAILABLE/CAPTURE_TIMEOUT à chaque tick. Les coordonnées temporelles numériques sont des observations, jamais du contenu Prime.

Pour un diagnostic futur autorisé, utiliser [le script PowerShell](../scripts/Collect-SceneVibeDiagnostic.ps1). Il lit les buffers SceneVibe existants puis suit les nouveaux événements sans les vider. Démarrer avant l’action, laisser tourner après la reprise, arrêter avec Ctrl+C. Ne pas installer/désinstaller, force-stop, effacer le cache ou injecter des touches pour cette capture.

## Qualification et protocole physique

Les résultats définitifs et liens Actions sont ajoutés après exécution. La suite couvre moteur pur, widgets Robolectric, Android TV API31, vrais callbacks focus/touches, commandes TCP, cache/MediaCodec, service de restitution et reprise dans un PID différent. Les réponses fournisseur du nouveau banc sont simulées ; aucune nouvelle preuve Neon/R2 n’est revendiquée.

Après autorisation distincte d’installation uniquement :

1. Conserver les données v21, installer le candidat signé M5 par mise à jour. Ouvrir SceneVibe : vérifier Columbo depuis le cache, sans téléchargement ; confirmer explicitement l’admission si le service est arrêté.
2. Activer AD-SYNC et le mode Développeur. Pour l’alternative locale, associer le téléphone avant le film ; ouvrir Prime normalement. Vérifier que les actions téléphone TEXT/AUDIO, Suspendre/Reprendre, Recaler, montrer/fermer le moniteur ne changent pas l’écran ni le transport Prime. Ce résultat reste une UX téléphone.
3. Si la Sony expose la notification SceneVibe pendant la lecture, sélectionner volontairement « Ouvrir le panneau ». Exiger `FOCUS_GRANTED`, D-pad opérationnel, aucun retour à la fiche, puis Back/× et touches rendues à Prime. Si le système quitte le film ou n’offre aucune entrée, consigner le critère télécommande comme non satisfait ; ne pas insister avec une prise de focus automatique.
4. Provoquer seulement un aller-retour normal entre applications. Avec MEDIA compatible revenu et AD-SYNC ON, vérifier récupération automatique, préférences conservées, cache sans retéléchargement et aucun ancien MP3 rejoué. Vérifier séparément qu’une suspension manuelle attend Reprendre.
5. Lancer le diagnostic avant/après ces actions et noter les faits visibles/audibles. La publicité Prime reste non observable et la détection automatique n’est pas déclarée qualifiée.

**STOP avant installation Sony. Aucun PASS physique v22 n’est prononcé par Work.**
