# TV-FIRST 008 — Profil Android compte / cache

Implémentation locale issue de `fdc5f6d5f17a19ef775422bf0b6e63454cc0fbbb`, sans intégration E5/M6. ApplicationId inchangé : `com.scenevibe.tvcompanionpoc`. Nom public : **SceneVibe TV**.

## Variante

`tvfirst008` : versionCode 13, suffixe `-tvfirst008-cache-only`, non débogable, `TVFIRST_DELIVERY_ONLY=true`, `TVFIRST_OBSERVATION_ONLY=true`, LAN false. Origine Cloud exacte fournie explicitement au build ; vide pour le candidat local fermé. HTTPS seul, port 443, aucun redirect/trust override/fallback. Cette variante ne prend pas le factory de démonstration debug ou le factory fermé de release.

Le manifeste 008 retire les permissions overlay, FGS et boot. `OverlayService`, `MediaSessionAccessService` et `BootReceiver` sont déclarés désactivés. Les méthodes réelles boot/remplacement et service ont en plus une fermeture 008 avant lecture des anciens credentials, restauration ou construction de probe. Un appel tardif direct aux sinks reste refusé par la fermeture observationnelle. Aucun essai MEDIA n’est effectué.

## Parcours natif

Les six écrans et le calcul de focus natifs restent en place. Le factory 008 compose un adapter `TvAccountSource` possédé par l’Activity, sans service. Association et récupération se font avec choix humain explicite ; le navigateur ouvre le compte Clerk et confirme le code. Pas de mot de passe sur la TV.

Une nouvelle association explicite retire immédiatement les anciennes cartes de la mémoire de présentation. Les fiches ne sont accessibles que sous une association vérifiée ; une réponse tardive ou un échec de récupération ne réaffiche pas la bibliothèque d’une autre liaison. Les caches antérieurs restent inertes sur disque.

La liste charge au plus 20 résumés, puis une page supplémentaire sur demande. L’id de carte `entry-…` est une clé de présentation distincte de la référence exécutable ; une fiche contrôle la propriété de la référence choisie. Titre/auteur/édition inconnus ne sont pas inventés. « Ajouter à ma TV » ouvre une boîte de confirmation native avant POST.

L’ajout persiste son UUID et sa précondition avant le réseau. Une réponse ambiguë conserve l’intention ; une reprise est une action humaine. Après affectation, seuls les bytes originaux validés sont remis à `PackageInstaller.prepareOnly`. Cette méthode partage validation, encodage, commit, readback et restauration sémantique avec le chemin existant, sans recevoir de `RuntimePorts` et sans appeler ARM.

Le cache réel utilise `AndroidInstallationBackend` dans un namespace de génération `tvfirst008_cache_<generation>`. Il n’efface ni cache historique ni identité antérieure. L’ACK historique reste à zéro dans ce namespace. Un reçu 008 est une preuve distincte ; `TvPreparation.Evidence.cacheOnly` n’alimente jamais `acknowledged` ou `mediaAdmission`.

READY exige les mêmes coordonnées pour la confirmation humaine d’intention, l’affectation, la relecture durable et le reçu Cloud exact. Une reprise revalide le grant, la révision courante, les bytes et le statut. Un cache supprimé, un reçu altéré ou une autre génération ne devient pas prêt. L’état MEDIA reste sans admission.

## Identité et cycle de vie

Les métadonnées 008 ont leur propre fichier. Les secrets utilisent le `SecretStore` AES/GCM existant avec des noms de records de challenge/génération ; aucun fallback ni migration depuis `CloudDeviceCredentials` n’est appelé. Les lectures du SecretStore ne génèrent plus une clé manquante. Le test de ce chemin utilise les vrais cipher/prefs avec un provider JCA synthétique ; ce n’est pas une qualification du matériel AndroidKeyStore Sony.

Un faux commit masque la vue de métadonnées au sein du processus, y compris après recréation du source. Le credential n’est publié qu’après écriture et readback du secret. Les id de requêtes survivent au réseau ambigu. Annuler abandonne une intention/code local explicite ; cela ne supprime pas une identité ou un cache.

L’expiration connue du grant retire localement READY et invalide les callbacks sans requête de secours. Une révocation anticipée n’est découverte qu’à la prochaine vérification serveur ; aucun push de révocation ou état de connexion permanent n’est prétendu.

Un worker sérialisé et un epoch d’Activity gardent les réponses tardives sans autorité. La pause invalide les callbacks avant toute écriture de cache ou preuve locale de reçu. Une requête déjà émise peut avoir atteint le serveur : sa fin n’est pas présumée annulée, et la reprise recontrôle le statut. Les polls sont bornés au challenge courant ; aucune boucle de téléchargement de bibliothèque ou ancien client Cloud à 1 seconde n’est lancée.

Réglages et Diagnostics 008 n’affichent aucun bouton de reset, start/stop historique ou permission overlay/MEDIA. Leurs états ne déchiffrent pas l’identité historique. La bannière et l’icône 008 sont des VectorDrawable : couleurs, marque sparkle et police native déjà présents ; aucun PNG historique n’est réécrit.

## Qualification et limites

Les tests 008 ciblent API31/35 sous Robolectric, les entrées réelles et un peer HTTP de test sur boucle locale relié aux handlers de production/PostgreSQL natif. Le transport HTTP loopback est exclusivement dans `src/testTvfirst008`, absent du DEX de l’APK. Le transport produit exige HTTPS.

Les gates 003/006 restent intacts ; le gate 008 qualifie le delta courant. Les résultats historiques 118 Python et JVM sont conservés séparément. Ils ne remplacent aucun test 008. Le candidat fermé ne démontre pas de compte Clerk réel, d’endpoint HTTPS autorisé, de données privées réelles, de signature continue avec 007 ou d’installation/navigation physique sur Sony. Aucun APK ne doit être installé sous ce seul GO local.
