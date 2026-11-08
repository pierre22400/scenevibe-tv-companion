# PARCOURS AUTONOME 001-B — protocole physique préparé, non exécuté

STOP actuel : installation Sony, déploiement fournisseur, mutation Neon/R2, fusion, Production et secrets hors CI autorisée. Les étapes ci-dessous appartiennent à une future campagne physique autorisée ; aucune ne constitue un PASS actuel.

## Préparer la continuité et la récupération

1. Conserver l'APK v31 exact et les références fonctionnelles/documentaires du rapport. Relever la version installée, le package `com.scenevibe.tvcompanionpoc`, le certificat M5, les associations et révisions privées, sans exporter les secrets.
2. Archiver les exports Studio privés et les preuves de la référence. Vérifier l'espace disponible, les permissions Android existantes et la procédure de diagnostic. Ne pas effacer les données de l'application, ne pas désinstaller et ne pas réinitialiser ses identités.
3. Une mise à jour de même package/certificat conserve normalement les données, mais cette continuité devra être constatée sur Sony. Ne pas tenter `adb install -d` ni forcer un downgrade 32→31.
4. En cas d'échec public, arrêter uniquement le service public et revenir à l'interface privée conservée. Si l'APK complet doit être remplacé, préparer séparément un build de récupération fondé sur le fonctionnel v31, avec le même certificat et un versionCode supérieur au maximum installé. Qualifier sa lecture des données existantes sur émulateur avant toute installation. Cette livraison ne contient pas d'APK de récupération déjà qualifié et n'en installe aucun.

## Préparer le Cloud isolé

La cible Preview intégrée à l'APK n'héberge pas les nouvelles routes publiques. Définir dans une campagne ultérieure un environnement isolé explicitement autorisé : données synthétiques, stockage isolé, origines HTTPS exactes Web/TV et persistance des commandes. Ne pas simplement activer le mode local sur Vercel : sa gate le refuse intentionnellement. Qualifier TLS, révocation, flux interrompus, origines et cache sur cet environnement avant de l'utiliser sur Sony. Si l'origine TV diffère, produire un nouvel APK signé avec versionCode approprié.

## Réception PC puis téléphone physiques

| Étape | Réception attendue | Preuve |
| --- | --- | --- |
| Référence privée | Rejouer la Track privée de référence avant mise à jour | Révision, événements TEXT/AUDIO réellement observés, absence de régression |
| Installation autorisée | Package/certificat/empreinte conformes, données privées conservées | Métadonnées et constat propriétaire |
| Catalogue PC | Track, Channel et Spaces accessibles sans compte | Navigateur réel et URL de campagne |
| Association | Un code lisible affiché sur TV ; sa seule saisie Web associe | Pas de second code, dialogue TV ni écran d'approbation |
| Téléchargement | Demande puis cache complet confirmé avec les trois MP3 | Binding Track/révision/SHA et reçu après engagement réel |
| Activation | TEXT/AUDIO Web appliqués automatiquement, OFF/OFF compris | Statut `armed` et réglages TV cohérents ; aucun clic de confirmation TV |
| Identité média | Bonne œuvre/édition reconnue, mauvaise œuvre bloquée | MEDIA exact ; `MEDIA_IDENTITY_BLOCKED` sur le cas contradictoire |
| Lecture | Compter chaque commentaire écrit et chaque audio dans l'œuvre | Observations individuelles, pause/reprise/seek et limites publicitaires |
| Durée | Laisser dépasser 15 minutes, modifier TEXT/AUDIO | Même association, contrôles toujours utilisables tant que le grant est valide |
| Édition publiée | Sauvegarder dans Studio et proposer la nouvelle révision | Fiche publique stable ; offerte/installée/active relevées séparément |
| Session active | Demander cette mise à jour pendant la lecture | Aucune interruption ; mise à jour différée après fin MEDIA effectivement observée |
| Panne / corruption | Interrompre puis reprendre ; présenter un MP3 incorrect de campagne | Pas de reçu de succès prématuré, ancien cache complet préservé |
| Redémarrages | Arrêter le processus puis redémarrer Android | Cache entièrement revérifié, même possession valide, aucun second code |
| Retrait / révocation | Retirer la Track ou révoquer le grant isolé | Nouveaux départs/demandes refusés, cache local conservé, effets conformes à la politique documentée |
| Téléphone réel | Rejouer tout le parcours depuis un navigateur mobile physique | Même application responsive ; résultat indépendant du PC |
| Non-régression finale | Rejouer la Track privée après la campagne publique | Association et cache privés intacts ; effets privés réellement observés |

Les permissions imposées par Android peuvent nécessiter leur réglage système si elles manquent. Elles ne doivent pas être transformées en demandes d'autorisation SceneVibe supplémentaires.

Un succès sur émulateur avec notre propre MediaSession ne valide ni Prime, ni les métadonnées effectivement exposées sur Sony, ni le comportement de focus/permissions du firmware, ni le trajet fournisseur. Toute étape non exécutée reste « non exécutée ».
