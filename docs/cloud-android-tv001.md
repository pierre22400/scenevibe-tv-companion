# Cloud → Android TV 001 — Companion expérimental

## Filiation

- Companion : `414151c4b207544dd417db4a102ee6ccebedbd66`, branche TV-FIRST013.
- Cloud : `42c2b94aa436a2dbacf2bb082dc6737457602cee`, qualification fournisseur 003-I, run `40202527959`.
- Réintégration des patches TV complets 003-D puis du delta 003-E, restés dans les livraisons locales. Les sources du consommateur 003-I correspondent à ces composants ; les changements fonctionnels nouveaux portent sur leur exposition au Companion, la restauration hors réseau et les délais HTTPS.

## Application livrée

La variante `cloudandroid001` conserve `com.scenevibe.tvcompanionpoc`, son launcher, son parcours Compte et le service TEXT existants. Son versionCode est 16. Elle ajoute dans Réglages le catalogue multimédia privé, une confirmation de téléchargement et la relecture explicite du cache.

TV-FIRST reste propriétaire de l'identité chiffrée, du worker série et de l'annulation par époque. Le transport utilise les routes privées 003-I ; aucun accès administratif Neon/R2 n'existe dans l'application. Une intention persistée est réutilisée après interruption. Le cache conserve les fichiers vérifiés, un pointeur transactionnel et les ressources AUDIO logiques distinctes, même lorsque leurs octets sont identiques.

La restauration locale n'exige pas de transport réseau configuré. Elle ne renouvelle ni le consentement, ni une admission MEDIA. Le chargement d'un catalogue ou d'un cache ne lance aucun commentaire.

## Qualification et frontières

Les tests instrumentés `CloudAndroid001HttpsTest` utilisent le vrai client, le vrai cache Android, AndroidKeyStore et le décodeur natif. `CloudAndroid001PlaybackTest` compose le calendrier M5, SceneRenderer et AndroidLocalAudioPort existants avec des observations de timeline synthétiques. `CloudAndroid001RestartTest` vérifie une relecture après changement réel de processus.

Le serveur de `qualification/cloud-android-tv001/server.ts` est un banc local : services Cloud du commit indiqué, repositories mémoire et ObjectStore HTTP local. Ce n'est pas une nouvelle campagne Neon/R2 ni un staging durable. Il écoute seulement sur loopback:8443. Les identités synthétiques et la CA publique sont générées dans les assets de tests ignorés par Git ; aucun de ces assets n'entre dans l'APK cible.

Le constructeur HTTPS produit conserve le truststore Android, le contrôle du hostname, l'origine exacte et le port implicite 443. Le constructeur de qualification accepte une CA explicite et loopback:8443 seulement dans un APK debuggable. Il ne désactive jamais la validation des certificats ou du hostname. L'APK expérimental standard est non debuggable et non signé.

Les résultats chiffrés, les journaux d'exécution et les empreintes sont fournis dans la livraison de qualification. Les scénarios échoués avant correction et les limites de l'émulateur restent identifiés dans ce bilan.

### Résultats locaux du 26 octobre 2026

- 1 329 tests JVM réussis, zéro échec final ; un test privé optionnel Sony ignoré. Décomposition : 1 168 du socle, 38 TV-FIRST008, 38 d'intégration TEXT, 56 du service TEXT en Robolectric, 3 frontières HTTPS et 26 du calendrier multimédia.
- 10/10 tests natifs HTTPS/cache réussis, dont le vrai callback du source utilisé par l'écran du Companion et le décodage MP3 Android.
- 8/8 tests natifs de restitution réussis : 16 fenêtres TEXT réellement attachées par SceneRenderer et 3 MP3 démarrés par AndroidLocalAudioPort ; PAUSE/RESUME, SEEK, absence de doublon, interrupteurs et interruption contrôlés. La timeline injectée est synthétique.
- 1/1 test natif de reprise réussi : processus différent après force-stop, pont réseau local supprimé et relecture des 19 événements par le callback de restauration hors réseau de l'écran.
- Compilation et lint du candidat et de TV-FIRST013 réussis. Aucun credential de tests ou certificat de qualification dans l'APK cible ; ils appartiennent exclusivement à l'APK d'instrumentation.

