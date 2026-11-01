# TIMEBASE RESYNC 003 — Protocole Sony G2 futur

**DOCUMENT UNIQUEMENT — NOT EXECUTED.** Aucun script d'installation physique n'est livré. Ce protocole ne vaut pas GO. Prérequis : PASS SOFTWARE, admissibilité examinée favorablement, APK et certificat vérifiés, puis nouvelle décision expresse de Pierre. Le verdict actuel d'admissibilité est **LEGAL REVIEW REQUIRED**.

1. Après ce nouveau GO seulement, récupérer l'APK exacte et comparer sa taille, son SHA-256 et son certificat aux métadonnées livrées. Installation manuelle du package autonome `com.scenevibe.timebasecaptureg2`, jamais en remplacement du Companion ou de G1. Ne pas activer Accessibility.
2. Ouvrir volontairement l'instrument avant l'essai. Lire la portée réelle : le système peut acquérir le display entier, seule la ROI sera copiée. Aucun réseau, audio, OCR ou fichier image. L'APK est privée et de diagnostic, non distribuable.
3. Définir X, Y, largeur, hauteur normalisés du compteur de **position du programme**, avec une marge faible. Les valeurs par défaut 0,05 / 0,10 / 0,90 / 0,30 concernent uniquement notre fixture. Elles ne sont pas des coordonnées Netflix. Déterminer les coordonnées à partir de la géométrie connue de l'écran, sans capture préalable non autorisée. Si le cadrage ne peut être préparé raisonnablement : STOP, INCONCLUSIVE.
4. Choisir un délai de 500 à 60 000 ms avant armement. Aucun délai idéal Sony n'est qualifié. Il doit permettre de quitter volontairement la préparation, revenir au lecteur, mettre soi-même le programme en pause et afficher sa timeline. La projection commence après consentement mais aucun VirtualDisplay ne reçoit d'image avant le déclenchement ; la fenêtre d'acquisition est ensuite de 10 s maximum.
5. Une fois le GO et l'admissibilité effectivement acquis, cocher la déclaration de contexte externe. Appuyer sur « Consentement et armement unique », puis accepter le véritable dialogue système. Un refus est terminal. Ne pas masquer notification/indicateur Android. Ne pas appuyer sur le bouton du faux lecteur pour cette future campagne.
6. Revenir soi-même au programme et afficher volontairement sa position. Ne pas choisir une durée restante ni un compte à rebours publicitaire. Aucune publicité et aucune track SceneVibe ne sont nécessaires. L'instrument n'ouvre et ne commande aucun lecteur.
7. Une seule image logique utile est retenue ; les buffers d'amorçage/doublons sont rejetés. Ne pas revenir à la préparation pendant l'attente/capture : ce retour annule. Si aucun buffer neuf ne suit l'amorçage, IMAGE_TIMEOUT est possible et ne démontre aucune protection Netflix.
8. Attendre la fermeture de la projection et la disparition de sa notification. Revenir volontairement à la préparation puis ouvrir « Inspecter volontairement la ROI ». La ROI expire **30 s après acquisition**, pas 30 s après ouverture. Si ce parcours est trop lent : INCONCLUSIVE, aucune prolongation ni deuxième tentative automatique.
9. Comparer humainement la ROI au compteur physiquement visible du même instant/contexte. La fenêtre d'inspection est FLAG_SECURE. Cocher la comparabilité uniquement si la timeline était réellement présente, la ROI correcte et le contexte certain. Choisir A uniquement si les mêmes chiffres sont visibles ; B uniquement si l'instrument fonctionne et si l'absence des chiffres est concluante avec cadrage correct ; C si consentement/acquisition indisponible ; INCONCLUSIVE pour toute ambiguïté. Ces constats sont humains, jamais une décision automatique ou un résultat déjà acquis.
10. « Effacer et fermer », puis « Arrêter et effacer ». Expiration, sortie de l'inspection et redémarrage effacent également la ROI. Vérifier l'arrêt de la projection via le système. Récupérer uniquement le journal technique privé `g2.jsonl` par une méthode locale expressément autorisée pour ce futur essai ; aucun export d'image ni envoi Cloud n'est prévu. Conserver ses octets et son SHA-256 avant suppression volontaire du journal. La présente mission n'exécute aucune récupération physique.

## STOP

Arrêter si consentement absent, indicateurs inattendus, besoin de privilège, protection, cadrage/horloge incertains, expiration ou erreur. Pas de retry automatique, pas d'API alternative, pas de contournement. Après cette unique tentative future : pixels absents de façon concluante ⇒ arrêter cette voie ; pixels présents ⇒ proposer un mandat G3 distinct, sans OCR automatique. Tous les résultats physiques restent actuellement **NOT EXECUTED**.

## Récupération manuelle future du journal, après nouveau GO seulement

L'APK est debuggable précisément pour le laboratoire. Après arrêt confirmé, une lecture `run-as` de **son seul** fichier technique peut être autorisée dans le mandat physique. Elle ne sert jamais à acquérir des pixels. Exemple documentaire Windows PowerShell 5.1 ou PowerShell 7, avec chemins et serial à remplacer volontairement ; aucune commande d'installation ou de lancement du lecteur :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

cmd.exe /d /c '""C:\Dossier de diagnostic\adb.exe" -s "SERIAL_A_REMPLACER" exec-out run-as com.scenevibe.timebasecaptureg2 cat files/g2.jsonl > "C:\Dossier de diagnostic\g2.jsonl""'
if ($LASTEXITCODE -ne 0) { throw "Lecture du journal refusée ou indisponible" }
Get-FileHash -Algorithm SHA256 -LiteralPath "C:\Dossier de diagnostic\g2.jsonl"
```

La redirection par `cmd.exe` conserve les octets UTF-8 ; ne pas remplacer par `>` de PowerShell 5.1 qui peut transcoder. Ne pas modifier les octets originaux avant empreinte. Si `run-as` est indisponible, noter la limitation et arrêter : aucun root, privilège ou contournement. Cette procédure n'a pas été exécutée sur Sony.
