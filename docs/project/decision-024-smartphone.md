# Décision 024 — qualification du Companion

Candidat M5 v42 sur la même application qu’en v41. La qualification vérifie le certificat, les permissions, l’upgrade sans effacement, le cache et le redémarrage sur AVD. L’association d’un navigateur supplémentaire réutilise le challenge HTTPS, l’identité, la génération et le bearer TV. Elle ne remplace aucun de ces éléments.

## Recette propriétaire

Après qualification du Récepteur et succès du build M5, vérifier l’empreinte et installer uniquement comme mise à jour, sans désinstaller la v41. Ouvrir SceneVibe sur la Sony et lancer « Associer un nouvel appareil ». Couper le Wi-Fi sur le smartphone, scanner le QR et saisir le code affiché. Vérifier les reçus `cached` et `armed`, la seconde Track en un clic, l’envoi PC puis la restitution TEXT/AUDIO dans Prime Video.

## Repli

Conserver l’APK v41 signé comme référence. Un APK de versionCode 41 ne constitue pas un retour arrière sur une installation v42. Le repli Android sans effacement nécessite, si besoin, un build M5 du comportement v41 avec un versionCode supérieur à celui installé. Le repli indépendant du Récepteur Cloud s’effectue par réaffectation de son alias au déploiement précédent.
