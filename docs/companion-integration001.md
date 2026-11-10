# COMPANION INTEGRATION 001 — candidat principal v32

Décision propriétaire du 10 novembre 2026, amendement 001 prioritaire. PR Draft empilée sur #47. Aucun verdict READY FOR SONY avant validation de la chaîne complète.

## Intégration ciblée

Base exacte : `3a24849603f0e88f60946b870cb7b30b327da26c` (#47), contenant les parcours privé v31 et autonome v33. Source des widgets : `07fda5229611d412e849cc82ca8cd8594d16955f` (#44), `CompanionControlPanel.ManualEdit`, présentation CURRENT/NEW et traitement D-pad/OK long. Le code de la variante Netflix, son service, son horloge, son accessibilité et son certificat ne sont pas importés.

Le principal conserve son package `com.scenevibe.tvcompanionpoc`, son `OverlayService`, son `WindowManager`, son `AdSyncController`, son calendrier M5, son lecteur MP3 et ses deux caches. La correction est un décalage de la projection MEDIA réellement transmise à ce calendrier. Les timecodes éditoriaux restent immuables. Un candidat d'édition ne suspend, ne recale ni ne reprend rien avant validation. Annulation, perte de focus et génération périmée ne l'appliquent pas.

Les deux commandes choisies avant l'édition sont **Recaler et reprendre** et **Recaler sans reprise**. La seconde fixe la position SceneVibe et maintient une suspension volontaire. Une reprise ultérieure ancre cette position sur deux captures MEDIA fraîches. Une demande locale de reprise ne modifie jamais l'état du film : une session MEDIA en pause reste en pause.

Les contrôles MEDIA, les permissions existantes, la durée vérifiée de l'œuvre et la limite historique d'écart de ±1 heure sont conservés. Identité incompatible, session changée, autorité expirée, publicité protégée, observation absente ou périmée refusent l'édition. Un véritable retour arrière utilise le réarmement existant ; une correction vers l'avant conserve les identités consommées. Les AUDIO actifs sont réconciliés par le calendrier et le port natif existants.

## Ouverture sans nouvelle autorisation

Deux actions natives réutilisent les capacités actuelles :

1. La notification existante du Companion contient son `PendingIntent` de service immuable. Quand une Track est active, cette action ouvre le panneau principal sans activer le mode Développeur. La visibilité et l'accès à cette notification par la télécommande Sony ne sont pas prouvés par l'envoi instrumenté de ce `PendingIntent`.
2. Le bouton **Recaler pendant le film**, dans le Companion existant, lance normalement Prime via son entrée publique déjà utilisée, puis demande le panneau après `MainActivity.onStop`. Cette action à la télécommande ne nécessite aucun réglage ni service supplémentaire. Elle ne commande ni PLAY, ni PAUSE, ni SEEK à Prime. Son effet réel sur la lecture Prime et le focus doit être confirmé sur Sony.

Aucun manifeste applicatif, permission, service d'accessibilité, onboarding, association ou consentement supplémentaire n'est ajouté. Aucun raccourci MUTE global n'est revendiqué. La comparaison des manifestes compilés et le scénario signé v31 → v32 font partie de la CI. Les commandes shell d'attribution des permissions n'existent que pour l'état initial de l'émulateur v31 ; aucune n'est exécutée après la mise à jour signée.

## Construction et qualification

Variante : `tvux002`, propriétés `scenevibeRealTrack001=true`, `scenevibePublicAutonomous001b=true`, `scenevibeAutonomousHosted001d=true`, `scenevibeCompanionIntegration001=true`.

VersionCode : **32** ; VersionName : `0.8.2-tv-release-hardening-companion-integration001-v32`. Origine : `https://scenevibe-autonomous001d-qual-preview.vercel.app`.

La chaîne Actions existante est étendue à la seule branche d'intégration. Elle utilise exclusivement les secrets M5 historiques, sans extraire ou publier la clé privée. L'APK livré reste non débogable. Le runner de test, signé par cette même chaîne, est réservé à l'émulateur ; il ne constitue pas une seconde application à installer sur Sony.

Référence figée d'upgrade : APK v31 physique de l'artefact `13617051785`, run `42480014146`, source `1d642e70d1559e6d753476945dcb749036dc4266`, SHA-256 `98ac6b74f71f02cbb52bd09998565012c864ec61aeb0a094d610de614283710f`. La CI vérifie son empreinte avant utilisation. Le candidat est signé M5, certificat SHA-256 `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

Le scénario d'upgrade installe cet APK v31, conserve une association chiffrée synthétique et un cache privé réel 3 TEXT + 3 AUDIO, puis applique `install -r` au candidat signé v32. Il relit le cache, les MP3, les préférences et le credential après update, et compare les réglages système sans les modifier après update. Le banc natif privé exerce aussi le panneau réel, les touches et l'absence de commande vidéo. Le banc public HTTPS vérifie le code unique, le cache complet, les six restitutions et un recalage à l'intérieur d'un MP3.

Les suites historiques ne sont ni supprimées ni affaiblies. Résultats et limites de chaque exécution sont consignés dans le rapport de réception. Tout cas absent ou non exécuté reste NOT EXECUTED. Une MediaSession du banc ne prouve pas Prime Video.

## Protocole Sony — à exécuter uniquement après GO propriétaire

1. Vérifier que le Companion principal est toujours en v31 ; conserver l'association et le cache privés de référence. Ne rien désinstaller, réinitialiser ou réassocier.
2. Installer uniquement l'APK principal v32 signé fourni, après contrôle de son SHA-256. Toute demande de nouvelle permission ou de nouveau réglage vaut **échec** : ne pas la valider.
3. Ouvrir le Companion : vérifier l'association et la Track privée déjà en cache. Vérifier ensuite le téléchargement normal d'une Track publique par le parcours existant.
4. Ouvrir Prime Video et l'épisode de référence. Tester l'action de la notification si elle est accessible. Tester aussi, depuis le Companion, **Recaler pendant le film** : retour normal à Prime, puis panneau. Aucun mode Développeur, accessibilité, MUTE ou réglage TV à configurer.
5. Choisir **Recaler et reprendre**. Comparer POSITION ACTUELLE et NOUVELLE POSITION. Régler avec ↑/↓ 60 s, ←/→ 10 s ; OK long commute ←/→ à 1 s. OK applique, BACK annule. SceneVibe ne connaît pas automatiquement le temps du film.
6. Vérifier séparément : overlay affiché ; touches reçues ; nouveau temps appliqué ; progression SceneVibe ; TEXT visibles ; AUDIO audibles ; fermeture et retour du focus à Prime ; commandes ordinaires de la télécommande.
7. Tester **Recaler sans reprise**, puis l'annulation par BACK. Vérifier que les commentaires restent en pause après la première commande et que le candidat annulé ne modifie rien.
8. Revenir à la Track privée *Columbo — Eaux troubles*. Comparer les 3 TEXT + 3 AUDIO à la référence v31. Relever tout effet sur la lecture native et toute régression privée/publique.

**STOP** avant toute installation Sony dans le présent mandat. Aucune fusion, publication Play, modification Cloud, migration ou mutation fournisseur n'est réalisée. Cet APK est destiné à une Sony en v31 ; il ne remplace pas directement une v33 déjà installée. Aucun PHYSICAL PASS n'est attribué ici.
