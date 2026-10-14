# Retest physique E5-038 — procédure préparée

Précondition : la candidate signée compatible doit avoir fait l'objet d'une décision
physique et d'une mise à jour distinctes. L'APK E5 versionCode 14 actuellement installée
ne contient pas ce receiver. Aucune installation n'est exécutée dans WORK E5-038.
Les permissions overlay, notification listener et Usage Access restent requises.
Cloud non configuré, LAN OFF ; Sony Android 12/API31 uniquement. ADB déjà connecté.

1. Lance Columbo sur Prime Video et laisse la lecture active.
2. Depuis PowerShell, en attestant qu'il s'agit bien d'une VOD, exécute :

```powershell
adb shell am broadcast `
  -n com.scenevibe.tvcompanionpoc/.E5AdbTriggerReceiver `
  -a com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE `
  --ez e5VodConfirmed true
```

3. `QUEUED_GUARDS_PENDING` confirme la transmission seulement. Observe la pause,
   l'interlude audiovisuel de dix secondes et la reprise ; aucun retour à Companion.
4. Après environ 25 secondes, relève le diagnostic final :

```powershell
adb logcat -d -t 300 -v time "SceneVibeE5Adb:I" "SceneVibeE5:I" "*:S"
```

5. Rapporte la dernière tentative : événement/phase/reason et observation TV.
   `SUCCESS` exige une confirmation PLAYING, pas simplement un PLAY envoyé.
   `REFUSED` signifie que les preuves manquent ; `RECOVERY_REQUIRED` nécessite
   une récupération manuelle dans Prime. Ne pas automatiser PLAY ni relancer en boucle.

Si plusieurs appareils ADB sont présents, insère `-s <serial réel>` après `adb`.
Le script fourni peut faire la commande, l'attente bornée et la lecture du diagnostic :
`& .\Invoke-SceneVibeE5Interlude.ps1 -Serial '<serial réel>'`.
Cette commande exacte a été vérifiée sur émulateur Android 12/API31 ; la transmission est réelle et la cible non Sony est refusée par les gardes.
La syntaxe Windows est standard PowerShell ; aucun Windows physique n'est contrôlé ici.
Aucun `adb shell input`, Activity, installation, désinstallation, clear data ou pairing reset.