La cible réellement exécutée est Android 12/API31 AOSP x86_64, avec profil d'écran TV. L'image Android TV x86 n'a pas terminé son démarrage sur cet hôte sans KVM ; aucun PASS propre à cette image ou à une Sony n'est revendiqué.

Les premiers tests ont permis de corriger des défauts du banc : namespace de cache de test non admis, observations de lecture trop espacées assimilées à des SEEK et assertion d'attachement appelée avant la traversée asynchrone WindowManager. La politique M5 et le renderer produit n'ont pas été assouplis pour ces corrections. Le mutant M5 a aussi reçu une ancre adaptée à l'ajout AUDIO, et les mutations JSON du banc JVM acceptent désormais les exceptions déclarées par l'API JSON Android.

**Verdict produit : PARTIEL FONCTIONNEL.** Transport et cache dans l'application, restitution logicielle Android et reprise sont qualifiés dans les périmètres indiqués. L'activation multimédia dans le service réel et le parcours Prime/Sony restent ouverts selon les limites ci-dessous.

## Limites produit encore ouvertes

Le reçu `CACHE_CONFIRMED` ne vaut pas admission MEDIA. Le document de qualification conserve une édition et un mapping UNKNOWN. Le garde `requireNativeIdentityEvidence()` reste fermé. Le service natif Sony conserve son parcours TEXT historique ; le cache multimédia n'est pas encore raccordé à son activation réelle. Les tests du runtime utilisent exclusivement son entrée de qualification logicielle, même lorsqu'ils exécutent les vrais widgets et MediaPlayer sur Android.

Une démonstration Prime/Sony exige donc encore : une API de qualification durable accessible, les preuves d'identité/mapping et le raccordement contrôlé au propriétaire de service existant. Aucune de ces conditions n'est déduite d'un test JVM ou d'une observation synthétique.

## Préparation d'un staging sans déploiement

`qualification/cloud-android-tv001/staging.example.json` décrit la configuration à remplir. Réutiliser une seule composition serveur comprenant l'association TV-FIRST008 et les routes multimédia 003-I, sur une branche Neon jetable explicitement choisie et un préfixe R2 isolé. Les credentials fournisseurs restent côté serveur. Exposer une origine HTTPS exacte avec certificat reconnu par Android ; désactiver toute route de contrôle de qualification.

Construire ensuite `assembleCloudandroid001` avec `SCENEVIBE_CLOUD_ORIGIN` fixé à cette origine. Ne pas réutiliser l'URL d'un runner GitHub Actions et ne pas présumer qu'une ancienne URL de qualification est active. La configuration fournie n'est pas une preuve de déploiement ou de disponibilité.

## Future qualification physique — non exécutée

Après clôture des réserves techniques et un nouveau GO propriétaire :

1. Construire et vérifier un APK v16 lié au staging effectivement testé.
2. Appliquer la signature M5 avec la procédure TV-FIRST014 déjà qualifiée, puis recalculer le SHA-256 du fichier signé. Pour exécuter son script PowerShell, prévoir `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`.
3. Vérifier le package, la version, le certificat et la conservation de l'installation existante avant toute commande d'installation Sony.
4. Après autorisation, installer la mise à jour, associer le compte/appareil et télécharger volontairement Columbo. Vérifier les 16 TEXT, les 3 AUDIO et la relecture du cache après fermeture.
5. Après admission locale explicite, lancer Prime manuellement et vérifier visuellement/auditivement les fenêtres, PAUSE/RESUME, SEEK avant/arrière, les doublons et les interrupteurs indépendants.
6. Conserver les observations physiques séparées des requêtes de rendu et des états MediaPlayer. Aucun flux Prime ne doit être modifié ou capturé.

**STOP avant installation Sony, production, fusion main, Google Play ou mutation des données historiques.**
