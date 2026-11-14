# Studio Q2-D et Companion v40

La recette physique commence après le GO propriétaire. Préparer l'APK v40 de GitHub Actions, son SHA-256, ADB et les Android Build Tools 34. Depuis PowerShell, utiliser le numéro de série relevé dans `adb devices -l` et le dossier exact des build-tools.

```powershell
.\Upgrade-SceneVibeQ2.ps1 -Serial $Serial -Apk $ApkV40 -Sha256 $Sha256Publie -BuildTools $BuildTools
```

Le précontrôle extrait en lecture seule le fichier Android installé dans un dossier temporaire et vérifie v38, v40, les signatures et le package. Rejouer avec `-Install` après avoir arrêté volontairement les commentaires SceneVibe. L'option lance `adb install -r`, relit le package v40, vérifie son empreinte et compare UID, overlay, notifications et raccourci.

## Parcours Chrome et Android TV

1. Ouvrir le [Studio Q2](https://scenevibe-product-consolidation001q2-studi-git-5fd51d-archc-ode.vercel.app/studio) dans Chrome. Si nécessaire, se connecter à GitHub avec `pierre22400`. Créer une Track synthétique dans le Studio, renseigner l'identité native, ajouter 2 TEXT et un vrai AUDIO WAV ou MP3. Faire **Enregistrer**, puis **Sauvegarder dans mon Cloud privé**. Dans Contenus, vérifier le titre, la révision 1 et l'historique.
2. Dans un autre profil Chrome connecté, ouvrir la dernière révision depuis Mes Tracks privées. Vérifier que les textes et l'original AUDIO sont éditables. Préparer pour la TV et rendre la révision distribuable en privé. Contrôler son absence du [Viewer Q2](https://scenevibe-product-consolidation001q2-viewe-git-6ef46d-archc-ode.vercel.app/decouvrir).
3. Sur la TV, vérifier la lecture de Columbo depuis le cache historique et la présence de la Track publique connue. Contrôler TEXT, AUDIO et le raccourci MUTE. Ouvrir **Bibliothèque**, puis **Studio Q2** dans l'écran « Mes Tracks ». Demander un code et le valider dans **Mes Tracks privées → Associer une TV au Studio Q2** sur Chrome. Le nouveau code concerne seulement ce Preview Q2.
4. Dans Chrome, sélectionner la TV et l'envoyer. Attendre **Cache TV confirmé**. Sur la TV, rechercher le titre et sa révision. Activer explicitement la restitution et ouvrir l'œuvre exacte dans Prime Video. Relever ce qui apparaît et ce qui est audible. Si le média n'est pas reconnu par Android, relever le diagnostic de correspondance et l'identifiant affiché par les outils du propriétaire.
5. Ajouter un TEXT au même projet et répéter sauvegarde, préparation et décision privée. Demander l'envoi pendant les commentaires de la révision précédente. Relever l'attente affichée et l'absence de rupture. Arrêter volontairement la restitution SceneVibe et contrôler le reçu du nouveau paquet. Le nouveau paquet doit devenir courant seulement après vérification.
6. Rejouer veille, recréation de l'application, redémarrage à froid, relecture du cache et réception d'une nouvelle commande envoyée après le redémarrage. Relever séparément heure, révision, reçu et restitution. Faire le test séparément pour privé et public.

## Récupération

Pour une panne propre au Preview, revenir au déploiement Preview précédemment qualifié après décision du propriétaire. La sauvegarde éditable distante et les révisions présentes dans Neon restent disponibles pour le redéploiement du service Q2-D. Cesser les demandes de nouvelles révisions si la preuve de cache est douteuse.

Pour une régression de la v40, la stratégie de conservation des données est une APK de récupération M5 basée sur un code stable, de même package et de versionCode supérieure, à compiler et qualifier sur AVD avant la Sony. Une réinstallation directe de v38 a un versionCode inférieur et un downgrade standard est refusé. Une désinstallation suivie de l'installation v38 est une opération destructive, soumise à une nouvelle décision. Elle perd normalement les données privées de l'application, ses caches et les clés Android Keystore associées. L'export des APK et des rapports ne constitue pas une sauvegarde de ces données. Préserver les originaux et brouillons de création, ainsi que l'accès au Studio et à la bibliothèque distante, avant toute décision destructive.

Documentation Android sur [les signatures de mise à jour](https://developer.android.com/studio/publish/app-signing) et [la vérification des certificats](https://developer.android.com/tools/apksigner).
